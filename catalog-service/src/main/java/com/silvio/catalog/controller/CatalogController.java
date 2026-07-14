package com.silvio.catalog.controller;

import com.silvio.catalog.dto.LibroRequestDTO;
import com.silvio.catalog.dto.LibroResponseDTO;
import com.silvio.catalog.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

import java.util.List;

@Tag(name = "Catalog", description = "Gestión del catálogo de libros de SmallBooks")
@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @Operation(summary = "Listar todos los libros",
               description = "Devuelve la lista completa de libros registrados en el catálogo")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    @GetMapping
    public ResponseEntity<List<LibroResponseDTO>> obtenerTodos() {
        List<LibroResponseDTO> libros = catalogService.obtenerTodos();

    libros.forEach(dto ->
            dto.add(linkTo(methodOn(CatalogController.class).obtenerPorId(dto.getId())).withSelfRel())
    );

    return ResponseEntity.ok(libros);
    }

    @Operation(summary = "Listar libros disponibles",
               description = "Devuelve solo los libros marcados como disponibles para préstamo")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    @GetMapping("/disponibles")
    public ResponseEntity<List<LibroResponseDTO>> obtenerDisponibles() {
         List<LibroResponseDTO> libros = catalogService.obtenerDisponibles();

    libros.forEach(dto ->
            dto.add(linkTo(methodOn(CatalogController.class).obtenerPorId(dto.getId())).withSelfRel())
    );

    return ResponseEntity.ok(libros);
    }

    @Operation(summary = "Obtener libro por ID",
               description = "Devuelve un libro específico según su identificador")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Libro encontrado"),
        @ApiResponse(responseCode = "400", description = "ID del libro inválido"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
        @ApiResponse(responseCode = "404", description = "Libro no encontrado")
    })
    @GetMapping("/{id}")
    public ResponseEntity<LibroResponseDTO> obtenerPorId(
            @Parameter(description = "ID del libro", required = true)
            @PathVariable Long id) {
            LibroResponseDTO dto = catalogService.obtenerPorId(id);
            dto.add(linkTo(methodOn(CatalogController.class).obtenerPorId(id)).withSelfRel());
            dto.add(linkTo(methodOn(CatalogController.class).obtenerTodos()).withRel("todos"));
            dto.add(linkTo(methodOn(CatalogController.class).obtenerDisponibles()).withRel("disponibles"));
            dto.add(linkTo(methodOn(CatalogController.class).eliminar(id)).withRel("eliminar"));


        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Buscar libros",
               description = "Búsqueda por título, autor o género. Los parámetros son opcionales y combinables")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Búsqueda realizada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Parámetros de búsqueda inválidos"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente")
    })
    @GetMapping("/buscar")
    public ResponseEntity<List<LibroResponseDTO>> buscar(
            @Parameter(description = "Título del libro (parcial)") 
            @RequestParam(required = false) String titulo,
            @Parameter(description = "Nombre del autor (parcial)") 
            @RequestParam(required = false) String autor,
            @Parameter(description = "Género literario") 
            @RequestParam(required = false) String genero) {
        return ResponseEntity.ok(catalogService.buscar(titulo, autor, genero));
    }

    @Operation(summary = "Agregar libro",
               description = "Registra un nuevo libro en el catálogo")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Libro creado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos del libro inválidos o incompletos"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
        @ApiResponse(responseCode = "409", description = "El ISBN del libro ya existe")
    })
    @PostMapping
    public ResponseEntity<LibroResponseDTO> agregar(
            @Valid @RequestBody LibroRequestDTO request) {
                 LibroResponseDTO dto = catalogService.agregar(request);

            dto.add(linkTo(methodOn(CatalogController.class).obtenerPorId(dto.getId())).withSelfRel());
            dto.add(linkTo(methodOn(CatalogController.class).obtenerTodos()).withRel("todos"));

        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
        
    }

    @Operation(summary = "Actualizar libro",
               description = "Actualiza todos los campos de un libro existente")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Libro actualizado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos o ID incorrecto"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
        @ApiResponse(responseCode = "404", description = "Libro no encontrado")
    })
    @PutMapping("/{id}")
    public ResponseEntity<LibroResponseDTO> actualizar(
            @Parameter(description = "ID del libro a actualizar", required = true)
            @PathVariable Long id,
            @Valid @RequestBody LibroRequestDTO request) {
        LibroResponseDTO dto = catalogService.actualizar(id, request);

            dto.add(linkTo(methodOn(CatalogController.class).obtenerPorId(id)).withSelfRel());
            dto.add(linkTo(methodOn(CatalogController.class).obtenerTodos()).withRel("todos"));

        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Cambiar disponibilidad",
               description = "Marca un libro como disponible o no disponible. " +
                             "Usado internamente por E-Lending via Feign al crear/devolver préstamos")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Disponibilidad actualizada"),
        @ApiResponse(responseCode = "400", description = "Parámetro 'disponible' inválido o ausente"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
        @ApiResponse(responseCode = "404", description = "Libro no encontrado")
    })
    @PatchMapping("/{id}/disponibilidad")
    public ResponseEntity<LibroResponseDTO> cambiarDisponibilidad(
            @Parameter(description = "ID del libro", required = true)
            @PathVariable Long id,
            @Parameter(description = "true = disponible, false = prestado", required = true)
            @RequestParam Boolean disponible) {
        return ResponseEntity.ok(catalogService.cambiarDisponibilidad(id, disponible));
    }

    @Operation(summary = "Eliminar libro",
               description = "Elimina permanentemente un libro del catálogo")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Libro eliminado exitosamente"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
        @ApiResponse(responseCode = "404", description = "Libro no encontrado")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            @Parameter(description = "ID del libro a eliminar", required = true)
            @PathVariable Long id) {
        catalogService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}