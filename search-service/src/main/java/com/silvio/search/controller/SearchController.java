package com.silvio.search.controller;

import com.silvio.search.dto.SearchResultDTO;
import com.silvio.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@Tag(name = "Search", description = "Búsqueda y descubrimiento de libros — consulta el catálogo via Feign desde Catalog Service")
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @Operation(summary = "Listar todos los libros", security = @SecurityRequirement(name = "BearerAuth"),
               description = "Devuelve el catálogo completo de libros disponibles en la plataforma")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista completa obtenida exitosamente"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
        @ApiResponse(responseCode = "503", description = "Error de comunicación con el catálogo"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    @GetMapping
    public ResponseEntity<List<SearchResultDTO>> obtenerTodos() {
        List<SearchResultDTO> lista = searchService.obtenerTodos();

        lista.forEach(dto ->
            dto.add(linkTo(methodOn(SearchController.class)
                    .obtenerTodos()).withSelfRel())
        );

        return ResponseEntity.ok(lista);
    }

    @Operation(summary = "Listar libros disponibles", security = @SecurityRequirement(name = "BearerAuth"),
               description = "Devuelve solo los libros disponibles para préstamo en este momento")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de disponibles obtenida exitosamente"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
        @ApiResponse(responseCode = "503", description = "Error de comunicación con el catálogo"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    @GetMapping("/disponibles")
    public ResponseEntity<List<SearchResultDTO>> disponibles() {
        List<SearchResultDTO> lista = searchService.buscarDisponibles();

        lista.forEach(dto ->
            dto.add(linkTo(methodOn(SearchController.class)
                    .disponibles()).withSelfRel())
        );

        return ResponseEntity.ok(lista);
    }

    @Operation(summary = "Buscar libros", security = @SecurityRequirement(name = "BearerAuth"),
               description = "Búsqueda combinable por título, autor o género. " +
                              "Todos los parámetros son opcionales. " +
                              "Ejemplo: /api/search/buscar?titulo=harry&genero=fantasia")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resultados de búsqueda obtenidos"),
        @ApiResponse(responseCode = "400", description = "Parámetros de búsqueda inválidos"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
        @ApiResponse(responseCode = "503", description = "Error de comunicación con el catálogo"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    @GetMapping("/buscar")
    public ResponseEntity<List<SearchResultDTO>> buscar(
            @Parameter(description = "Título del libro (búsqueda parcial)")
            @RequestParam(required = false) String titulo,
            @Parameter(description = "Nombre del autor (búsqueda parcial)")
            @RequestParam(required = false) String autor,
            @Parameter(description = "Género literario")
            @RequestParam(required = false) String genero) {
        List<SearchResultDTO> lista = searchService.buscar(titulo, autor, genero);

        lista.forEach(dto ->
            dto.add(linkTo(methodOn(SearchController.class)
                    .buscar(titulo, autor, genero)).withSelfRel())
        );

        return ResponseEntity.ok(lista);
    }
}