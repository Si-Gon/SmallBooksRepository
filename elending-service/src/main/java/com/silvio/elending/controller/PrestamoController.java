package com.silvio.elending.controller;

import com.silvio.elending.dto.PrestamoRequestDTO;
import com.silvio.elending.dto.PrestamoResponseDTO;
import com.silvio.elending.exception.AccesoDenegadoException;
import com.silvio.elending.service.PrestamoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

import java.util.List;

@Tag(name = "E-Lending", description = "Gestión de préstamos digitales — reglas BASICO (2 préstamos, 7 días) y PREMIUM (5 préstamos, 14 días)")
@RestController
@RequestMapping("/api/lending")
@RequiredArgsConstructor
public class PrestamoController {

    private final PrestamoService prestamoService;

    @Operation(summary = "Crear préstamo", security = @SecurityRequirement(name = "BearerAuth"),
               description = "Crea un nuevo préstamo digital. El usuario se identifica desde el header X-User-Id " +
                             "propagado por el Gateway — no es necesario enviar el usuarioId en el body. " +
                             "Valida límites de plan: BASICO (máx 2 activos), PREMIUM (máx 5 activos)")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Préstamo creado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Límite de préstamos alcanzado según el plan o header faltante"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente (Gateway)"),
        @ApiResponse(responseCode = "404", description = "Libro no encontrado o no disponible")
    })
    @PostMapping("/prestamos")
    public ResponseEntity<PrestamoResponseDTO> crearPrestamo(
            @Valid @RequestBody PrestamoRequestDTO request,
            @Parameter(description = "ID del usuario autenticado propagado por el Gateway", required = true)
            @RequestHeader("X-User-Id") String usuarioId) {

        PrestamoResponseDTO prestamo = prestamoService.crearPrestamo(request, usuarioId);
        prestamo.add(linkTo(methodOn(PrestamoController.class)
                .obtenerActivos(null)).withRel("mis-activos"));
        prestamo.add(linkTo(methodOn(PrestamoController.class)
                .obtenerHistorial(null)).withRel("mi-historial"));

        return ResponseEntity.status(HttpStatus.CREATED).body(prestamo);
    }

    @Operation(summary = "Obtener préstamos activos", security = @SecurityRequirement(name = "BearerAuth"),
               description = "Lista los préstamos activos del usuario autenticado. " +
                             "El usuario se identifica desde el header X-User-Id propagado por el Gateway")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de préstamos activos"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente (Gateway)"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    @GetMapping("/prestamos/activos")
    public ResponseEntity<List<PrestamoResponseDTO>> obtenerActivos(
            @Parameter(description = "ID del usuario autenticado propagado por el Gateway", required = true)
            @RequestHeader("X-User-Id") String usuarioId) {

        List<PrestamoResponseDTO> prestamos = prestamoService.obtenerPrestamosActivos(usuarioId);

        prestamos.forEach(p ->
            p.add(linkTo(methodOn(PrestamoController.class)
                    .obtenerHistorial(null)).withRel("mi-historial")));

        return ResponseEntity.ok(prestamos);
    }

    @Operation(summary = "Obtener historial de préstamos", security = @SecurityRequirement(name = "BearerAuth"),
               description = "Lista todos los préstamos (activos y vencidos) del usuario autenticado")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Historial completo obtenido"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente (Gateway)"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    @GetMapping("/prestamos/historial")
    public ResponseEntity<List<PrestamoResponseDTO>> obtenerHistorial(
            @Parameter(description = "ID del usuario autenticado propagado por el Gateway", required = true)
            @RequestHeader("X-User-Id") String usuarioId) {

        List<PrestamoResponseDTO> prestamos = prestamoService.obtenerHistorial(usuarioId);

        prestamos.forEach(p ->
            p.add(linkTo(methodOn(PrestamoController.class)
                    .obtenerActivos(null)).withRel("mis-activos")));

        return ResponseEntity.ok(prestamos);
    }

    @Operation(summary = "Obtener todos los préstamos (paginado)", security = @SecurityRequirement(name = "BearerAuth"),
               description = "Endpoint interno usado por Analytics Service via Feign. " +
                              "Devuelve todos los préstamos del sistema con paginación para cálculo de estadísticas globales. " +
                              "Ordenado por fechaInicio descendente por defecto, 50 elementos por página.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Página de préstamos obtenida correctamente (contiene content, totalElements, totalPages, number, size)"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    @GetMapping("/prestamos/todos")
    public ResponseEntity<Page<PrestamoResponseDTO>> obtenerTodos(
            @PageableDefault(size = 50, sort = "fechaInicio", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Page<PrestamoResponseDTO> prestamos = prestamoService.obtenerTodos(pageable);
        return ResponseEntity.ok(prestamos);
    }

    @Operation(summary = "Obtener historial por usuario", security = @SecurityRequirement(name = "BearerAuth"),
               description = "Endpoint interno usado por Analytics Service via Feign. " +
                              "Devuelve el historial de préstamos de un usuario específico. " +
                              "Si el usuario no tiene préstamos, devuelve lista vacía (200)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Historial obtenido exitosamente (puede ser lista vacía)"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente (Gateway)"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado — no puedes acceder al historial de otro usuario"),
        @ApiResponse(responseCode = "500", description = "Error interno del servidor")
    })
    @GetMapping("/prestamos/historial/{usuarioId}")
    public ResponseEntity<List<PrestamoResponseDTO>> obtenerHistorialPorId(
            @Parameter(description = "ID del usuario (username)", required = true)
            @PathVariable String usuarioId,
            @Parameter(description = "ID del usuario autenticado propagado por el Gateway", required = false)
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @Parameter(description = "Roles del usuario autenticado propagados por el Gateway", required = false)
            @RequestHeader(value = "X-User-Roles", required = false) String currentUserRoles) {
        validarAccesoUsuario(currentUserId, usuarioId, currentUserRoles);
        List<PrestamoResponseDTO> prestamos = prestamoService.obtenerHistorial(usuarioId);
        prestamos.forEach(p ->
            p.add(linkTo(methodOn(PrestamoController.class)
                    .obtenerHistorialPorId(usuarioId, null, null)).withSelfRel()));
        return ResponseEntity.ok(prestamos);
    }

    // ─── helper: validación IDOR ────────────────────────────────────────────────

    private void validarAccesoUsuario(String currentUserId, String usuarioId, String currentUserRoles) {
        // 1) header X-User-Id ausente o vacío → denegar
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new AccesoDenegadoException("X-User-Id header es obligatorio");
        }
        // 2) admin bypass — ROLE_ADMIN puede acceder a cualquier usuarioId
        if (currentUserRoles != null && currentUserRoles.contains("ROLE_ADMIN")) {
            return;
        }
        // 3) el usuario autenticado debe coincidir con el usuario solicitado
        if (!currentUserId.equals(usuarioId)) {
            throw new AccesoDenegadoException("Acceso denegado — no puedes acceder a los datos de otro usuario");
        }
        // 4) permitir
    }
}
