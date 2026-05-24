package com.silvio.catalog.controller;

import com.silvio.catalog.dto.LibroRequestDTO;
import com.silvio.catalog.dto.LibroResponseDTO;
import com.silvio.catalog.service.CatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Endpoints REST semánticos (IE1.1.2):
//   GET    /api/catalog              → listar todos
//   GET    /api/catalog/disponibles  → listar disponibles
//   GET    /api/catalog/{id}         → obtener uno
//   GET    /api/catalog/buscar       → buscar por título/autor/género
//   POST   /api/catalog              → agregar libro
//   PUT    /api/catalog/{id}         → actualizar libro
//   PATCH  /api/catalog/{id}/disponibilidad → cambiar disponibilidad
//   DELETE /api/catalog/{id}         → eliminar libro

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping
    public ResponseEntity<List<LibroResponseDTO>> obtenerTodos() {
        return ResponseEntity.ok(catalogService.obtenerTodos());
    }

    @GetMapping("/disponibles")
    public ResponseEntity<List<LibroResponseDTO>> obtenerDisponibles() {
        return ResponseEntity.ok(catalogService.obtenerDisponibles());
    }

    @GetMapping("/{id}")
    public ResponseEntity<LibroResponseDTO> obtenerPorId(@PathVariable Long id) {
        return ResponseEntity.ok(catalogService.obtenerPorId(id));
    }

    // Búsqueda con parámetros opcionales en la URL:
    // GET /api/catalog/buscar?titulo=harry
    // GET /api/catalog/buscar?autor=rowling
    // GET /api/catalog/buscar?genero=fantasia
    @GetMapping("/buscar")
    public ResponseEntity<List<LibroResponseDTO>> buscar(
            @RequestParam(required = false) String titulo,
            @RequestParam(required = false) String autor,
            @RequestParam(required = false) String genero) {
        return ResponseEntity.ok(catalogService.buscar(titulo, autor, genero));
    }

    @PostMapping
    public ResponseEntity<LibroResponseDTO> agregar(
            @Valid @RequestBody LibroRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.agregar(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LibroResponseDTO> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody LibroRequestDTO request) {
        return ResponseEntity.ok(catalogService.actualizar(id, request));
    }

    // PATCH para cambiar disponibilidad — usado internamente por E-Lending via Feign
    // También puede usarlo un admin directamente si necesita marcar un libro manualmente
    @PatchMapping("/{id}/disponibilidad")
    public ResponseEntity<LibroResponseDTO> cambiarDisponibilidad(
            @PathVariable Long id,
            @RequestParam Boolean disponible) {
        return ResponseEntity.ok(catalogService.cambiarDisponibilidad(id, disponible));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        catalogService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}