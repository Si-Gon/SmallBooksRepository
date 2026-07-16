package com.silvio.content.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class IngestionClientFallbackFactory implements FallbackFactory<IngestionClient> {

    @Override
    public IngestionClient create(Throwable cause) {
        log.warn("Ingestion Service no disponible. Activando fallback. Causa: {}",
                cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName());

        return new IngestionClient() {
            @Override
            public byte[] obtenerBytes(Long libroId) {
                log.warn("Fallback — obtenerBytes({}) — devolviendo arreglo vacio", libroId);
                return new byte[0];
            }
        };
    }
}
