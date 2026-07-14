package com.silvio.elending.exception;

public class CopiaNoDisponibleException extends RuntimeException {
    public CopiaNoDisponibleException(Long libroId) {
        super("No hay copias disponibles del libro con id: " + libroId);
    }
}
