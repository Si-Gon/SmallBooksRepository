package com.silvio.notification.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "notificaciones")
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, length = 100)
    private String usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoNotificacion tipo;

    @Column(nullable = false, length = 500)
    private String mensaje;

    @Column(name = "fecha_envio", nullable = false)
    private LocalDateTime fechaEnvio;

    @Column(nullable = false)
    private Boolean leida = false;

    // Clave de idempotencia: SHA-256(usuarioId + "|" + tipo + "|" + mensaje).
    // Evita duplicados cuando RabbitMQ reintenta entregar el mismo mensaje.
    // El UNIQUE INDEX se define en la migración Flyway V2.
    @Column(name = "idempotency_key", nullable = false, length = 64, unique = true)
    private String idempotencyKey;

    public enum TipoNotificacion {
        PRESTAMO_CREADO,
        PROXIMO_VENCER,   // scheduler avisa 2 días antes del vencimiento
        VENCIDO
    }
}