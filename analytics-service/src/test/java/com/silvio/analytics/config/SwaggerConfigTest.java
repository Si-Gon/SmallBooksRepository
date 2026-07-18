package com.silvio.analytics.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para SwaggerConfig.
 * <p>
 * Verifica que el bean OpenAPI contenga la configuración programática
 * de seguridad JWT Bearer, sin usar anotaciones @SecurityScheme
 * que causan ArrayIndexOutOfBoundsException en Spring Boot 3.3.11.
 */
@SpringBootTest(classes = SwaggerConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class SwaggerConfigTest {

    @Autowired
    private OpenAPI openAPI;

    @Test
    void customOpenAPI_DebeContenerSecuritySchemeBearerAuth() {
        assertNotNull(openAPI, "El bean OpenAPI no debe ser null");

        var components = openAPI.getComponents();
        assertNotNull(components, "Components no debe ser null");
        assertTrue(components.getSecuritySchemes().containsKey("BearerAuth"),
                "Debe existir un SecurityScheme llamado BearerAuth");

        var securityScheme = components.getSecuritySchemes().get("BearerAuth");
        assertEquals(SecurityScheme.Type.HTTP, securityScheme.getType(),
                "El tipo del SecurityScheme debe ser HTTP");
        assertEquals("bearer", securityScheme.getScheme(),
                "El scheme del SecurityScheme debe ser bearer");
        assertEquals("JWT", securityScheme.getBearerFormat(),
                "El bearerFormat del SecurityScheme debe ser JWT");

        assertNotNull(openAPI.getSecurity(), "La lista de SecurityRequirement no debe ser null");
        assertFalse(openAPI.getSecurity().isEmpty(), "Debe haber al menos un SecurityRequirement");

        var securityRequirement = openAPI.getSecurity().get(0);
        assertTrue(securityRequirement.containsKey("BearerAuth"),
                "El SecurityRequirement debe contener BearerAuth");
    }

    @Test
    void customOpenAPI_DebeContenerInfoCorrecto() {
        assertNotNull(openAPI.getInfo(), "Info no debe ser null");
        assertEquals("SmallBooks - Analytics Service", openAPI.getInfo().getTitle());
        assertEquals("1.0.0", openAPI.getInfo().getVersion());
        assertEquals("Métricas y estadísticas de uso de la plataforma",
                openAPI.getInfo().getDescription());
    }
}
