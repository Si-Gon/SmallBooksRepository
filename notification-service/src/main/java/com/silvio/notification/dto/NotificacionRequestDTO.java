package com.silvio.notification.dto;

import com.silvio.notification.model.Notificacion.TipoNotificacion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

// Usado por E-Lending Service via Feign para registrar notificaciones
@Data
public class NotificacionRequestDTO {

    @NotBlank(message = "El usuarioId es obligatorio")
    private String usuarioId;

    @NotNull(message = "El tipo es obligatorio")
    private TipoNotificacion tipo;

    @NotBlank(message = "El mensaje es obligatorio")
    private String mensaje;
}