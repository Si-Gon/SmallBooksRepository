package com.silvio.search.dto;

import lombok.Data;

// Resultado de búsqueda — incluye datos del libro y disponibilidad
@Data
public class SearchResultDTO {
    private Long id;
    private String titulo;
    private String autor;
    private String isbn;
    private String editorial;
    private Integer anioPublicacion;
    private String idioma;
    private String genero;
    private String sinopsis;
    private String portadaUrl;
    private Boolean disponible;
}