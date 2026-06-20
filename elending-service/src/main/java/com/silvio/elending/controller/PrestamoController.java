package com.silvio.elending.controller;

import com.silvio.elending.dto.PrestamoRequestDTO;
import com.silvio.elending.dto.PrestamoResponseDTO;
import com.silvio.elending.service.PrestamoService;
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

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

import java.util.List;

@Tag(name = "E-Lending", description = "Gestión de préstamos digitales — reglas BASICO (2 préstamos, 7 días) y PREMIUM (5 préstamos, 14 días)")
@RestController
@RequestMapping("/api/lending")
@RequiredArgsConstructor
public class PrestamoController {

    private final PrestamoService prestamoService;

    @Operation(summary = "Crear préstamo",
               description = "Crea un nuevo préstamo digital. El usuario se identifica desde el token JWT — " +
                             "no es necesario enviar el usuarioId en el body. " +
                             "Valida límites de plan: BASICO (máx 2 activos), PREMIUM (máx 5 activos)")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Préstamo creado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Límite de préstamos alcanzado según el plan"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
        @ApiResponse(responseCode = "404", description = "Libro no encontrado o no disponible")
    })
    @PostMapping("/prestamos")
    public ResponseEntity<PrestamoResponseDTO> crearPrestamo(
            @Valid @RequestBody PrestamoRequestDTO request,
            @Parameter(description = "Token JWT en formato: Bearer {token}", required = true)
            @RequestHeader("Authorization") String authHeader) {

        String usuarioId = extraerUsuarioDelToken(authHeader);
        PrestamoResponseDTO prestamo = prestamoService.crearPrestamo(request, usuarioId);
        prestamo.add(linkTo(methodOn(PrestamoController.class).obtenerActivos(authHeader)).withRel("mis-activos"));
        prestamo.add(linkTo(methodOn(PrestamoController.class).obtenerHistorial(authHeader)).withRel("mi-historial"));

        return ResponseEntity.status(HttpStatus.CREATED).body(prestamo);
    }

    @Operation(summary = "Obtener préstamos activos",
               description = "Lista los préstamos activos del usuario autenticado. " +
                             "El usuario se identifica desde el token JWT")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de préstamos activos"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente")
    })
    @GetMapping("/prestamos/activos")
    public ResponseEntity<List<PrestamoResponseDTO>> obtenerActivos(
            @Parameter(description = "Token JWT en formato: Bearer {token}", required = true)
            @RequestHeader("Authorization") String authHeader) {

        String usuarioId = extraerUsuarioDelToken(authHeader);
        List<PrestamoResponseDTO> prestamos = prestamoService.obtenerPrestamosActivos(usuarioId);

        prestamos.forEach(p ->
        p.add(linkTo(methodOn(PrestamoController.class).obtenerHistorial(authHeader)).withRel("mi-historial")));

        return ResponseEntity.ok(prestamos);
    }

    @Operation(summary = "Obtener historial de préstamos",
               description = "Lista todos los préstamos (activos y vencidos) del usuario autenticado")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Historial completo obtenido"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente")
    })
    @GetMapping("/prestamos/historial")
    public ResponseEntity<List<PrestamoResponseDTO>> obtenerHistorial(
            @Parameter(description = "Token JWT en formato: Bearer {token}", required = true)
            @RequestHeader("Authorization") String authHeader) {

        String usuarioId = extraerUsuarioDelToken(authHeader);
        List<PrestamoResponseDTO> prestamos = prestamoService.obtenerHistorial(usuarioId);

    prestamos.forEach(p ->
        p.add(linkTo(methodOn(PrestamoController.class).obtenerActivos(authHeader)).withRel("mis-activos")));

    return ResponseEntity.ok(prestamos);
    }

    @Operation(summary = "Obtener todos los préstamos",
               description = "Endpoint interno usado por Analytics Service via Feign. " +
                             "Devuelve todos los préstamos del sistema para cálculo de estadísticas globales")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista completa de préstamos")
    })
    @GetMapping("/prestamos/todos")
    public ResponseEntity<List<PrestamoResponseDTO>> obtenerTodos() {
        List<PrestamoResponseDTO> prestamos = prestamoService.obtenerTodos();
        prestamos.forEach(p ->
        p.add(linkTo(methodOn(PrestamoController.class).obtenerTodos()).withSelfRel()));
        return ResponseEntity.ok(prestamos);
    }

    @Operation(summary = "Obtener historial por usuario",
               description = "Endpoint interno usado por Analytics Service via Feign. " +
                             "Devuelve el historial de préstamos de un usuario específico")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Historial obtenido exitosamente"),
        @ApiResponse(responseCode = "404", description = "Usuario no encontrado")
    })
    @GetMapping("/prestamos/historial/{usuarioId}")
    public ResponseEntity<List<PrestamoResponseDTO>> obtenerHistorialPorId(
            @Parameter(description = "ID del usuario (username)", required = true)
            @PathVariable String usuarioId) {
        List<PrestamoResponseDTO> prestamos = prestamoService.obtenerHistorial(usuarioId);
        prestamos.forEach(p ->
        p.add(linkTo(methodOn(PrestamoController.class).obtenerHistorialPorId(usuarioId)).withSelfRel())
    );
        return ResponseEntity.ok(prestamos);
    }

    private String extraerUsuarioDelToken(String authHeader) {
        try {
            String token = authHeader.substring(7);
            String payload = token.split("\\.")[1];
            String decodedPayload = new String(
                    java.util.Base64.getUrlDecoder().decode(payload));
            String sub = decodedPayload.split("\"sub\":\"")[1].split("\"")[0];
            return sub;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo extraer el usuario del token");
        }
    }
}