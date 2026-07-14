package com.silvio.elending.exception;

public class UltimaCopiaNoDisponibleException extends RuntimeException {
    public UltimaCopiaNoDisponibleException() {
        super("La última copia del libro fue tomada por otro usuario. Intenta de nuevo.");
    }
}
