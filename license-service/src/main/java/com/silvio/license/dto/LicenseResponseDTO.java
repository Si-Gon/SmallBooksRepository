package com.silvio.license.dto;

import org.springframework.hateoas.RepresentationModel;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class LicenseResponseDTO extends RepresentationModel<LicenseResponseDTO>{

    private Long id;
    private Long libroId;
    private Integer totalCopias;
    private Integer copiasDisponibles;

    // Campo calculado — útil para que E-Lending sepa rápidamente
    // si puede crear un préstamo sin hacer lógica extra
    public boolean hayDisponibles() {
        return copiasDisponibles != null && copiasDisponibles > 0;
    }
}