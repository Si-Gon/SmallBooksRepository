package com.silvio.ingestion.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "archivos_libros")
public class ArchivoLibro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Referencia al libro en catalog-service
    @Column(name = "libro_id", unique = true, nullable = false)
    private Long libroId;

    @Column(name = "nombre_archivo", nullable = false)
    private String nombreArchivo;

    // PDF o EPUB
    @Column(nullable = false, length = 10)
    private String formato;

    // Tamaño en bytes
    @Column(nullable = false)
    private Long tamanio;

    // En Opción A: ruta completa en disco
    // En Opción B (futura): id del BLOB en MySQL
    @Column(name = "ruta_o_clave", nullable = false)
    private String rutaOClave;

    @Column(name = "fecha_subida", nullable = false)
    private LocalDateTime fechaSubida;
}