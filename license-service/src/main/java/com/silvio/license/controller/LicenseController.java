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
        return ResponseEntity.ok(licenseService.obtenerTodas());
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
        return ResponseEntity.ok(licenseService.obtenerPorLibroId(libroId));
    }

    @Operation(summary = "Registrar nueva licencia",
               description = "Crea una nueva licencia para un libro con el total de copias adquiridas")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Licencia creada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos de licencia inválidos"),
        @ApiResponse(responseCode = "409", description = "Ya existe una licencia para este libro")
    })
    @PostMapping
    public ResponseEntity<LicenseResponseDTO> crear(
            @Valid @RequestBody LicenseRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(licenseService.crear(request));
    }

    @Operation(summary = "Actualizar licencia",
               description = "Actualiza el total de copias de la licencia de un libro " +
                             "(por ejemplo al adquirir más copias)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Licencia actualizada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "404", description = "Licencia no encontrada")
    })
    @PutMapping("/{libroId}")
    public ResponseEntity<LicenseResponseDTO> actualizar(
            @Parameter(description = "ID del libro", required = true)
            @PathVariable Long libroId,
            @Valid @RequestBody LicenseRequestDTO request) {
        return ResponseEntity.ok(licenseService.actualizar(libroId, request));
    }

    @Operation(summary = "Descontar copia al prestar",
               description = "Descuenta 1 copia disponible cuando se crea un préstamo. " +
                             "Endpoint interno llamado por E-Lending Service via Feign")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Copia descontada exitosamente"),
        @ApiResponse(responseCode = "400", description = "No hay copias disponibles para prestar"),
        @ApiResponse(responseCode = "404", description = "Licencia no encontrada")
    })
    @PutMapping("/{libroId}/prestar")
    public ResponseEntity<LicenseResponseDTO> prestar(
            @Parameter(description = "ID del libro", required = true)
            @PathVariable Long libroId) {
        return ResponseEntity.ok(licenseService.prestar(libroId));
    }

    @Operation(summary = "Sumar copia al devolver",
               description = "Suma 1 copia disponible cuando vence o se cierra un préstamo. " +
                             "Endpoint interno llamado por E-Lending Service via Feign")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Copia devuelta exitosamente"),
        @ApiResponse(responseCode = "404", description = "Licencia no encontrada")
    })
    @PutMapping("/{libroId}/devolver")
    public ResponseEntity<LicenseResponseDTO> devolver(
            @Parameter(description = "ID del libro", required = true)
            @PathVariable Long libroId) {
        return ResponseEntity.ok(licenseService.devolver(libroId));
    }
}