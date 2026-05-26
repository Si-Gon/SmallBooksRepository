package com.silvio.content.controller;

import com.silvio.content.service.ContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Endpoint:
//   GET /api/content/{libroId}  → descarga el archivo si tiene préstamo activo

@RestController
@RequestMapping("/api/content")
@RequiredArgsConstructor
public class ContentController {

    private final ContentService contentService;

    @GetMapping("/{libroId}")
    public ResponseEntity<byte[]> descargarArchivo(
            @PathVariable Long libroId,
            @RequestHeader("Authorization") String authHeader) {

        byte[] bytes = contentService.obtenerArchivo(libroId, authHeader);

        // Devolver el archivo como descarga
        // Content-Disposition: attachment → el navegador lo descarga
        // application/octet-stream → tipo genérico para cualquier archivo binario
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"libro_" + libroId + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(bytes.length)
                .body(bytes);
    }
}