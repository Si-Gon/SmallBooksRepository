package com.silvio.catalog.dto;

import lombok.Data;

@Data
public class LibroResponseDTO {

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