package com.silvio.identity.exception;

public class TokenInvalidoException extends RuntimeException {
    public TokenInvalidoException() {
        super("Token de recuperación inválido");
    }

    public TokenInvalidoException(String message) {
        super(message);
    }
}
