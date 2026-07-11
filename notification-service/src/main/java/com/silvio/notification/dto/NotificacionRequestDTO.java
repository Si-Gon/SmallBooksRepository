package com.silvio.notification.dto;

import com.silvio.notification.model.Notificacion.TipoNotificacion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

// Usado por el endpoint REST POST /api/notifications y por NotificacionEventListener
// (RabbitMQ) para registrar notificaciones en la base de datos.
@Data
public class NotificacionRequestDTO {

    @NotBlank(message = "El usuarioId es obligatorio")
    @Size(max = 100, message = "El usuarioId no puede superar 100 caracteres")
    private String usuarioId;

    @NotNull(message = "El tipo de notificación es obligatorio")
    private TipoNotificacion tipo;

    @NotBlank(message = "El mensaje es obligatorio")
    @Size(min = 5, max = 500, message = "El mensaje debe tener entre 5 y 500 caracteres")
    private String mensaje;
}