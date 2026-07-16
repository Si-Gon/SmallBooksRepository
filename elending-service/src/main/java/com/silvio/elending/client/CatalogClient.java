package com.silvio.elending.client;

import com.silvio.elending.dto.LibroDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

// Circuit Breaker habilitado vía spring.cloud.openfeign.circuitbreaker.enabled=true
// El nombre del cliente (catalog-service) se usa como ID del circuito en resilience4j
@FeignClient(
    name = "catalog-service",
    fallbackFactory = CatalogClientFallbackFactory.class
)
public interface CatalogClient {

    // Consultar un libro por ID en el catálogo
    @GetMapping("/api/catalog/{id}")
    LibroDTO obtenerLibro(@PathVariable("id") Long id);

    // Listar todos los libros del catálogo (paginado)
    @GetMapping("/api/catalog")
    Page<LibroDTO> obtenerTodos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "titulo,asc") String sort);
}
