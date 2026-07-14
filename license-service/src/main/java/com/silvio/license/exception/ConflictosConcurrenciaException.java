package com.silvio.license.exception;

public class ConflictosConcurrenciaException extends RuntimeException {
    public ConflictosConcurrenciaException() {
        super("El libro está siendo solicitado por muchos usuarios. Intenta de nuevo.");
    }
}
