package com.silvio.ingestion.repository;

import java.time.LocalDateTime;

/**
 * Proyección cerrada de ArchivoLibro que excluye el campo LONGBLOB (datos).
 * Utilizada en consultas de metadatos para evitar tráfico innecesario.
 */
public interface ArchivoLibroInfo {

    Long getId();

    Long getLibroId();

    String getNombreArchivo();

    String getFormato();

    Long getTamanio();

    String getRutaOClave();

    LocalDateTime getFechaSubida();
}
