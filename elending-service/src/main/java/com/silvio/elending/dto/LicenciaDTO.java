package com.silvio.elending.dto;

import lombok.Data;

// Representa la respuesta que devuelve License Service
// Solo los campos que E-Lending necesita conocer
@Data
public class LicenciaDTO {

    private Long id;
    private Long libroId;
    private Integer totalCopias;
    private Integer copiasDisponibles;

    
}