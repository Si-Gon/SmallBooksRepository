package com.silvio.search.controller;

import com.silvio.search.dto.SearchResultDTO;
import com.silvio.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Endpoints:
//   GET /api/search                  → todos los libros
//   GET /api/search/disponibles      → solo disponibles para préstamo
//   GET /api/search/buscar           → búsqueda por título, autor o género
//     ?titulo=...
//     ?autor=...
//     ?genero=...

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public ResponseEntity<List<SearchResultDTO>> obtenerTodos() {
        return ResponseEntity.ok(searchService.obtenerTodos());
    }

    @GetMapping("/disponibles")
    public ResponseEntity<List<SearchResultDTO>> disponibles() {
        return ResponseEntity.ok(searchService.buscarDisponibles());
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<SearchResultDTO>> buscar(
            @RequestParam(required = false) String titulo,
            @RequestParam(required = false) String autor,
            @RequestParam(required = false) String genero) {
        return ResponseEntity.ok(searchService.buscar(titulo, autor, genero));
    }
}