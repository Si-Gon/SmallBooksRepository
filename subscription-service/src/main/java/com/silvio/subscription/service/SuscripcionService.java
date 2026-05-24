package com.silvio.subscription.service;

import com.silvio.subscription.dto.SuscripcionRequestDTO;
import com.silvio.subscription.dto.SuscripcionResponseDTO;
import com.silvio.subscription.model.Suscripcion;
import com.silvio.subscription.model.Suscripcion.PlanSuscripcion;
import com.silvio.subscription.repository.SuscripcionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SuscripcionRepository suscripcionRepository;

    // Reglas por plan — centralizadas aquí para fácil mantenimiento
    private static final int BASICO_MAX_PRESTAMOS = 2;
    private static final int BASICO_DIAS_PRESTAMO = 7;
    private static final int PREMIUM_MAX_PRESTAMOS = 5;
    private static final int PREMIUM_DIAS_PRESTAMO = 14;

    // Consultar suscripción activa — usado por E-Lending via Feign
   
    public SuscripcionResponseDTO obtenerPorUsuario(String usuarioId) {
        Suscripcion suscripcion = suscripcionRepository
                .findByUsuarioIdAndActivaTrue(usuarioId)
                .orElseThrow(() -> new RuntimeException(
                        "No hay suscripción activa para el usuario: " + usuarioId));
        return mapearADto(suscripcion);
    }

    // Crear nueva suscripción
    
    @Transactional
    public SuscripcionResponseDTO crear(SuscripcionRequestDTO request, String usuarioId) {

        // Si ya tiene una activa, cancelarla primero
        suscripcionRepository.findByUsuarioIdAndActivaTrue(usuarioId)
                .ifPresent(s -> {
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

        return mapearADto(suscripcionRepository.save(suscripcion));
    }

    // Cancelar suscripción
    
    @Transactional
    public SuscripcionResponseDTO cancelar(String usuarioId) {
        Suscripcion suscripcion = suscripcionRepository
                .findByUsuarioIdAndActivaTrue(usuarioId)
                .orElseThrow(() -> new RuntimeException(
                        "No hay suscripción activa para el usuario: " + usuarioId));

        suscripcion.setActiva(false);
        return mapearADto(suscripcionRepository.save(suscripcion));
    }

    // Mapeo — incluye reglas del plan en la respuesta
    // E-Lending consulta estos valores para aplicar límites
    
    private SuscripcionResponseDTO mapearADto(Suscripcion s) {
        SuscripcionResponseDTO dto = new SuscripcionResponseDTO();
        dto.setId(s.getId());
        dto.setUsuarioId(s.getUsuarioId());
        dto.setPlan(s.getPlan());
        dto.setFechaInicio(s.getFechaInicio());
        dto.setFechaFin(s.getFechaFin());
        dto.setActiva(s.getActiva());

        // Aplicar reglas según el plan
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