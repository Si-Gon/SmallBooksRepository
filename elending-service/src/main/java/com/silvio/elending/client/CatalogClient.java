package com.silvio.elending.client;

import com.silvio.elending.dto.LibroDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

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

    // Listar todos los libros del catálogo
    @GetMapping("/api/catalog")
    List<LibroDTO> obtenerTodos();
}
