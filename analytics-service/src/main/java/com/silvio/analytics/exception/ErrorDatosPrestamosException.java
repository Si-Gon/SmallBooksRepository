package com.silvio.analytics.exception;

public class ErrorDatosPrestamosException extends RuntimeException {
    public ErrorDatosPrestamosException(String mensaje) {
        super("Error al obtener datos de préstamos: " + mensaje);
    }
}
