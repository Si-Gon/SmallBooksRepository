package com.silvio.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LibroRequestDTO {

    @NotBlank(message = "El título es obligatorio")
    @Size(max = 200, message = "El título no puede superar 200 caracteres")
    private String titulo;

    @NotBlank(message = "El autor es obligatorio")
    @Size(max = 150, message = "El autor no puede superar 150 caracteres")
    private String autor;

    @NotBlank(message = "El ISBN es obligatorio")
    @Size(max = 20, message = "El ISBN no puede superar 20 caracteres")
    private String isbn;

    private String editorial;
    private Integer anioPublicacion;
    private String idioma;
    private String genero;
    private String sinopsis;
    private String portadaUrl;
}