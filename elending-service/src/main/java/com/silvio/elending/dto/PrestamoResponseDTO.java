package com.silvio.elending.dto;

import com.silvio.elending.model.Prestamo.EstadoPrestamo;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PrestamoResponseDTO {

    private Long id;
    private String usuarioId;
    private Long libroId;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaVencimiento;
    private EstadoPrestamo estado;
}