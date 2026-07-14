package com.silvio.license.exception;

public class DevolucionInvalidaException extends RuntimeException {
    public DevolucionInvalidaException() {
        super("Todas las copias del libro ya están disponibles");
    }
}
