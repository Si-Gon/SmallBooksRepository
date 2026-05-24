package com.silvio.elending.service;

import com.silvio.elending.client.LicenseClient;
import com.silvio.elending.dto.LicenciaDTO;
import com.silvio.elending.dto.PrestamoRequestDTO;
import com.silvio.elending.dto.PrestamoResponseDTO;
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

    private static final int MAX_PRESTAMOS_ACTIVOS = 5;
    private static final int DIAS_PRESTAMO = 14;

    // CREAR PRÉSTAMO
    // usuarioId viene del token JWT — el controller lo extrae y lo pasa aquí
    
    @Transactional
    public PrestamoResponseDTO crearPrestamo(PrestamoRequestDTO request, String usuarioId) {

        // --- Paso 1: Verificar límite de 5 préstamos activos por usuario ---
        List<Prestamo> prestamosActivos = prestamoRepository
                .findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO);

        if (prestamosActivos.size() >= MAX_PRESTAMOS_ACTIVOS) {
            throw new RuntimeException(
                    "Has alcanzado el límite de " + MAX_PRESTAMOS_ACTIVOS +
                    " préstamos activos simultáneos");
        }

        // --- Paso 2: Verificar que el usuario no tenga ya ese libro ---
        boolean yaLoTiene = prestamoRepository
                .findByLibroIdAndEstado(request.getLibroId(), EstadoPrestamo.ACTIVO)
                .stream()
                .anyMatch(p -> p.getUsuarioId().equals(usuarioId));

        if (yaLoTiene) {
            throw new RuntimeException(
                    "Ya tienes este libro en préstamo activo");
        }

        // --- Paso 3: Consultar License Service — ¿hay copias disponibles? ---
        LicenciaDTO licencia;
        try {
            licencia = licenseClient.obtenerLicencia(request.getLibroId());
        } catch (Exception e) {
            throw new RuntimeException("Error Feign: " + e.getClass().getName() + " - " + e.getMessage());
        }

        if (licencia.getCopiasDisponibles() == null || licencia.getCopiasDisponibles() <= 0) { 
            throw new RuntimeException(
                    "No hay copias disponibles del libro con id: " + request.getLibroId());
        }

        // --- Paso 4: Descontar 1 copia en License Service ---
        try {
            licenseClient.prestar(request.getLibroId());
        } catch (Exception e) {
            throw new RuntimeException("Error Feign prestar: " + e.getClass().getName() + " - " + e.getMessage());
        }

        // --- Paso 5: Crear el préstamo en nuestra BD ---
        LocalDateTime ahora = LocalDateTime.now();
        Prestamo prestamo = new Prestamo();
        prestamo.setUsuarioId(usuarioId);
        prestamo.setLibroId(request.getLibroId());
        prestamo.setFechaInicio(ahora);
        prestamo.setFechaVencimiento(ahora.plusDays(DIAS_PRESTAMO));
        prestamo.setEstado(EstadoPrestamo.ACTIVO);

        return mapearADto(prestamoRepository.save(prestamo));
    }

    // -----------------------------------------------------------------------
    // OBTENER PRÉSTAMOS ACTIVOS DEL USUARIO
    // -----------------------------------------------------------------------
    public List<PrestamoResponseDTO> obtenerPrestamosActivos(String usuarioId) {
        return prestamoRepository
                .findByUsuarioIdAndEstado(usuarioId, EstadoPrestamo.ACTIVO)
                .stream()
                .map(this::mapearADto)
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // OBTENER HISTORIAL COMPLETO DEL USUARIO
    // -----------------------------------------------------------------------
    public List<PrestamoResponseDTO> obtenerHistorial(String usuarioId) {
        return prestamoRepository
                .findByUsuarioId(usuarioId)
                .stream()
                .map(this::mapearADto)
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // SCHEDULER — revisa cada hora préstamos vencidos y los cierra
    // @Scheduled(fixedRate = 3600000) = cada 3.600.000 ms = cada 1 hora
    // Spring ejecuta este método automáticamente sin que nadie lo llame
    // -----------------------------------------------------------------------
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void cerrarPrestamosVencidos() {
        List<Prestamo> vencidos = prestamoRepository
                .findByEstadoAndFechaVencimientoBefore(
                        EstadoPrestamo.ACTIVO, LocalDateTime.now());

        for (Prestamo prestamo : vencidos) {
            try {
                // Devolver la copia al License Service
                licenseClient.devolver(prestamo.getLibroId());

                // Marcar el préstamo como vencido
                prestamo.setEstado(EstadoPrestamo.VENCIDO);
                prestamoRepository.save(prestamo);

                System.out.println("Préstamo vencido cerrado — id: " + prestamo.getId() +
                        " | libro: " + prestamo.getLibroId() +
                        " | usuario: " + prestamo.getUsuarioId());

            } catch (Exception e) {
                // Si falla uno, continúa con los demás
                System.err.println("Error al cerrar préstamo id: " + prestamo.getId() +
                        " — " + e.getMessage());
            }
        }
    }

    // -----------------------------------------------------------------------
    // Mapeo privado Entidad → ResponseDTO
    // -----------------------------------------------------------------------
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