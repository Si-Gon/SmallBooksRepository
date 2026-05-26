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
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrestamoService {

    private final PrestamoRepository prestamoRepository;
    private final LicenseClient licenseClient;
    private final SubscriptionClient subscriptionClient;
    private final NotificationClient notificationClient;

    @Transactional
    public PrestamoResponseDTO crearPrestamo(PrestamoRequestDTO request, String usuarioId) {
        log.info("Iniciando creación de préstamo — usuario: {}, libro: {}", 
                usuarioId, request.getLibroId());

        // Paso 1: Consultar plan del usuario
        SuscripcionDTO suscripcion;
        try {
            suscripcion = subscriptionClient.obtenerSuscripcion(usuarioId);
            log.info("Plan activo para usuario {}: {} — max: {} préstamos, {} días",
                    usuarioId, suscripcion.getPlan(),
                    suscripcion.getMaxPrestamos(), suscripcion.getDiasPrestamo());
        } catch (Exception e) {
            log.warn("No se encontró suscripción activa para usuario {}. Aplicando plan BASICO por defecto.", 
                    usuarioId);
            suscripcion = new SuscripcionDTO();
            suscripcion.setMaxPrestamos(2);
            suscripcion.setDiasPrestamo(7);
            suscripcion.setPlan("BASICO");
        }

        // Paso 2: Verificar límite de préstamos
        List<Prestamo> prestamosActivos = prestamoRepository
                .findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO);

        if (prestamosActivos.size() >= suscripcion.getMaxPrestamos()) {
            log.warn("Usuario {} alcanzó límite de préstamos activos: {}/{}",
                    usuarioId, prestamosActivos.size(), suscripcion.getMaxPrestamos());
            throw new RuntimeException(
                    "Has alcanzado el límite de " + suscripcion.getMaxPrestamos() +
                    " préstamos activos para tu plan " + suscripcion.getPlan());
        }

        // Paso 3: Verificar duplicado
        boolean yaLoTiene = prestamoRepository
                .findByLibroIdAndEstado(request.getLibroId(), EstadoPrestamo.ACTIVO)
                .stream()
                .anyMatch(p -> p.getUsuarioId().equals(usuarioId));

        if (yaLoTiene) {
            log.warn("Usuario {} ya tiene préstamo activo del libro {}", 
                    usuarioId, request.getLibroId());
            throw new RuntimeException("Ya tienes este libro en préstamo activo");
        }

        // Paso 4: Verificar copias disponibles
        LicenciaDTO licencia;
        try {
            licencia = licenseClient.obtenerLicencia(request.getLibroId());
            log.info("Licencia libro {}: {}/{} copias disponibles",
                    request.getLibroId(), licencia.getCopiasDisponibles(), licencia.getTotalCopias());
        } catch (Exception e) {
            log.error("Error al consultar licencia del libro {}: {}", 
                    request.getLibroId(), e.getMessage());
            throw new RuntimeException(
                    "No se pudo verificar disponibilidad del libro con id: " + request.getLibroId());
        }

        if (licencia.getCopiasDisponibles() == null || licencia.getCopiasDisponibles() <= 0) {
            log.warn("No hay copias disponibles del libro {}", request.getLibroId());
            throw new RuntimeException(
                    "No hay copias disponibles del libro con id: " + request.getLibroId());
        }

        // Paso 5: Descontar copia
        try {
            licenseClient.prestar(request.getLibroId());
            log.info("Copia descontada exitosamente — libro: {}", request.getLibroId());
        } catch (Exception e) {
            log.error("Error al descontar copia en License Service — libro: {}: {}",
                    request.getLibroId(), e.getMessage());
            throw new RuntimeException("Error al registrar el préstamo en License Service");
        }

        // Paso 6: Crear préstamo
        LocalDateTime ahora = LocalDateTime.now();
        Prestamo prestamo = new Prestamo();
        prestamo.setUsuarioId(usuarioId);
        prestamo.setLibroId(request.getLibroId());
        prestamo.setFechaInicio(ahora);
        prestamo.setFechaVencimiento(ahora.plusDays(suscripcion.getDiasPrestamo()));
        prestamo.setEstado(EstadoPrestamo.ACTIVO);

        Prestamo guardado = prestamoRepository.save(prestamo);
        log.info("Préstamo creado exitosamente — id: {}, usuario: {}, libro: {}, vence: {}",
                guardado.getId(), usuarioId, request.getLibroId(), guardado.getFechaVencimiento());

        // Paso 7: Notificar
        try {
            notificationClient.crear(
                NotificacionRequestDTO.prestamoCreado(usuarioId, request.getLibroId()));
            log.info("Notificación PRESTAMO_CREADO enviada al usuario {}", usuarioId);
        } catch (Exception e) {
            log.warn("No se pudo enviar notificación de préstamo creado para usuario {}: {}",
                    usuarioId, e.getMessage());
        }

        return mapearADto(guardado);
    }

    public List<PrestamoResponseDTO> obtenerPrestamosActivos(String usuarioId) {
        log.info("Consultando préstamos activos del usuario: {}", usuarioId);
        return prestamoRepository
                .findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO)
                .stream()
                .map(this::mapearADto)
                .collect(Collectors.toList());
    }

    public List<PrestamoResponseDTO> obtenerHistorial(String usuarioId) {
        log.info("Consultando historial completo del usuario: {}", usuarioId);
        return prestamoRepository
                .findByUsuarioId(usuarioId)
                .stream()
                .map(this::mapearADto)
                .collect(Collectors.toList());
    }

    public List<PrestamoResponseDTO> obtenerTodos() {
        log.info("Consultando todos los préstamos para Analytics");
        return prestamoRepository.findAll()
                .stream()
                .map(this::mapearADto)
                .collect(Collectors.toList());
    }

    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cerrarPrestamosVencidos() {
        LocalDateTime ahora = LocalDateTime.now();
        log.info("Scheduler ejecutado — verificando préstamos vencidos a las {}", ahora);

        List<Prestamo> vencidos = prestamoRepository
                .findByEstadoAndFechaVencimientoBefore(EstadoPrestamo.ACTIVO, ahora);

        log.info("Préstamos vencidos encontrados: {}", vencidos.size());

        for (Prestamo prestamo : vencidos) {
            try {
                licenseClient.devolver(prestamo.getLibroId());
                prestamo.setEstado(EstadoPrestamo.VENCIDO);
                prestamoRepository.save(prestamo);

                log.info("Préstamo cerrado — id: {}, usuario: {}, libro: {}",
                        prestamo.getId(), prestamo.getUsuarioId(), prestamo.getLibroId());

                try {
                    notificationClient.crear(
                        NotificacionRequestDTO.prestamoVencido(
                            prestamo.getUsuarioId(), prestamo.getLibroId()));
                } catch (Exception e) {
                    log.warn("No se pudo notificar vencimiento — préstamo {}: {}",
                            prestamo.getId(), e.getMessage());
                }

            } catch (Exception e) {
                log.error("Error al cerrar préstamo id: {} — {}", 
                        prestamo.getId(), e.getMessage());
            }
        }

        // Avisar próximos a vencer
        LocalDateTime en2Dias = ahora.plusDays(2);
        List<Prestamo> proximosAVencer = prestamoRepository
                .findByEstadoAndFechaVencimientoBefore(EstadoPrestamo.ACTIVO, en2Dias)
                .stream()
                .filter(p -> p.getFechaVencimiento().isAfter(ahora))
                .collect(Collectors.toList());

        log.info("Préstamos próximos a vencer (2 días): {}", proximosAVencer.size());

        for (Prestamo prestamo : proximosAVencer) {
            try {
                notificationClient.crear(
                    NotificacionRequestDTO.proximoVencer(
                        prestamo.getUsuarioId(), prestamo.getLibroId()));
            } catch (Exception e) {
                log.warn("No se pudo notificar próximo vencimiento — préstamo {}: {}",
                        prestamo.getId(), e.getMessage());
            }
        }
    }

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