package com.silvio.subscription.service;

import com.silvio.subscription.dto.SuscripcionRequestDTO;
import com.silvio.subscription.dto.SuscripcionResponseDTO;
import com.silvio.subscription.model.Suscripcion;
import com.silvio.subscription.model.Suscripcion.PlanSuscripcion;
import com.silvio.subscription.repository.SuscripcionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.observation.annotation.Observed;
import java.util.NoSuchElementException;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuscripcionService {

    private final SuscripcionRepository suscripcionRepository;

    private static final int BASICO_MAX_PRESTAMOS = 2;
    private static final int BASICO_DIAS_PRESTAMO = 7;
    private static final int PREMIUM_MAX_PRESTAMOS = 5;
    private static final int PREMIUM_DIAS_PRESTAMO = 14;

    @Observed(name = "subscription.obtenerPorUsuario")
    @Transactional(readOnly = true)
    public SuscripcionResponseDTO obtenerPorUsuario(String usuarioId) {
        log.info("Consultando suscripción activa del usuario: {}", usuarioId);
        Suscripcion suscripcion = suscripcionRepository
                .findByUsuarioIdAndActivaTrue(usuarioId)
                .orElseThrow(() -> {
                    log.warn("Sin suscripción activa para usuario: {}", usuarioId);
                    return new NoSuchElementException("Suscripción activa no encontrada para usuario: " + usuarioId);
                });
        return mapearADto(suscripcion);
    }

    @Observed(name = "subscription.crear")
    @Transactional
    public SuscripcionResponseDTO crear(SuscripcionRequestDTO request, String usuarioId) {
        log.info("Creando suscripción {} para usuario: {}, duración: {} mes(es)",
                request.getPlan(), usuarioId, request.getMeses());

        suscripcionRepository.findByUsuarioIdAndActivaTrue(usuarioId)
                .ifPresent(s -> {
                    log.info("Cancelando suscripción anterior {} para usuario: {}",
                            s.getPlan(), usuarioId);
                    s.setActiva(false);
                    suscripcionRepository.save(s);
                });

        LocalDateTime ahora = LocalDateTime.now();
        Suscripcion suscripcion = new Suscripcion();
        suscripcion.setUsuarioId(usuarioId);
        suscripcion.setPlan(request.getPlan());
        suscripcion.setFechaInicio(ahora);
        suscripcion.setFechaFin(ahora.plusMonths(request.getMeses()));
        suscripcion.setActiva(true);

        Suscripcion guardada = suscripcionRepository.save(suscripcion);
        log.info("Suscripción creada — id: {}, usuario: {}, plan: {}, vence: {}",
                guardada.getId(), usuarioId, guardada.getPlan(), guardada.getFechaFin());
        return mapearADto(guardada);
    }

    @Observed(name = "subscription.cancelar")
    @Transactional
    public SuscripcionResponseDTO cancelar(String usuarioId) {
        log.info("Cancelando suscripción del usuario: {}", usuarioId);
        Suscripcion suscripcion = suscripcionRepository
                .findByUsuarioIdAndActivaTrue(usuarioId)
                .orElseThrow(() -> new NoSuchElementException("Suscripción activa no encontrada para usuario: " + usuarioId));

        suscripcion.setActiva(false);
        log.info("Suscripción cancelada — usuario: {}, plan: {}", usuarioId, suscripcion.getPlan());
        return mapearADto(suscripcionRepository.save(suscripcion));
    }

    private SuscripcionResponseDTO mapearADto(Suscripcion s) {
        SuscripcionResponseDTO dto = new SuscripcionResponseDTO();
        dto.setId(s.getId());
        dto.setUsuarioId(s.getUsuarioId());
        dto.setPlan(s.getPlan());
        dto.setFechaInicio(s.getFechaInicio());
        dto.setFechaFin(s.getFechaFin());
        dto.setActiva(s.getActiva());

        if (s.getPlan() == PlanSuscripcion.PREMIUM) {
            dto.setMaxPrestamos(PREMIUM_MAX_PRESTAMOS);
            dto.setDiasPrestamo(PREMIUM_DIAS_PRESTAMO);
        } else {
            dto.setMaxPrestamos(BASICO_MAX_PRESTAMOS);
            dto.setDiasPrestamo(BASICO_DIAS_PRESTAMO);
        }
        return dto;
    }
}