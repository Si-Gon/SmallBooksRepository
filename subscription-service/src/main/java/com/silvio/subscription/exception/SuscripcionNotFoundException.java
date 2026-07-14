package com.silvio.subscription.exception;

public class SuscripcionNotFoundException extends RuntimeException {
    public SuscripcionNotFoundException(String usuarioId) {
        super("No hay suscripción activa para el usuario: " + usuarioId);
    }
}
