package com.silvio.elending.dto;

import lombok.Data;

// Respuesta mínima que E-Lending necesita de Notification Service
// Solo para confirmar que la notificación fue creada
@Data
public class NotificacionResponseDTO {
    private Long id;
    private String usuarioId;
    private String tipo;
    private String mensaje;
}