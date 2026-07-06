package com.silvio.catalog.dto;


import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LibroRequestDTO {

    @NotBlank(message = "El título es obligatorio")
    @Size(min = 1, max = 200, message = "El título debe tener entre 1 y 200 caracteres")
    private String titulo;

    @NotBlank(message = "El autor es obligatorio")
    @Size(min = 2, max = 150, message = "El autor debe tener entre 2 y 150 caracteres")
    private String autor;

    @NotBlank(message = "El ISBN es obligatorio")
    @Pattern(
        regexp = "^(?:\\d{9}[\\dX]|\\d{13})$",
        message = "El ISBN debe tener formato ISBN-10 (10 dígitos) o ISBN-13 (13 dígitos)"
    )
    private String isbn;

    @Size(max = 150, message = "La editorial no puede superar 150 caracteres")
    private String editorial;

    @Min(value = 1450, message = "El año de publicación no puede ser anterior a 1450 (invención de la imprenta)")
    @Max(value = 2100, message = "El año de publicación no es válido")
    private Integer anioPublicacion;

    @Size(max = 50, message = "El idioma no puede superar 50 caracteres")
    private String idioma;

    @Size(max = 100, message = "El género no puede superar 100 caracteres")
    private String genero;

    @Size(max = 2000, message = "La sinopsis no puede superar 2000 caracteres")
    private String sinopsis;

    @Size(max = 500, message = "La URL de portada no puede superar 500 caracteres")
    @Pattern(
        regexp = "^(https?://.+)?$",
        message = "La URL de portada debe comenzar con http:// o https://"
    )
    private String portadaUrl;
}