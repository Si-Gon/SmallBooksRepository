package com.silvio.elending.exception;

public class VerificacionDisponibilidadException extends RuntimeException {
    public VerificacionDisponibilidadException(Long libroId) {
        super("No se pudo verificar disponibilidad del libro con id: " + libroId);
    }
}
