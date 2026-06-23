package com.silvio.elending.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class PrestamoRequestDTO {

    // El usuarioId viene del token JWT en el service
    // El cliente solo necesita enviar el libroId
    @NotNull(message = "El ID del libro es obligatorio")
    @Positive(message = "El ID del libro debe ser un número positivo")
    private Long libroId;
}