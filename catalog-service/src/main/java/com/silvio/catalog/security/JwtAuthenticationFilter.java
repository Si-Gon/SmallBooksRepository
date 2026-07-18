package com.silvio.catalog.security;

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

// Filtro que extrae los roles del header X-User-Roles (propagado por el API Gateway)
// y construye la autenticación en SecurityContextHolder.
// El Gateway ya validó el JWT — este filtro solo traduce los headers a autoridades de Spring Security.
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        try {
            String userId = request.getHeader("X-User-Id");
            String rolesHeader = request.getHeader("X-User-Roles");

            List<SimpleGrantedAuthority> authorities = parseRoles(rolesHeader);

            // Usa X-User-Id como principal, o null si no está presente
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);

            chain.doFilter(request, response);
        } finally {
            // Limpia el contexto de seguridad al finalizar el request para evitar
            // fuga de autenticación entre peticiones reutilizadas por el pool de threads
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
