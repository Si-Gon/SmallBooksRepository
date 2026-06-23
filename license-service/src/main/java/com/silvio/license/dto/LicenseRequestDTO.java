package com.silvio.license.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class LicenseRequestDTO {

    @NotNull(message = "El ID del libro es obligatorio")
    @Positive(message = "El ID del libro debe ser un número positivo")
    private Long libroId;

    @NotNull(message = "El total de copias es obligatorio")
    @Min(value = 1, message = "Debe haber al menos 1 copia")
    @Max(value = 10000, message = "No se pueden registrar más de 10.000 copias en una sola licencia")
    private Integer totalCopias;
}