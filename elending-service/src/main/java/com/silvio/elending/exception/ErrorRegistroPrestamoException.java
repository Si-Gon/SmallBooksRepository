package com.silvio.elending.exception;

public class ErrorRegistroPrestamoException extends RuntimeException {
    public ErrorRegistroPrestamoException() {
        super("Error al registrar el préstamo en License Service");
    }
}
