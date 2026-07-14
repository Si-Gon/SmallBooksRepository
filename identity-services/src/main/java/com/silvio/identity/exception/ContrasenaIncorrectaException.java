package com.silvio.identity.exception;

public class ContrasenaIncorrectaException extends RuntimeException {
    public ContrasenaIncorrectaException() {
        super("Contraseña actual incorrecta");
    }
}
