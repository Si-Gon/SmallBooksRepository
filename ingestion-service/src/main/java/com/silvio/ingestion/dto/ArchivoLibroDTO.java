package com.silvio.ingestion.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

import org.springframework.hateoas.RepresentationModel;

@Data
@EqualsAndHashCode(callSuper = false)
public class ArchivoLibroDTO extends RepresentationModel<ArchivoLibroDTO> {
    private Long id;
    private Long libroId;
    private String nombreArchivo;
    private String formato;
    private Long tamanio;
    private LocalDateTime fechaSubida;
    // No incluimos rutaOClave — es interna del servidor, no debe exponerse
}