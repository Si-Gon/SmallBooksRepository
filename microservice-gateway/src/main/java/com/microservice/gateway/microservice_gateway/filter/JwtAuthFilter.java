package com.microservice.gateway.microservice_gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
 
import java.security.Key;
 
// ¿QUÉ HACE ESTE FILTRO?
//
// Cada request que llega al gateway pasa por aquí ANTES de llegar
// al microservicio destino. El filtro hace tres cosas:
//
//   1. Verifica que el header "Authorization: Bearer <token>" existe
//   2. Valida la firma del token con la misma clave secreta que usó
//      identity-service para generarlo
//   3. Verifica que sea un access token (no un refresh token)
//
// Si todo está bien → deja pasar la request al microservicio
// Si algo falla    → devuelve 401 UNAUTHORIZED de inmediato
//
// Las rutas /auth/** NO tienen este filtro — son públicas
// (login, register, refresh) y se configuran en msvc-gateway.yml
 
@Component
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {
 
    // Lee el secret del msvc-gateway.yml descargado desde Config Server
    // Debe ser exactamente el mismo secret que usa identity-service
    @Value("${jwt.secret}")
    private String secret;
 
    public JwtAuthFilter() {
        super(Config.class);
    }
 
    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
 
            // --- Paso 1: Leer el header Authorization ---
            String authHeader = exchange.getRequest()
                    .getHeaders()
                    .getFirst(HttpHeaders.AUTHORIZATION);
 
            // Si no hay header o no empieza con "Bearer " → 401
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return rechazar(exchange, HttpStatus.UNAUTHORIZED);
            }
 
            // Extraer el token (quitar el prefijo "Bearer ")
            String token = authHeader.substring(7);
 
            try {
                // --- Paso 2: Validar firma y expiración del token ---
                // Keys.hmacShaKeyFor convierte el String secret en una Key criptográfica
                // parseClaimsJws lanza excepción si el token es inválido o expiró
                Key key = Keys.hmacShaKeyFor(secret.getBytes());
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(key)
                        .build()
                        .parseClaimsJws(token)
                        .getBody();
 
                // --- Paso 3: Verificar que sea access token ---
                // El campo "type" dentro del token lo pone identity-service
                // Un refresh token no debe usarse para autenticar requests normales
                String tokenType = claims.get("type", String.class);
                if (!"access".equals(tokenType)) {
                    return rechazar(exchange, HttpStatus.UNAUTHORIZED);
                }
 
                // Token válido → dejar pasar al microservicio destino
                return chain.filter(exchange);
 
            } catch (Exception e) {
                // Token malformado, expirado, firma incorrecta → 401
                return rechazar(exchange, HttpStatus.UNAUTHORIZED);
            }
        };
    }
 
    // Corta la request y devuelve el código HTTP indicado
    // Mono<Void> es el tipo reactivo equivalente a "void" en programación normal
    private Mono<Void> rechazar(ServerWebExchange exchange, HttpStatus status) {
        exchange.getResponse().setStatusCode(status);
        return exchange.getResponse().setComplete();
    }
 
    // Clase de configuración requerida por AbstractGatewayFilterFactory
    // Vacía por ahora — se usaría si el filtro necesitara parámetros
    // Por ejemplo: roles permitidos, rutas excluidas, etc.
    public static class Config {
    }
}
