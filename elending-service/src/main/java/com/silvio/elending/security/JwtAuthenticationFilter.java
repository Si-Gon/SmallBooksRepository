package com.silvio.elending.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

// Filtro que extrae los headers propagados por el API Gateway (X-User-Id, X-User-Roles)
// y el token JWT del header Authorization, construyendo la autenticación en SecurityContextHolder.
// - X-User-Id → principal (identidad del usuario para el controller)
// - Authorization Bearer → credentials (token JWT para FeignRequestInterceptor)
// - X-User-Roles → authorities (roles para hasRole() en SecurityConfig)
// El Gateway ya validó el JWT — este filtro solo traduce los headers a Spring Security.
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        try {
            String userId = request.getHeader("X-User-Id");
            String rolesHeader = request.getHeader("X-User-Roles");
            String authHeader = request.getHeader("Authorization");

            // Extrae el token JWT del header Authorization (para FeignRequestInterceptor)
            String token = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }

            // Parsea los roles del header X-User-Roles (para hasRole() en SecurityConfig)
            List<SimpleGrantedAuthority> authorities = parseRoles(rolesHeader);

            // Construye la autenticación:
            // - principal = userId (de X-User-Id, null si no está presente)
            // - credentials = token (de Authorization, para FeignRequestInterceptor)
            // - authorities = roles (de X-User-Roles, para hasRole())
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, token, authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);

            chain.doFilter(request, response);
        } finally {
            // Limpia el contexto de seguridad al finalizar el request para evitar
            // fuga de autenticación entre peticiones reutilizadas por el pool de threads
            // (relevante con virtual threads y MODE_INHERITABLETHREADLOCAL)
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * Convierte el header X-User-Roles en una lista de autoridades de Spring Security.
     * <p>
     * Ejemplo: "ROLE_ADMIN,ROLE_USER" → [ROLE_ADMIN, ROLE_USER]
     * Si el header es null o blank, retorna lista vacía.
     */
    private List<SimpleGrantedAuthority> parseRoles(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return Collections.emptyList();
        }

        return Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }
}
