package com.silvio.analytics.exception;

public class ErrorHistorialUsuarioException extends RuntimeException {
    public ErrorHistorialUsuarioException(String usuarioId) {
        super("Error al obtener historial del usuario: " + usuarioId);
    }
}
