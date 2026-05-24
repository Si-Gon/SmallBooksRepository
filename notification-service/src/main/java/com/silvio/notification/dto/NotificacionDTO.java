package com.silvio.notification.dto;

import com.silvio.notification.model.Notificacion.TipoNotificacion;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificacionDTO {
    private Long id;
    private String usuarioId;
    private TipoNotificacion tipo;
    private String mensaje;
    private LocalDateTime fechaEnvio;
    private Boolean leida;
}