package com.silvio.content.controller;

import com.silvio.content.service.ContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Content Delivery", description = "Entrega de contenido digital — descarga archivos PDF/EPUB solo si el usuario tiene préstamo activo")
@RestController
@RequestMapping("/api/content")
@RequiredArgsConstructor
public class ContentController {

    private final ContentService contentService;

    @Operation(summary = "Descargar archivo de libro",
               description = "Descarga el archivo PDF o EPUB del libro indicado. " +
                             "Requiere que el usuario autenticado tenga un préstamo activo para ese libro. " +
                             "El usuario se identifica desde el token JWT")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Archivo descargado exitosamente"),
        @ApiResponse(responseCode = "400", description = "ID del libro inválido"),
        @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
        @ApiResponse(responseCode = "403", description = "El usuario no tiene préstamo activo para este libro"),
        @ApiResponse(responseCode = "404", description = "Archivo no encontrado para el libro indicado"),
        @ApiResponse(responseCode = "500", description = "Error al obtener el archivo o verificar el préstamo")
    })
    @GetMapping("/{libroId}")
    public ResponseEntity<byte[]> descargarArchivo(
            @Parameter(description = "ID del libro a descargar", required = true)
            @PathVariable Long libroId,
            @Parameter(description = "Token JWT en formato: Bearer {token}", required = true)
            @RequestHeader("Authorization") String authHeader) {

        byte[] bytes = contentService.obtenerArchivo(libroId, authHeader);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"libro_" + libroId + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(bytes.length)
                .body(bytes);
    }
}