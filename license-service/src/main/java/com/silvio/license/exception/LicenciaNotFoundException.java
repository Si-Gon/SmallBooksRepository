package com.silvio.license.exception;

public class LicenciaNotFoundException extends RuntimeException {
    public LicenciaNotFoundException(Long libroId) {
        super("No existe licencia para el libro con id: " + libroId);
    }
}
