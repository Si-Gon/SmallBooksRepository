package com.silvio.notification.messaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Evento recibido desde RabbitMQ, publicado por E-Lending Service.
// Debe coincidir con la estructura del NotificacionEvent de elending-service
// para que Jackson deserialice correctamente el mensaje JSON.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificacionEvent {

    private String usuarioId;
    private String tipo;       // "PRESTAMO_CREADO", "PROXIMO_VENCER", "VENCIDO"
    private String mensaje;
}
