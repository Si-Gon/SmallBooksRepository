package com.silvio.subscription.exception;

public class TokenExtraccionException extends RuntimeException {
    public TokenExtraccionException() {
        super("No se pudo extraer el usuario del token");
    }
}
