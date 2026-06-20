package com.silvio.search.controller;

import com.silvio.search.dto.SearchResultDTO;
import com.silvio.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Search", description = "Búsqueda y descubrimiento de libros — consulta el catálogo via Feign desde Catalog Service")
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "Listar todos los libros",
               description = "Devuelve el catálogo completo de libros disponibles en la plataforma")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista completa obtenida exitosamente"),
        @ApiResponse(responseCode = "500", description = "Error al consultar el catálogo")
    })
    @GetMapping
    public ResponseEntity<List<SearchResultDTO>> obtenerTodos() {
        return ResponseEntity.ok(searchService.obtenerTodos());
    }

    @Operation(summary = "Listar libros disponibles",
               description = "Devuelve solo los libros disponibles para préstamo en este momento")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de disponibles obtenida exitosamente")
    })
    @GetMapping("/disponibles")
    public ResponseEntity<List<SearchResultDTO>> disponibles() {
        return ResponseEntity.ok(searchService.buscarDisponibles());
    }

    @Operation(summary = "Buscar libros",
               description = "Búsqueda combinable por título, autor o género. " +
                             "Todos los parámetros son opcionales — se pueden usar individualmente o combinados. " +
                             "Ejemplo: /api/search/buscar?titulo=harry&genero=fantasia")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resultados de búsqueda obtenidos"),
        @ApiResponse(responseCode = "400", description = "Parámetros de búsqueda inválidos")
    })
    @GetMapping("/buscar")
    public ResponseEntity<List<SearchResultDTO>> buscar(
            @Parameter(description = "Título del libro (búsqueda parcial)")
            @RequestParam(required = false) String titulo,
            @Parameter(description = "Nombre del autor (búsqueda parcial)")
            @RequestParam(required = false) String autor,
            @Parameter(description = "Género literario")
            @RequestParam(required = false) String genero) {
        return ResponseEntity.ok(searchService.buscar(titulo, autor, genero));
    }
}