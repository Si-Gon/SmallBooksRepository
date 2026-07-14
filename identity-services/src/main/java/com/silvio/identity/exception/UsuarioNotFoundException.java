package com.silvio.identity.exception;

public class UsuarioNotFoundException extends RuntimeException {
    public UsuarioNotFoundException(String username) {
        super("Usuario no encontrado: " + username);
    }
}
