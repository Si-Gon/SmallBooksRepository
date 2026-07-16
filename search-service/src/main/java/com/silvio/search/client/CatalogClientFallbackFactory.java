package com.silvio.search.client;

import com.silvio.search.dto.LibroCatalogDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class CatalogClientFallbackFactory implements FallbackFactory<CatalogClient> {

    @Override
    public CatalogClient create(Throwable cause) {
        log.warn("Catalog Service no disponible. Activando fallback. Causa: {}",
                cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName());

        return new CatalogClient() {
            @Override
            public Page<LibroCatalogDTO> obtenerTodos(int page, int size, String sort) {
                log.warn("Fallback — obtenerTodos() — devolviendo pagina vacia");
                return Page.empty();
            }

            @Override
            public List<LibroCatalogDTO> buscar(String titulo, String autor, String genero) {
                log.warn("Fallback — buscar() — devolviendo lista vacia");
                return Collections.emptyList();
            }

            @Override
            public List<LibroCatalogDTO> obtenerDisponibles() {
                log.warn("Fallback — obtenerDisponibles() — devolviendo lista vacia");
                return Collections.emptyList();
            }
        };
    }
}
