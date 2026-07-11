package com.silvio.catalog.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para SwaggerConfig.
 *
 * Verifica que el bean OpenAPI se cree correctamente con la metadata
 * del servicio (título, versión, descripción).
 */
class SwaggerConfigTest {

    private final SwaggerConfig swaggerConfig = new SwaggerConfig();

    @Test
    void customOpenAPI_DebeCrearBeanConMetadataCorrecta() {
        // When
        OpenAPI openAPI = swaggerConfig.customOpenAPI();

        // Then
        assertNotNull(openAPI, "El bean OpenAPI no debe ser null");

        Info info = openAPI.getInfo();
        assertNotNull(info, "La metadata Info no debe ser null");
        assertEquals("SmallBooks - Catalog Service", info.getTitle());
        assertEquals("1.0.0", info.getVersion());
        assertEquals("Gestión del catálogo de libros disponibles en la plataforma", info.getDescription());
    }

    @Test
    void customOpenAPI_DebeCrearNuevaInstanciaEnCadaLlamada() {
        // When: llamamos dos veces al método del bean
        OpenAPI primeraLlamada = swaggerConfig.customOpenAPI();
        OpenAPI segundaLlamada = swaggerConfig.customOpenAPI();

        // Then: deben ser instancias diferentes (Spring maneja el singleton)
        assertNotSame(primeraLlamada, segundaLlamada,
                "El método @Bean puede crear una nueva instancia; Spring asegura el singleton");
    }
}
