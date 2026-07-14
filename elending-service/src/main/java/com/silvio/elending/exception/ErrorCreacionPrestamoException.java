package com.silvio.elending.exception;

public class ErrorCreacionPrestamoException extends RuntimeException {
    public ErrorCreacionPrestamoException() {
        super("Error al crear el préstamo. La operación fue revertida.");
    }
}
