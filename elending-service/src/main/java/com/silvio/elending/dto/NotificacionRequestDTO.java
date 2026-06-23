package com.silvio.elending.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

// DTO que E-Lending envía a Notification Service via Feign
// El tipo debe coincidir con TipoNotificacion del notification-service
@Data
public class NotificacionRequestDTO {

    @NotBlank(message = "El usuarioId es obligatorio")
    private String usuarioId;

    @NotBlank(message = "El tipo de notificación es obligatorio")
    @Pattern(
        regexp = "^(PRESTAMO_CREADO|PROXIMO_VENCER|VENCIDO)$",
        message = "El tipo debe ser: PRESTAMO_CREADO, PROXIMO_VENCER o VENCIDO"
    )
    private String tipo;    // "PRESTAMO_CREADO", "PROXIMO_VENCER", "VENCIDO"

    @NotBlank(message = "El mensaje es obligatorio")
    @Size(max = 500, message = "El mensaje no puede superar 500 caracteres")
    private String mensaje;

    // Constructor estático para crear notificaciones fácilmente
    public static NotificacionRequestDTO prestamoCreado(String usuarioId, Long libroId) {
        NotificacionRequestDTO dto = new NotificacionRequestDTO();
        dto.setUsuarioId(usuarioId);
        dto.setTipo("PRESTAMO_CREADO");
        dto.setMensaje("Tu préstamo del libro " + libroId +
                " ha sido creado exitosamente. Tienes 14 días para leerlo.");
        return dto;
    }

    public static NotificacionRequestDTO prestamoVencido(String usuarioId, Long libroId) {
        NotificacionRequestDTO dto = new NotificacionRequestDTO();
        dto.setUsuarioId(usuarioId);
        dto.setTipo("VENCIDO");
        dto.setMensaje("Tu préstamo del libro " + libroId +
                " ha vencido y ha sido cerrado automáticamente.");
        return dto;
    }

    public static NotificacionRequestDTO proximoVencer(String usuarioId, Long libroId) {
        NotificacionRequestDTO dto = new NotificacionRequestDTO();
        dto.setUsuarioId(usuarioId);
        dto.setTipo("PROXIMO_VENCER");
        dto.setMensaje("Tu préstamo del libro " + libroId +
                " vence en 2 días. Aprovecha el tiempo restante.");
        return dto;
    }
}