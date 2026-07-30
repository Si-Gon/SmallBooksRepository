package com.silvio.elending.config;

import com.silvio.elending.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// Configuración de seguridad para E-Lending Service.
// La autenticación se delega al API Gateway — este servicio valida los headers
// propagados por el Gateway (X-User-Id, X-User-Roles) vía JwtAuthenticationFilter
// y aplica reglas de autorización por endpoint.
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF deshabilitado: API stateless con JWT propagado por el Gateway
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // ─── Endpoints públicos — no requieren autenticación ───
                        // Health check para orquestadores y load balancers
                        .requestMatchers("/actuator/health").permitAll()
                        // Documentación OpenAPI/Swagger (acceso público para desarrollo)
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // ─── Endpoints de préstamos — requieren rol USER ───
                        // POST /api/lending/prestamos — crear préstamo (requiere usuario autenticado)
                        .requestMatchers(HttpMethod.POST, "/api/lending/prestamos").hasRole("USER")
                        // GET /api/lending/prestamos/activos — préstamos activos del usuario autenticado
                        .requestMatchers(HttpMethod.GET, "/api/lending/prestamos/activos").hasRole("USER")
                        // GET /api/lending/prestamos/historial — historial propio (ruta exacta, sin path variable)
                        .requestMatchers(HttpMethod.GET, "/api/lending/prestamos/historial").hasRole("USER")
                        // GET /api/lending/prestamos/historial/{usuarioId} — historial de un usuario específico.
                        // La validación IDOR (mismo usuario o admin) se hace en el controller vía validarAccesoUsuario().
                        // SecurityConfig solo exige que el request tenga rol USER para llegar al controller.
                        .requestMatchers(HttpMethod.GET, "/api/lending/prestamos/historial/*").hasRole("USER")

                        // ─── Endpoints de administración — requieren rol ADMIN ───
                        // GET /api/lending/prestamos/todos — todos los préstamos del sistema (uso interno Analytics via Feign)
                        .requestMatchers(HttpMethod.GET, "/api/lending/prestamos/todos").hasRole("ADMIN")

                        // ─── Fallback — cualquier otra request requiere autenticación ───
                        .anyRequest().authenticated())
                .sessionManagement(session ->
                        // Stateless: no se crean sesiones HTTP, cada request se autentica vía headers
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
