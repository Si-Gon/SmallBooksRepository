package com.silvio.elending.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "prestamos")
public class Prestamo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ID del usuario extraído del token JWT
    // No se consulta a otro MS — viene directo del token
    @Column(name = "usuario_id", nullable = false, length = 100)
    private String usuarioId;

    // Referencia al libro en catalog-service
    @Column(name = "libro_id", nullable = false)
    private Long libroId;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;

    // fechaInicio + 14 días — calculado al crear, nunca cambia
    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDateTime fechaVencimiento;

    // ACTIVO → préstamo vigente
    // VENCIDO → plazo expirado, cerrado por el scheduler
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPrestamo estado;

    public enum EstadoPrestamo {
        ACTIVO,
        VENCIDO
    }
}