package com.silvio.subscription.dto;

import com.silvio.subscription.model.Suscripcion.PlanSuscripcion;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SuscripcionRequestDTO {

    // usuarioId viene del token JWT en el controller
    @NotNull(message = "El plan de suscripción es obligatorio")
    private PlanSuscripcion plan;

    // Duración en meses — por defecto 1 mes
    @NotNull(message = "La duración en meses es obligatoria")
    @Min(value = 1, message = "La suscripción debe ser de al menos 1 mes")
    @Max(value = 12, message = "La suscripción no puede superar los 12 meses")
    private Integer meses = 1;
}