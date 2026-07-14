package com.silvio.identity.exception;

public class TokenExpiradoException extends RuntimeException {
    public TokenExpiradoException() {
        super("El token de recuperación ha expirado");
    }
}
