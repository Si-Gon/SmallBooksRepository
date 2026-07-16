package com.silvio.content.client;

import com.silvio.content.dto.PrestamoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
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
            public List<PrestamoDTO> obtenerPrestamosActivos(String usuarioId) {
                log.warn("Fallback — obtenerPrestamosActivos() — devolviendo lista vacia");
                return Collections.emptyList();
            }
        };
    }
}
