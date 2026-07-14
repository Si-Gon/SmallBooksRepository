package com.silvio.license.exception;

public class ErrorDevolucionException extends RuntimeException {
    public ErrorDevolucionException() {
        super("Error al devolver copia. Intenta de nuevo.");
    }
}
