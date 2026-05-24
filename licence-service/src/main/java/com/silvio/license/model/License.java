package com.silvio.license.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Entity
@Table(name = "licencias")
public class License {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "El ID del libro es obligatorio")
    @Column(name = "libro_id", unique = true, nullable = false)
    private Long libroId;

    @NotNull(message = "El total de copias es obligatorio")
    @Min(value = 1, message = "Debe haber al menos 1 copia")
    @Column(name = "total_copias", nullable = false)
    private Integer totalCopias;

    @NotNull
    @Column(name = "copias_disponibles", nullable = false)
    private Integer copiasDisponibles;
}