package com.silvio.search.client;

import com.silvio.search.dto.LibroCatalogDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "catalog-service")
public interface CatalogClient {

    // Obtener todos los libros del catálogo
    @GetMapping("/api/catalog")
    List<LibroCatalogDTO> obtenerTodos();

    // Buscar por criterio en el catalog-service
    @GetMapping("/api/catalog/buscar")
    List<LibroCatalogDTO> buscar(
            @RequestParam(required = false) String titulo,
            @RequestParam(required = false) String autor,
            @RequestParam(required = false) String genero);

    // Solo los disponibles
    @GetMapping("/api/catalog/disponibles")
    List<LibroCatalogDTO> obtenerDisponibles();
}