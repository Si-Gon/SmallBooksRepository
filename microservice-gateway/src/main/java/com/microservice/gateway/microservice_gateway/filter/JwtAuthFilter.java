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
 
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
 
// ¿QUÉ HACE ESTE FILTRO?
//
// Cada request que llega al gateway pasa por aquí ANTES de llegar
// al microservicio destino. El filtro hace cuatro cosas:
//
//   1. Verifica que el header "Authorization: Bearer <token>" existe
//   2. Valida la firma del token con la misma clave secreta que usó
//      identity-service para generarlo
//   3. Verifica que sea un access token (no un refresh token)
//   4. Propaga la identidad (userId, roles) como headers X-User-Id y
//      X-User-Roles al microservicio destino
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
                // parseSignedClaims lanza excepción si el token es inválido o expiró
                SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
                Claims claims = Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
 
                // --- Paso 3: Verificar que sea access token ---
                // El campo "type" dentro del token lo pone identity-service
                // Un refresh token no debe usarse para autenticar requests normales
                String tokenType = claims.get("type", String.class);
                if (!"access".equals(tokenType)) {
                    return rechazar(exchange, HttpStatus.UNAUTHORIZED);
                }
 
                // --- Paso 4: Propagar identidad al microservicio destino ---
                // Extraemos userId (sub del token) y roles del JWT y los inyectamos
                // como headers HTTP para que el microservicio destino sepa quién es
                // el usuario sin tener que validar el token nuevamente.
                // Usamos h.set() en lugar de r.header() para REEMPLAZAR cualquier
                // header X-User-Id o X-User-Roles que el cliente haya enviado,
                // evitando así inyección de identidad por duplicación de headers.
                String userId = claims.getSubject();
                String roles = claims.get("roles", String.class);

                if (userId == null) {
                    return rechazar(exchange, HttpStatus.UNAUTHORIZED);
                }

                ServerWebExchange mutatedExchange = exchange.mutate()
                        .request(r -> r.headers(h -> {
                            h.set("X-User-Id", userId);
                            h.set("X-User-Roles", roles != null ? roles : "");
                        }))
                        .build();

                return chain.filter(mutatedExchange);
 
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
