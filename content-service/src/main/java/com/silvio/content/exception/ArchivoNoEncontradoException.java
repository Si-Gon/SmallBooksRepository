package com.silvio.content.exception;

public class ArchivoNoEncontradoException extends RuntimeException {
    public ArchivoNoEncontradoException(Long libroId) {
        super("No se pudo obtener el archivo del libro con id: " + libroId);
    }
}
