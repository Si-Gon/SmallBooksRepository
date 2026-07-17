package com.silvio.license.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica que SwaggerConfig tenga la anotación @SecurityScheme
 * con configuración JWT Bearer para autenticación.
 * <p>
 * Es un test de regresión estático: si alguien elimina la anotación
 * en el futuro, este test fallará en CI.
 */
class SwaggerConfigTest {

    private static final Path SWAGGER_CONFIG_SOURCE = Paths.get(
            "src/main/java/com/silvio/license/config/SwaggerConfig.java");

    private String leerFuente() {
        try {
            return Files.readString(SWAGGER_CONFIG_SOURCE);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo leer " + SWAGGER_CONFIG_SOURCE, e);
        }
    }

    @Test
    void swaggerConfig_DebeTenerAnnotationSecurityScheme() {
        String source = leerFuente();
        assertTrue(source.contains("@SecurityScheme"),
                "SwaggerConfig.java debe tener la anotación @SecurityScheme");
        assertTrue(source.contains("name = \"BearerAuth\""),
                "@SecurityScheme debe tener name = \"BearerAuth\"");
        assertTrue(source.contains("type = SecuritySchemeType.HTTP"),
                "@SecurityScheme debe ser de tipo HTTP");
        assertTrue(source.contains("scheme = \"bearer\""),
                "@SecurityScheme debe usar scheme bearer");
        assertTrue(source.contains("bearerFormat = \"JWT\""),
                "@SecurityScheme debe tener bearerFormat JWT");
    }
}
