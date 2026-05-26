package com.silvio.ingestion.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ArchivoLibroDTO {
    private Long id;
    private Long libroId;
    private String nombreArchivo;
    private String formato;
    private Long tamanio;
    private LocalDateTime fechaSubida;
    // No incluimos rutaOClave — es interna del servidor, no debe exponerse
}