package com.silvio.license.exception;

public class LicenciaDuplicadaException extends RuntimeException {
    public LicenciaDuplicadaException(Long libroId) {
        super("Ya existe una licencia para el libro con id: " + libroId);
    }
}
