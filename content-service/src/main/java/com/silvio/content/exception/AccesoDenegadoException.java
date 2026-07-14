package com.silvio.content.exception;

public class AccesoDenegadoException extends RuntimeException {
    public AccesoDenegadoException(Long libroId) {
        super("Acceso denegado — no tienes un préstamo activo del libro con id: " + libroId);
    }
}
