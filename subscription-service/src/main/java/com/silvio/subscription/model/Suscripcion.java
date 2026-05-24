package com.silvio.subscription.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "suscripciones")
public class Suscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", unique = true, nullable = false, length = 100)
    private String usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanSuscripcion plan;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDateTime fechaFin;

    @Column(nullable = false)
    private Boolean activa = true;

    public enum PlanSuscripcion {
        BASICO,   // 2 préstamos, 7 días
        PREMIUM   // 5 préstamos, 14 días
    }
}