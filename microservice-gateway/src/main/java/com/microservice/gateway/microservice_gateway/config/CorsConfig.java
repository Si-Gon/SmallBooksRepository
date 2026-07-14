package com.microservice.gateway.microservice_gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

// ─────────────────────────────────────────────────────────────
// CONFIGURACIÓN CORS PARA EL GATEWAY
// ─────────────────────────────────────────────────────────────
//
// ¿QUÉ HACE?
// Configura CORS (Cross-Origin Resource Sharing) en el gateway
// para permitir peticiones desde orígenes específicos (frontend).
//
// ¿POR QUÉ ORÍGENES ESPECÍFICOS Y NO "*"?
// Permitir allowedOrigins("*") es un riesgo de seguridad:
// cualquier sitio externo podría hacer peticiones al gateway
// desde el navegador de un usuario autenticado (CSRF-like).
// Al listar solo orígenes conocidos limitamos el ataque.
//
// ¿CÓMO SE CONFIGURA?
// Los orígenes permitidos se definen en msvc-gateway.yml:
//   gateway:
//     cors:
//       allowed-origins: http://localhost:4200,http://localhost:3000
//
// Si no se configuran, se usa una lista por defecto para desarrollo.

@Configuration
public class CorsConfig {

    @Value("${gateway.cors.allowed-origins:http://localhost:4200,http://localhost:3000,http://localhost:5173}")
    private List<String> allowedOrigins;

    // ── Getter package-private para tests ──────────────────────────
    // Expone la lista de orígenes permitidos para que CorsConfigTest
    // pueda verificar que se inyectaron correctamente desde properties.
    List<String> getAllowedOriginsForTest() {
        return allowedOrigins;
    }

    /**
     * Filtro CORS reactivo que se aplica a todas las rutas del gateway.
     *
     * Configura:
     * - Orígenes permitidos (lista blanca desde properties)
     * - Métodos HTTP permitidos (GET, POST, PUT, DELETE, PATCH, OPTIONS)
     * - Headers permitidos (todos)
     * - Credenciales (cookies, auth headers) habilitadas
     * - MaxAge 1 hora (reduce preflight requests)
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        // Aplica la configuración CORS a todas las rutas del gateway
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
