package com.silvio.ingestion.controller;

import com.silvio.ingestion.dto.ArchivoLibroDTO;
import com.silvio.ingestion.service.IngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Ingestion", description = "Carga y gestión de archivos PDF/EPUB — almacenamiento en MySQL como BLOB")
@RestController
@RequestMapping("/api/ingestion")
@RequiredArgsConstructor
public class IngestionController {

    private final IngestionService ingestionService;

    @Operation(summary = "Subir archivo de libro",
               description = "Sube un archivo PDF o EPUB asociado a un libro del catálogo. " +
                             "Usar form-data en Postman: key 'archivo' de tipo File. " +
                             "El archivo se almacena como BLOB en MySQL")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Archivo subido exitosamente"),
        @ApiResponse(responseCode = "400", description = "Formato no permitido — solo PDF y EPUB"),
        @ApiResponse(responseCode = "404", description = "Libro no encontrado en el catálogo")
    })
    @PostMapping(value = "/upload/{libroId}",
                 consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ArchivoLibroDTO> subirArchivo(
            @Parameter(description = "ID del libro al que pertenece el archivo", required = true)
            @PathVariable Long libroId,
            @Parameter(description = "Archivo PDF o EPUB a subir", required = true)
            @RequestParam("archivo") MultipartFile archivo) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ingestionService.subirArchivo(libroId, archivo));
    }

    @Operation(summary = "Obtener información del archivo",
               description = "Devuelve la metadata del archivo asociado a un libro: nombre, formato, tamaño y fecha de subida")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Información obtenida exitosamente"),
        @ApiResponse(responseCode = "404", description = "No hay archivo subido para este libro")
    })
    @GetMapping("/{libroId}")
    public ResponseEntity<ArchivoLibroDTO> obtenerInfo(
            @Parameter(description = "ID del libro", required = true)
            @PathVariable Long libroId) {
        return ResponseEntity.ok(ingestionService.obtenerInfo(libroId));
    }

    @Operation(summary = "Obtener bytes del archivo",
               description = "Endpoint interno usado por Content Delivery Service via Feign. " +
                             "Devuelve los bytes raw del archivo para entrega al usuario final")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Bytes del archivo obtenidos exitosamente"),
        @ApiResponse(responseCode = "404", description = "Archivo no encontrado")
    })
    @GetMapping("/{libroId}/bytes")
    public ResponseEntity<byte[]> obtenerBytes(
            @Parameter(description = "ID del libro", required = true)
            @PathVariable Long libroId) {
        byte[] bytes = ingestionService.obtenerBytes(libroId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }

    @Operation(summary = "Eliminar archivo",
               description = "Elimina el archivo asociado a un libro tanto de la base de datos como del registro")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Archivo eliminado exitosamente"),
        @ApiResponse(responseCode = "404", description = "No hay archivo para este libro")
    })
    @DeleteMapping("/{libroId}")
    public ResponseEntity<Void> eliminar(
            @Parameter(description = "ID del libro", required = true)
            @PathVariable Long libroId) {
        ingestionService.eliminar(libroId);
        return ResponseEntity.noContent().build();
    }
}