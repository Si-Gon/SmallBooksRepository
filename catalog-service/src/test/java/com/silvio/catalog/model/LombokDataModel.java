package com.silvio.catalog.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Modelo de prueba para verificar que el procesamiento de anotaciones
 * de Lombok funciona correctamente en tiempo de compilacion.
 * Las anotaciones @Data, @Builder, @NoArgsConstructor y @AllArgsConstructor
 * deben generar los metodos getter, setter, equals, hashCode, toString
 * y el patron Builder en el bytecode compilado.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LombokDataModel {
    private Long id;
    private String nombre;
    private int cantidad;
}
