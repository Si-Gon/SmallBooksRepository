package com.silvio.elending.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Filtro que extrae el token JWT del header Authorization
// y lo almacena en SecurityContextHolder para que:
//   1. JwtExtractor pueda leerlo sin depender del header directo
//   2. FeignRequestInterceptor lo propague automáticamente a los Feign Clients
// La autenticación real se delega al API Gateway — este filtro solo
// preserva el token para comunicación entre microservicios.
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        try {
            String authHeader = request.getHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);

                // Almacena el token JWT como credentials del Authentication
                // FeignRequestInterceptor lo leerá desde SecurityContextHolder
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(null, token, null);

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }

            chain.doFilter(request, response);
        } finally {
            // Limpia el contexto de seguridad al finalizar el request para evitar
            // fuga del token entre peticiones reutilizadas por el pool de threads
            // (relevante con virtual threads y MODE_INHERITABLETHREADLOCAL)
            SecurityContextHolder.clearContext();
        }
    }
}
