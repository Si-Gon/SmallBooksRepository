package com.silvio.ingestion.controller;

import com.silvio.ingestion.dto.ArchivoLibroDTO;
import com.silvio.ingestion.service.IngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

// Endpoints:
//   POST   /api/ingestion/upload/{libroId}  → subir archivo PDF/EPUB
//   GET    /api/ingestion/{libroId}          → info del archivo
//   GET    /api/ingestion/{libroId}/bytes    → bytes del archivo (para Content Delivery)
//   DELETE /api/ingestion/{libroId}          → eliminar archivo

@RestController
@RequestMapping("/api/ingestion")
@RequiredArgsConstructor
public class IngestionController {

    private final IngestionService ingestionService;

    // Subir archivo — usa multipart/form-data en lugar de JSON
    // En Postman: Body → form-data → key: archivo (tipo File)
    @PostMapping(value = "/upload/{libroId}",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ArchivoLibroDTO> subirArchivo(
            @PathVariable Long libroId,
            @RequestParam("archivo") MultipartFile archivo) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ingestionService.subirArchivo(libroId, archivo));
    }

    @GetMapping("/{libroId}")
    public ResponseEntity<ArchivoLibroDTO> obtenerInfo(@PathVariable Long libroId) {
        return ResponseEntity.ok(ingestionService.obtenerInfo(libroId));
    }

    // Endpoint interno — usado por Content Delivery via Feign
    // Devuelve los bytes raw del archivo
    @GetMapping("/{libroId}/bytes")
    public ResponseEntity<byte[]> obtenerBytes(@PathVariable Long libroId) {
        byte[] bytes = ingestionService.obtenerBytes(libroId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }

    @DeleteMapping("/{libroId}")
    public ResponseEntity<Void> eliminar(@PathVariable Long libroId) {
        ingestionService.eliminar(libroId);
        return ResponseEntity.noContent().build();
    }
}