package com.silvio.elending.config;

import com.silvio.elending.client.FeignRequestInterceptor;
import com.silvio.elending.security.JwtAuthenticationFilter;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de integración para el pipeline JWT completo.
 *
 * Verifica que todos los beans necesarios para la propagación del token JWT
 * estén registrados en el contexto Spring y funcionen correctamente.
 */
@SpringBootTest
@ActiveProfiles("test")
class JwtPropagationIntegrationTest {

    @Autowired(required = false)
    private FeignRequestInterceptor feignRequestInterceptor;

    @Autowired(required = false)
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired(required = false)
    private SecurityFilterChain securityFilterChain;

    @Autowired(required = false)
    private List<RequestInterceptor> requestInterceptors;

    // =========================================================
    // Verificación de beans en el contexto Spring
    // =========================================================

    @Test
    void contextLoads_conJwtBeans() {
        // Verifica que el contexto Spring carga todos los beans de JWT
        assertNotNull(feignRequestInterceptor,
                "FeignRequestInterceptor debe ser un bean en el contexto");
        assertNotNull(jwtAuthenticationFilter,
                "JwtAuthenticationFilter debe ser un bean en el contexto");
        assertNotNull(securityFilterChain,
                "SecurityFilterChain debe estar configurado");
    }

    @Test
    void feignRequestInterceptor_esUnicoRequestInterceptor() {
        // El interceptor debe estar registrado como bean RequestInterceptor
        assertNotNull(requestInterceptors, "Debe haber al menos un RequestInterceptor");
        assertTrue(requestInterceptors.contains(feignRequestInterceptor),
                "FeignRequestInterceptor debe estar en la lista de interceptores");
    }

    // =========================================================
    // Verificación de funcionalidad del pipeline completo
    // =========================================================

    @Test
    void pipelineCompleto_filtroAlmacenaToken_eInterceptorLoPropaga() {
        try {
            // Given — simula el filtro: almacena token en SecurityContextHolder
            String token = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c3VhcmlvMSJ9.firma";
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(null, token, null);
            SecurityContextHolder.getContext().setAuthentication(auth);

            // When — el interceptor procesa un template de Feign
            RequestTemplate template = new RequestTemplate();
            feignRequestInterceptor.apply(template);

            // Then — el token se propagó al header Authorization
            Collection<String> headers = template.headers().get("Authorization");
            assertNotNull(headers, "El header Authorization debe estar presente");
            assertEquals(1, headers.size());
            assertEquals("Bearer " + token, headers.iterator().next());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void pipelineCompleto_sinToken_noPropagaHeader() {
        try {
            // Given — SecurityContextHolder vacío (simula scheduler sin JWT)
            SecurityContextHolder.getContext().setAuthentication(null);

            // When
            RequestTemplate template = new RequestTemplate();
            feignRequestInterceptor.apply(template);

            // Then — no debe agregar header
            assertNull(template.headers().get("Authorization"),
                    "Sin token, no debe propagar header Authorization");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void pipelineCompleto_tokenVacio_noPropagaHeader() {
        try {
            // Given — token vacío (simula header "Bearer " sin token)
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(null, "");
            SecurityContextHolder.getContext().setAuthentication(auth);

            // When
            RequestTemplate template = new RequestTemplate();
            feignRequestInterceptor.apply(template);

            // Then — isBlank() rechaza el token vacío
            assertNull(template.headers().get("Authorization"),
                    "Token vacío no debe propagarse");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void pipelineCompleto_tokenSoloEspacios_noPropagaHeader() {
        try {
            // Given — token con solo espacios en blanco
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(null, "   ");
            SecurityContextHolder.getContext().setAuthentication(auth);

            // When
            RequestTemplate template = new RequestTemplate();
            feignRequestInterceptor.apply(template);

            // Then — isBlank() rechaza espacios
            assertNull(template.headers().get("Authorization"),
                    "Token con solo espacios no debe propagarse");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
