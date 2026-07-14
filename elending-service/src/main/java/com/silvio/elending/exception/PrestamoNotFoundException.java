package com.silvio.elending.exception;

public class PrestamoNotFoundException extends RuntimeException {
    public PrestamoNotFoundException(Long prestamoId) {
        super("Préstamo no encontrado con id: " + prestamoId);
    }
}
