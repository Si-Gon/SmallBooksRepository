package com.silvio.elending.service;

import com.silvio.elending.client.LicenseClient;
import com.silvio.elending.client.NotificationClient;
import com.silvio.elending.client.SubscriptionClient;
import com.silvio.elending.dto.LicenciaDTO;
import com.silvio.elending.dto.NotificacionRequestDTO;
import com.silvio.elending.dto.PrestamoRequestDTO;
import com.silvio.elending.dto.PrestamoResponseDTO;
import com.silvio.elending.dto.SuscripcionDTO;
import com.silvio.elending.model.Prestamo;
import com.silvio.elending.model.Prestamo.EstadoPrestamo;
import com.silvio.elending.repository.PrestamoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PrestamoService {

    private final PrestamoRepository prestamoRepository;
    private final LicenseClient licenseClient;
    private final SubscriptionClient subscriptionClient;  // ← nuevo
    private final NotificationClient notificationClient;  // ← nuevo

    // CREAR PRÉSTAMO
    
    @Transactional
    public PrestamoResponseDTO crearPrestamo(PrestamoRequestDTO request, String usuarioId) {

        // --- Paso 1: Consultar plan del usuario en Subscription Service ---
        // Los límites ya no son fijos — dependen del plan
        SuscripcionDTO suscripcion;
        try {
            suscripcion = subscriptionClient.obtenerSuscripcion(usuarioId);
        } catch (Exception e) {
            // Si no tiene suscripción activa, aplicar límites de plan BASICO por defecto
            suscripcion = new SuscripcionDTO();
            suscripcion.setMaxPrestamos(2);
            suscripcion.setDiasPrestamo(7);
            suscripcion.setPlan("BASICO");
        }

        // --- Paso 2: Verificar límite de préstamos según plan ---
        List<Prestamo> prestamosActivos = prestamoRepository
                .findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO);

        if (prestamosActivos.size() >= suscripcion.getMaxPrestamos()) {
            throw new RuntimeException(
                    "Has alcanzado el límite de " + suscripcion.getMaxPrestamos() +
                    " préstamos activos para tu plan " + suscripcion.getPlan());
        }

        // --- Paso 3: Verificar que el usuario no tenga ya ese libro ---
        boolean yaLoTiene = prestamoRepository
                .findByLibroIdAndEstado(request.getLibroId(), EstadoPrestamo.ACTIVO)
                .stream()
                .anyMatch(p -> p.getUsuarioId().equals(usuarioId));

        if (yaLoTiene) {
            throw new RuntimeException("Ya tienes este libro en préstamo activo");
        }

        // --- Paso 4: Consultar License Service — ¿hay copias disponibles? ---
        LicenciaDTO licencia;
        try {
            licencia = licenseClient.obtenerLicencia(request.getLibroId());
        } catch (Exception e) {
            throw new RuntimeException(
                    "No se pudo verificar disponibilidad del libro con id: " +
                    request.getLibroId());
        }

        if (licencia.getCopiasDisponibles() == null || licencia.getCopiasDisponibles() <= 0) {
            throw new RuntimeException(
                    "No hay copias disponibles del libro con id: " + request.getLibroId());
        }

        // --- Paso 5: Descontar 1 copia en License Service ---
        try {
            licenseClient.prestar(request.getLibroId());
        } catch (Exception e) {
            throw new RuntimeException("Error al registrar el préstamo en License Service");
        }

        // --- Paso 6: Crear el préstamo con los días del plan ---
        LocalDateTime ahora = LocalDateTime.now();
        Prestamo prestamo = new Prestamo();
        prestamo.setUsuarioId(usuarioId);
        prestamo.setLibroId(request.getLibroId());
        prestamo.setFechaInicio(ahora);
        prestamo.setFechaVencimiento(ahora.plusDays(suscripcion.getDiasPrestamo()));
        prestamo.setEstado(EstadoPrestamo.ACTIVO);

        Prestamo guardado = prestamoRepository.save(prestamo);

        // --- Paso 7: Notificar al usuario ---
        // Si falla la notificación NO rollback el préstamo — es un aviso, no crítico
        try {
    notificationClient.crear(
        NotificacionRequestDTO.prestamoCreado(usuarioId, request.getLibroId()));
} catch (Exception e) {
    System.err.println("ERROR NOTIFICACION COMPLETO: " + 
        e.getClass().getName() + " - " + e.getMessage());
    if (e.getCause() != null) {
        System.err.println("CAUSA: " + e.getCause().getMessage());
    }
}

        return mapearADto(guardado);
    }

    // OBTENER PRÉSTAMOS ACTIVOS DEL USUARIO
    
    public List<PrestamoResponseDTO> obtenerPrestamosActivos(String usuarioId) {
        return prestamoRepository
                .findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO)
                .stream()
                .map(this::mapearADto)
                .collect(Collectors.toList());
    }

    // OBTENER HISTORIAL COMPLETO DEL USUARIO
    
    public List<PrestamoResponseDTO> obtenerHistorial(String usuarioId) {
        return prestamoRepository
                .findByUsuarioId(usuarioId)
                .stream()
                .map(this::mapearADto)
                .collect(Collectors.toList());
    }
    
    // OBTENER TODOS LOS PRÉSTAMOS — para Analytics Service
    
    public List<PrestamoResponseDTO> obtenerTodos() {
        return prestamoRepository.findAll()
                .stream()
                .map(this::mapearADto)
                .collect(Collectors.toList());
    }

    // SCHEDULER — revisa cada hora préstamos vencidos y los cierra
    // También avisa 2 días antes del vencimiento
    
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cerrarPrestamosVencidos() {
        LocalDateTime ahora = LocalDateTime.now();

        // Cerrar los vencidos
        List<Prestamo> vencidos = prestamoRepository
                .findByEstadoAndFechaVencimientoBefore(EstadoPrestamo.ACTIVO, ahora);

        for (Prestamo prestamo : vencidos) {
            try {
                licenseClient.devolver(prestamo.getLibroId());
                prestamo.setEstado(EstadoPrestamo.VENCIDO);
                prestamoRepository.save(prestamo);

                // Notificar vencimiento
                try {
                    notificationClient.crear(
                        NotificacionRequestDTO.prestamoVencido(
                            prestamo.getUsuarioId(), prestamo.getLibroId()));
                } catch (Exception e) {
                    System.err.println("No se pudo notificar vencimiento: " + e.getMessage());
                }

                System.out.println("Préstamo vencido cerrado — id: " + prestamo.getId());

            } catch (Exception e) {
                System.err.println("Error al cerrar préstamo id: " + prestamo.getId() +
                        " — " + e.getMessage());
            }
        }

        // Avisar los que vencen en 2 días
        LocalDateTime en2Dias = ahora.plusDays(2);
        List<Prestamo> proximosAVencer = prestamoRepository
                .findByEstadoAndFechaVencimientoBefore(EstadoPrestamo.ACTIVO, en2Dias)
                .stream()
                .filter(p -> p.getFechaVencimiento().isAfter(ahora))
                .collect(Collectors.toList());

        for (Prestamo prestamo : proximosAVencer) {
            try {
                notificationClient.crear(
                    NotificacionRequestDTO.proximoVencer(
                        prestamo.getUsuarioId(), prestamo.getLibroId()));
            } catch (Exception e) {
                System.err.println("No se pudo notificar próximo vencimiento: " +
                        e.getMessage());
            }
        }
    }

    // Mapeo privado Entidad → ResponseDTO
    
    private PrestamoResponseDTO mapearADto(Prestamo prestamo) {
        PrestamoResponseDTO dto = new PrestamoResponseDTO();
        dto.setId(prestamo.getId());
        dto.setUsuarioId(prestamo.getUsuarioId());
        dto.setLibroId(prestamo.getLibroId());
        dto.setFechaInicio(prestamo.getFechaInicio());
        dto.setFechaVencimiento(prestamo.getFechaVencimiento());
        dto.setEstado(prestamo.getEstado());
        return dto;
    }
}