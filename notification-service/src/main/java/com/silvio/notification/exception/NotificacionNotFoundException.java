package com.silvio.notification.exception;

public class NotificacionNotFoundException extends RuntimeException {
    public NotificacionNotFoundException(Long id) {
        super("Notificación no encontrada con id: " + id);
    }
}
