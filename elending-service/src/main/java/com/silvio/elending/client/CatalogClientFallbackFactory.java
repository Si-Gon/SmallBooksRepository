package com.silvio.elending.client;

import com.silvio.elending.dto.LibroDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

// Fallback factory para CatalogClient
// Cuando el circuito está abierto o el servicio no responde,
// devuelve respuestas degradadas: libro vacío o lista vacía.
@Slf4j
@Component
public class CatalogClientFallbackFactory implements FallbackFactory<CatalogClient> {

    @Override
    public CatalogClient create(Throwable cause) {
        log.warn(" Catalog Service no disponible. Activando fallback. Causa: {}",
                cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName());

        return new CatalogClient() {
            @Override
            public LibroDTO obtenerLibro(Long id) {
                log.warn("Fallback — obtenerLibro({}) — devolviendo libro desconocido", id);
                LibroDTO dto = new LibroDTO();
                dto.setId(id);
                dto.setTitulo("No disponible");
                dto.setAutor("No disponible");
                dto.setIsbn("No disponible");
                dto.setGenero("No disponible");
                dto.setDisponible(false);
                return dto;
            }

            @Override
            public Page<LibroDTO> obtenerTodos(int page, int size, String sort) {
                log.warn("Fallback — obtenerTodos() — devolviendo página vacía");
                return Page.empty();
            }
        };
    }
}
