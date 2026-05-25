package com.silvio.search.dto;

import lombok.Data;

// Representa la respuesta del Catalog Service
// Search Service usa esto para enriquecer los resultados de búsqueda
@Data
public class LibroCatalogDTO {
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