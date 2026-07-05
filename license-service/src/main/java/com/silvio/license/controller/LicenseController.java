package com.silvio.license.controller;

import com.silvio.license.dto.LicenseRequestDTO;
import com.silvio.license.dto.LicenseResponseDTO;
import com.silvio.license.service.LicenseService;
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

import java.util.List;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@Tag(name = "Licenses", description = "Gestión de licencias y control de copias disponibles por libro")
@RestController
@RequestMapping("/api/licenses")
@RequiredArgsConstructor
public class LicenseController {

    private final LicenseService licenseService;

    @Operation(summary = "Listar todas las licencias",
               description = "Devuelve todas las licencias registradas con su cantidad de copias totales y disponibles")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de licencias obtenida exitosamente"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    @GetMapping
    public ResponseEntity<List<LicenseResponseDTO>> obtenerTodas() {
        List<LicenseResponseDTO> lista = licenseService.obtenerTodas();

        lista.forEach(dto ->
            dto.add(linkTo(methodOn(LicenseController.class)
                    .obtenerPorLibroId(dto.getLibroId())).withSelfRel())
        );

        return ResponseEntity.ok(lista);
    }

    @Operation(summary = "Obtener licencia por libro",
               description = "Consulta la licencia de un libro específico. " +
                             "Usado por E-Lending Service via Feign antes de crear un préstamo " +
                             "para verificar si hay copias disponibles")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Licencia encontrada"),
        @ApiResponse(responseCode = "404", description = "No existe licencia para este libro")
    })
    @GetMapping("/{libroId}")
    public ResponseEntity<LicenseResponseDTO> obtenerPorLibroId(
            @Parameter(description = "ID del libro", required = true)
            @PathVariable Long libroId) {
        LicenseResponseDTO dto = licenseService.obtenerPorLibroId(libroId);

        dto.add(linkTo(methodOn(LicenseController.class)
                .obtenerPorLibroId(libroId)).withSelfRel());
        dto.add(linkTo(methodOn(LicenseController.class)
                .obtenerTodas()).withRel("todas"));
        dto.add(linkTo(methodOn(LicenseController.class)
                .prestar(libroId)).withRel("prestar"));
        dto.add(linkTo(methodOn(LicenseController.class)
                .devolver(libroId)).withRel("devolver"));

        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Registrar nueva licencia")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Licencia creada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos de licencia inválidos"),
        @ApiResponse(responseCode = "409", description = "Ya existe una licencia para este libro")
    })
    @PostMapping
    public ResponseEntity<LicenseResponseDTO> crear(
            @Valid @RequestBody LicenseRequestDTO request) {
        LicenseResponseDTO dto = licenseService.crear(request);

        dto.add(linkTo(methodOn(LicenseController.class)
                .obtenerPorLibroId(dto.getLibroId())).withSelfRel());
        dto.add(linkTo(methodOn(LicenseController.class)
                .obtenerTodas()).withRel("todas"));

        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @Operation(summary = "Actualizar licencia")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Licencia actualizada exitosamente"),
        @ApiResponse(responseCode = "404", description = "Licencia no encontrada")
    })
    @PutMapping("/{libroId}")
    public ResponseEntity<LicenseResponseDTO> actualizar(
            @Parameter(description = "ID del libro", required = true)
            @PathVariable Long libroId,
            @Valid @RequestBody LicenseRequestDTO request) {
        LicenseResponseDTO dto = licenseService.actualizar(libroId, request);

        dto.add(linkTo(methodOn(LicenseController.class)
                .obtenerPorLibroId(libroId)).withSelfRel());
        dto.add(linkTo(methodOn(LicenseController.class)
                .obtenerTodas()).withRel("todas"));

        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Descontar copia al prestar")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Copia descontada exitosamente"),
        @ApiResponse(responseCode = "404", description = "Licencia no encontrada"),
        @ApiResponse(responseCode = "422", description = "No hay copias disponibles para prestar")
    })
    @PutMapping("/{libroId}/prestar")
    public ResponseEntity<LicenseResponseDTO> prestar(
            @Parameter(description = "ID del libro", required = true)
            @PathVariable Long libroId) {
        LicenseResponseDTO dto = licenseService.prestar(libroId);

        dto.add(linkTo(methodOn(LicenseController.class)
                .obtenerPorLibroId(libroId)).withSelfRel());
        dto.add(linkTo(methodOn(LicenseController.class)
                .devolver(libroId)).withRel("devolver"));

        return ResponseEntity.ok(dto);
    }

    @Operation(summary = "Sumar copia al devolver")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Copia devuelta exitosamente"),
        @ApiResponse(responseCode = "404", description = "Licencia no encontrada"),
        @ApiResponse(responseCode = "422", description = "Todas las copias ya están disponibles")
    })
    @PutMapping("/{libroId}/devolver")
    public ResponseEntity<LicenseResponseDTO> devolver(
            @Parameter(description = "ID del libro", required = true)
            @PathVariable Long libroId) {
        LicenseResponseDTO dto = licenseService.devolver(libroId);

        dto.add(linkTo(methodOn(LicenseController.class)
                .obtenerPorLibroId(libroId)).withSelfRel());
        dto.add(linkTo(methodOn(LicenseController.class)
                .prestar(libroId)).withRel("prestar"));

        return ResponseEntity.ok(dto);
    }
}