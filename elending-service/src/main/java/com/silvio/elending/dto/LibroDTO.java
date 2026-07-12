package com.silvio.elending.dto;

import lombok.Data;

// Representa la respuesta de Catalog Service
// Información básica del libro para mostrar en préstamos
@Data
public class LibroDTO {

    private Long id;
    private String titulo;
    private String autor;
    private String isbn;
    private String genero;
    private Boolean disponible;
}
