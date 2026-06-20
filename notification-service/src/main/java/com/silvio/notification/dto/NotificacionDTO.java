package com.silvio.notification.dto;

import com.silvio.notification.model.Notificacion.TipoNotificacion;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

import org.springframework.hateoas.RepresentationModel;

@Data
@EqualsAndHashCode(callSuper = false)
public class NotificacionDTO extends RepresentationModel<NotificacionDTO> {
    private Long id;
    private String usuarioId;
    private TipoNotificacion tipo;
    private String mensaje;
    private LocalDateTime fechaEnvio;
    private Boolean leida;
}