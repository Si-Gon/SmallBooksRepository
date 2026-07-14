package com.silvio.ingestion.exception;

public class ArchivoNoEncontradoException extends RuntimeException {
    public ArchivoNoEncontradoException(Long libroId) {
        super("No hay archivo subido para el libro con id: " + libroId);
    }
}
