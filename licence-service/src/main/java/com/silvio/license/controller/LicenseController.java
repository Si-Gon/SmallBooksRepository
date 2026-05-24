package com.silvio.license.controller;

import com.silvio.license.dto.LicenseRequestDTO;
import com.silvio.license.dto.LicenseResponseDTO;
import com.silvio.license.service.LicenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Endpoints REST semánticos:
//   GET    /api/licenses              → listar todas las licencias
//   GET    /api/licenses/{libroId}    → consultar licencia de un libro
//   POST   /api/licenses              → registrar nueva licencia
//   PUT    /api/licenses/{libroId}    → actualizar total de copias
//   PATCH  /api/licenses/{libroId}/prestar   → descontar 1 copia (E-Lending)
//   PATCH  /api/licenses/{libroId}/devolver  → sumar 1 copia (E-Lending)

@RestController
@RequestMapping("/api/licenses")
@RequiredArgsConstructor
public class LicenseController {

    private final LicenseService licenseService;

    @GetMapping
    public ResponseEntity<List<LicenseResponseDTO>> obtenerTodas() {
        return ResponseEntity.ok(licenseService.obtenerTodas());
    }

    // Este endpoint es el que consume E-Lending via Feign
    // antes de crear un préstamo
    @GetMapping("/{libroId}")
    public ResponseEntity<LicenseResponseDTO> obtenerPorLibroId(@PathVariable Long libroId) {
        return ResponseEntity.ok(licenseService.obtenerPorLibroId(libroId));
    }

    @PostMapping
    public ResponseEntity<LicenseResponseDTO> crear(
            @Valid @RequestBody LicenseRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(licenseService.crear(request));
    }

    @PutMapping("/{libroId}")
    public ResponseEntity<LicenseResponseDTO> actualizar(
            @PathVariable Long libroId,
            @Valid @RequestBody LicenseRequestDTO request) {
        return ResponseEntity.ok(licenseService.actualizar(libroId, request));
    }

    // Llamado por E-Lending cuando se crea un préstamo
    @PutMapping("/{libroId}/prestar")
    public ResponseEntity<LicenseResponseDTO> prestar(@PathVariable Long libroId) {
    return ResponseEntity.ok(licenseService.prestar(libroId));
    }

    // Llamado por E-Lending cuando vence o se cierra un préstamo
    @PutMapping("/{libroId}/devolver")
    public ResponseEntity<LicenseResponseDTO> devolver(@PathVariable Long libroId) {
    return ResponseEntity.ok(licenseService.devolver(libroId));
}
}