package com.silvio.elending.exception;

public class TokenExtraccionException extends RuntimeException {
    public TokenExtraccionException() {
        super("No se pudo extraer el usuario del token");
    }
}
