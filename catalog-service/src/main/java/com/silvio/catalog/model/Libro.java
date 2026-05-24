package com.silvio.catalog.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Entity
@Table(name = "libros")
public class Libro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El título es obligatorio")
    @Size(max = 200, message = "El título no puede superar 200 caracteres")
    @Column(nullable = false, length = 200)
    private String titulo;

    @NotBlank(message = "El autor es obligatorio")
    @Size(max = 150, message = "El autor no puede superar 150 caracteres")
    @Column(nullable = false, length = 150)
    private String autor;

    @NotBlank(message = "El ISBN es obligatorio")
    @Column(unique = true, nullable = false, length = 20)
    private String isbn;

    @Column(length = 150)
    private String editorial;

    @Column(name = "anio_publicacion")
    private Integer anioPublicacion;

    @Column(length = 50)
    private String idioma;

    @Column(length = 100)
    private String genero;

    @Column(columnDefinition = "TEXT")
    private String sinopsis;

    @Column(name = "portada_url", length = 500)
    private String portadaUrl;

    // true = disponible para préstamo
    // false = actualmente prestado
    // El E-Lending Service cambia este valor via Feign cuando crea/cierra un préstamo
    @NotNull
    @Column(nullable = false)
    private Boolean disponible = true;
}