package com.silvio.search.dto;

import org.springframework.hateoas.RepresentationModel;

import lombok.Data;
import lombok.EqualsAndHashCode;

// Resultado de búsqueda — incluye datos del libro y disponibilidad
@Data
@EqualsAndHashCode(callSuper = false)
public class SearchResultDTO extends RepresentationModel<SearchResultDTO> {
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