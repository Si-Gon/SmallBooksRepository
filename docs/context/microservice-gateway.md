## Última Actualización
- Fecha: 2026-07-16 09:45
- Pipeline: Corrección de issue crítico de seguridad C-3 (migración API jjwt deprecada)

## Estado Actual del Servicio
- Clases principales:
  - `JwtAuthFilter` — filtro de Spring Cloud Gateway que valida JWTs en cada request entrante. Verifica firma, expiración, tipo de token (solo access), y propaga identidad (X-User-Id, X-User-Roles) al microservicio destino. Usa API jjwt 0.12.x: `parseSignedClaims().getPayload()`.
  - `GlobalRateLimitingFilter` — filtro de limitación de tasa global.
  - `TraceIdResponseFilter` — filtro que inyecta trace ID en responses.
  - `CorsConfig` — configuración CORS para el gateway.
  - `RateLimitingConfig` — configuración de rate limiting.
- Endpoints expuestos:
  - Gateway routing (configurado vía YAML en Config Server) — enruta requests a microservicios backend
  - Rutas /auth/** excluidas del filtro JWT (públicas: login, register, refresh)
- Dependencias externas: identity-services (validación JWT con misma clave secreta), Spring Cloud Config (configuración centralizada), Eureka (service discovery), jjwt 0.12.x
- Cobertura de tests: ~92% (88 tests, 0 failures, 0 errors)

## Decisiones Técnicas
- **C-3: Migración API jjwt 0.12.x** — `parseClaimsJws()` reemplazado por `parseSignedClaims()`, `.getBody()` reemplazado por `.getPayload()` en JwtAuthFilter.java. Comentario en línea 67 actualizado de `parseClaimsJws` a `parseSignedClaims` para consistencia. Tests de regresión estática (JwtAuthFilterApiMigrationTest) verifican que la API deprecada no sea reintroducida. Alternativa descartada: mantener API deprecada — genera warnings de compilación y será removida en futuras versiones de jjwt.
- **Identity Propagation con h.set()** — El filtro usa `h.set()` en lugar de `r.header()` para REEMPLAZAR headers X-User-Id y X-User-Roles, previniendo inyección de identidad por duplicación de headers.
- **Rechazo de refresh tokens** — El filtro verifica el claim "type" del JWT y solo permite access tokens. Refresh tokens son rechazados con 401.
- **Null subject protection** — Si el JWT no tiene claim "sub", el filtro rechaza con 401 en lugar de propagar null en headers.

## Criterios de Aceptación Cumplidos
- C-3) API jjwt deprecada reemplazada en gateway → `parseSignedClaims().getPayload()` en JwtAuthFilter.java. Comentario actualizado. JwtAuthFilterApiMigrationTest creado como test de regresión estática (2 tests: source scan para parseSignedClaims y getPayload).

## Historial de Cambios
- 2026-07-16 — C-3: parseClaimsJws() → parseSignedClaims(), getBody() → getPayload() en JwtAuthFilter.java. Comentario en línea 67 actualizado. JwtAuthFilterApiMigrationTest creado. Total: 88 tests microservice-gateway.
