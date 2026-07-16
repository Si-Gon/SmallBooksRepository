package com.silvio.analytics.client;

import com.silvio.analytics.dto.PrestamoAnalyticsDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class LendingClientFallbackFactory implements FallbackFactory<LendingClient> {

    @Override
    public LendingClient create(Throwable cause) {
        log.warn("E-Lending Service no disponible. Activando fallback. Causa: {}",
                cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName());

        return new LendingClient() {
            @Override
            public List<PrestamoAnalyticsDTO> obtenerHistorial(String usuarioId) {
                log.warn("Fallback — obtenerHistorial({}) — devolviendo lista vacia", usuarioId);
                return Collections.emptyList();
            }

            @Override
            public Page<PrestamoAnalyticsDTO> obtenerTodos() {
                log.warn("Fallback — obtenerTodos() — devolviendo pagina vacia");
                return Page.empty();
            }
        };
    }
}
