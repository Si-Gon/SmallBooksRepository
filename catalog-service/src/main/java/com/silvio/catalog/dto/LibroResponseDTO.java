package com.silvio.catalog.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.hateoas.RepresentationModel;

@Data
@EqualsAndHashCode(callSuper = false)
public class LibroResponseDTO extends RepresentationModel<LibroResponseDTO> {

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