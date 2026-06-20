package com.silvio.elending.dto;

import com.silvio.elending.model.Prestamo.EstadoPrestamo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

import org.springframework.hateoas.RepresentationModel;

@Data
@EqualsAndHashCode(callSuper = false)
public class PrestamoResponseDTO extends RepresentationModel<PrestamoResponseDTO>{

    private Long id;
    private String usuarioId;
    private Long libroId;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaVencimiento;
    private EstadoPrestamo estado;
}