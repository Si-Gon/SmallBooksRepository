package com.silvio.elending.exception;

public class PrestamoDuplicadoException extends RuntimeException {
    public PrestamoDuplicadoException() {
        super("Ya tienes este libro en préstamo activo");
    }
}
