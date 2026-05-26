package com.silvio.content.dto;

import lombok.Data;

@Data
public class PrestamoDTO {
    private Long id;
    private String usuarioId;
    private Long libroId;
    private String estado; // "ACTIVO" o "VENCIDO"
}