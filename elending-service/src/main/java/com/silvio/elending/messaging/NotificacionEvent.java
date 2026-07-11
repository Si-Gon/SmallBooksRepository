package com.silvio.elending.messaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Evento publicado a RabbitMQ por E-Lending Service cuando ocurre
// una operación de préstamo que requiere notificar al usuario.
// Reemplaza la llamada Feign síncrona a Notification Service.
// El tipo debe coincidir con TipoNotificacion del notification-service.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificacionEvent {

    private String usuarioId;
    private String tipo;       // "PRESTAMO_CREADO", "PROXIMO_VENCER", "VENCIDO"
    private String mensaje;

    // ─── factory methods ──────────────────────────────────────────────────────

    // diasPrestamo debe venir del plan del usuario (7 BASICO, 14 PREMIUM).
    // Si es <= 0, aplica 7 días por defecto para evitar mensajes inválidos.
    public static NotificacionEvent prestamoCreado(String usuarioId, Long libroId, int diasPrestamo) {
        if (diasPrestamo <= 0) {
            diasPrestamo = 7;
        }
        return new NotificacionEvent(
            usuarioId,
            "PRESTAMO_CREADO",
            "Tu préstamo del libro " + libroId +
                " ha sido creado exitosamente. Tienes " + diasPrestamo + " días para leerlo."
        );
    }

    public static NotificacionEvent prestamoVencido(String usuarioId, Long libroId) {
        return new NotificacionEvent(
            usuarioId,
            "VENCIDO",
            "Tu préstamo del libro " + libroId +
                " ha vencido y ha sido cerrado automáticamente."
        );
    }

    public static NotificacionEvent proximoVencer(String usuarioId, Long libroId) {
        return new NotificacionEvent(
            usuarioId,
            "PROXIMO_VENCER",
            "Tu préstamo del libro " + libroId +
                " vence en 2 días. Aprovecha el tiempo restante."
        );
    }
}
