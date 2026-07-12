package com.silvio.elending.client;

import com.silvio.elending.dto.SuscripcionDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

// Fallback factory para SubscriptionClient
// Cuando el circuito está abierto o el servicio no responde,
// aplica plan BASICO por defecto (2 préstamos, 7 días).
// Esto replica el comportamiento actual de PrestamoService cuando
// subscription-service no responde.
@Slf4j
@Component
public class SubscriptionClientFallbackFactory implements FallbackFactory<SubscriptionClient> {

    @Override
    public SubscriptionClient create(Throwable cause) {
        log.warn(" Subscription Service no disponible. Activando fallback. Causa: {}",
                cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName());

        return new SubscriptionClient() {
            @Override
            public SuscripcionDTO obtenerSuscripcion(String usuarioId) {
                log.warn("Fallback — obtenerSuscripcion({}) — aplicando plan BASICO por defecto", usuarioId);
                SuscripcionDTO dto = new SuscripcionDTO();
                dto.setUsuarioId(usuarioId);
                dto.setPlan("BASICO");
                dto.setMaxPrestamos(2);
                dto.setDiasPrestamo(7);
                dto.setActiva(true);
                return dto;
            }
        };
    }
}
