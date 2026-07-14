package com.silvio.ingestion.exception;

public class ErrorLecturaArchivoException extends RuntimeException {
    public ErrorLecturaArchivoException(String mensaje) {
        super("Error al leer los bytes del archivo: " + mensaje);
    }
}
