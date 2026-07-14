package com.silvio.notification.exception;

public class HashNoDisponibleException extends RuntimeException {
    public HashNoDisponibleException(Throwable causa) {
        super("SHA-256 no disponible", causa);
    }
}
