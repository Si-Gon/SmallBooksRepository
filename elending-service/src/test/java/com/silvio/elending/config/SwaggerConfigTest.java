package com.silvio.elending.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests del SwaggerConfig del E-Lending Service.
 * Verifica que el bean OpenAPI se cree con la metadata correcta.
 */
class SwaggerConfigTest {

    private final SwaggerConfig swaggerConfig = new SwaggerConfig();

    @Test
    void customOpenAPI_debeCrearBeanConMetadataCorrecta() {
        // When
        OpenAPI openAPI = swaggerConfig.customOpenAPI();

        // Then
        assertNotNull(openAPI);
        assertNotNull(openAPI.getInfo());
        assertEquals("SmallBooks - E-Lending Service", openAPI.getInfo().getTitle());
        assertEquals("1.0.0", openAPI.getInfo().getVersion());
        assertTrue(openAPI.getInfo().getDescription().contains("BASICO"));
        assertTrue(openAPI.getInfo().getDescription().contains("PREMIUM"));
    }

    @Test
    void customOpenAPI_debeCrearNuevaInstanciaEnCadaLlamada() {
        // When
        OpenAPI primera = swaggerConfig.customOpenAPI();
        OpenAPI segunda = swaggerConfig.customOpenAPI();

        // Then
        assertNotNull(primera);
        assertNotNull(segunda);
        assertNotSame(primera, segunda);
    }
}