package com.silvio.elending.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

// Respuesta mínima que E-Lending necesita de Notification Service
// Solo para confirmar que la notificación fue creada
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NotificacionResponseDTO {
    private Long id;
    private String usuarioId;
    private String tipo;
    private String mensaje;
}