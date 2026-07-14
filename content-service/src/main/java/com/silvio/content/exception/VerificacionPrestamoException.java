package com.silvio.content.exception;

public class VerificacionPrestamoException extends RuntimeException {
    public VerificacionPrestamoException(String mensaje) {
        super("No se pudo verificar el préstamo: " + mensaje);
    }
}
