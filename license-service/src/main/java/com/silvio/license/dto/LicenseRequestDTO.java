package com.silvio.license.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LicenseRequestDTO {

    @NotNull(message = "El ID del libro es obligatorio")
    private Long libroId;

    @NotNull(message = "El total de copias es obligatorio")
    @Min(value = 1, message = "Debe haber al menos 1 copia")
    private Integer totalCopias;
}