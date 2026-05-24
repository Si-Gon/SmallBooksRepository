package com.silvio.elending.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PrestamoRequestDTO {

    // El usuarioId viene del token JWT en el service
    // El cliente solo necesita enviar el libroId
    @NotNull(message = "El ID del libro es obligatorio")
    private Long libroId;
}