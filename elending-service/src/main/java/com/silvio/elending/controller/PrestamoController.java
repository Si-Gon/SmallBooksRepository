package com.silvio.elending.controller;

import com.silvio.elending.dto.PrestamoRequestDTO;
import com.silvio.elending.dto.PrestamoResponseDTO;
import com.silvio.elending.service.PrestamoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Endpoints REST semánticos:
//   POST /api/lending/prestamos              → crear préstamo
//   GET  /api/lending/prestamos/activos      → mis préstamos activos
//   GET  /api/lending/prestamos/historial    → mi historial completo

// El usuarioId NO viene en el body — se extrae del header Authorization
// Esto evita que un usuario pueda crear préstamos a nombre de otro

@RestController
@RequestMapping("/api/lending")
@RequiredArgsConstructor
public class PrestamoController {

    private final PrestamoService prestamoService;

    // -----------------------------------------------------------------------
    // POST /api/lending/prestamos
    // El token JWT viene en el header Authorization: Bearer <token>
    // Extraemos el username del token para identificar al usuario
    // -----------------------------------------------------------------------
    @PostMapping("/prestamos")
    public ResponseEntity<PrestamoResponseDTO> crearPrestamo(
            @Valid @RequestBody PrestamoRequestDTO request,
            @RequestHeader("Authorization") String authHeader) {

        // Extraer username del token
        // El token tiene formato: Bearer eyJhbGci...
        // El username está codificado en el payload del JWT
        String usuarioId = extraerUsuarioDelToken(authHeader);

        PrestamoResponseDTO prestamo = prestamoService.crearPrestamo(request, usuarioId);
        return ResponseEntity.status(HttpStatus.CREATED).body(prestamo);
    }

    // -----------------------------------------------------------------------
    // GET /api/lending/prestamos/activos
    // Lista los préstamos activos del usuario autenticado
    // -----------------------------------------------------------------------
    @GetMapping("/prestamos/activos")
    public ResponseEntity<List<PrestamoResponseDTO>> obtenerActivos(
            @RequestHeader("Authorization") String authHeader) {

        String usuarioId = extraerUsuarioDelToken(authHeader);
        return ResponseEntity.ok(prestamoService.obtenerPrestamosActivos(usuarioId));
    }

    // -----------------------------------------------------------------------
    // GET /api/lending/prestamos/historial
    // Lista todos los préstamos (activos y vencidos) del usuario
    // -----------------------------------------------------------------------
    @GetMapping("/prestamos/historial")
    public ResponseEntity<List<PrestamoResponseDTO>> obtenerHistorial(
            @RequestHeader("Authorization") String authHeader) {

        String usuarioId = extraerUsuarioDelToken(authHeader);
        return ResponseEntity.ok(prestamoService.obtenerHistorial(usuarioId));
    }

    // -----------------------------------------------------------------------
    // Método privado: extrae el username del token JWT
    // El token tiene 3 partes separadas por puntos: header.payload.signature
    // El payload está en Base64 y contiene el campo "sub" con el username
    // -----------------------------------------------------------------------
    private String extraerUsuarioDelToken(String authHeader) {
        try {
            // Quitar "Bearer "
            String token = authHeader.substring(7);

            // El payload es la segunda parte (índice 1) separada por "."
            String payload = token.split("\\.")[1];

            // Decodificar Base64
            String decodedPayload = new String(
                    java.util.Base64.getUrlDecoder().decode(payload));

            // Extraer el campo "sub" del JSON
            // Ejemplo de payload: {"roles":"ROLE_USER","type":"access","sub":"admin",...}
            String sub = decodedPayload.split("\"sub\":\"")[1].split("\"")[0];
            return sub;

        } catch (Exception e) {
            throw new RuntimeException("No se pudo extraer el usuario del token");
        }
    }
}