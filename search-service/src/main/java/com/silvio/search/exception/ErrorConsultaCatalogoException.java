package com.silvio.search.exception;

public class ErrorConsultaCatalogoException extends RuntimeException {
    public ErrorConsultaCatalogoException(String operacion, String mensaje) {
        super("Error al " + operacion + ": " + mensaje);
    }
}
