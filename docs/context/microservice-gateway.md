## Última Actualización
- Fecha: 2026-07-17 11:30
- Pipeline: Fix critical security issues H-04 (IP spoofing en rate limiting) y H-05 (Springdoc expuesto en producción)

## Estado Actual del Servicio
- Clases principales:
  - `JwtAuthFilter` — filtro de Spring Cloud Gateway que valida JWTs en cada request entrante. Verifica firma, expiración, tipo de token (solo access), y propaga identidad (`X-User-Id`, `X-User-Roles`) al microservicio destino. Usa API jjwt 0.12.x: `parseSignedClaims().getPayload()`. Deriva la clave HMAC con `secret.getBytes(StandardCharsets.UTF_8)` para consistencia cross-plataforma.
  - `GlobalRateLimitingFilter` — filtro global de rate limiting con Bucket4j (Token Bucket). Protege los 13 microservicios con dos niveles: global (1000 req/min) y por IP (50 req/min). `getClientIp()` valida proxy confiable antes de leer X-Forwarded-For. Método `isTrustedProxy()` verifica: 127.0.0.1, ::1, 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16.
  - `TraceIdResponseFilter` — filtro que inyecta trace ID en responses.
  - `CorsConfig` — configuración CORS para el gateway.
  - `RateLimitingConfig` — configuración de rate limiting.
- Endpoints expuestos:
  - Gateway routing (configurado vía YAML en Config Server) — enruta requests a microservicios backend
  - Rutas /auth/** excluidas del filtro JWT (públicas: login, register, refresh)
  - `/swagger-ui.html` y `/v3/api-docs` — deshabilitados por defecto, solo habilitados con env var `SWAGGER_ENABLED=true`
- Dependencias externas: identity-services (validación JWT con misma clave secreta), Spring Cloud Config (configuración centralizada), Eureka (service discovery), jjwt 0.12.x, Bucket4j 7.6.0, Springdoc OpenAPI
- Cobertura de tests: 98 tests, 0 failures, 0 skipped. Tests cubren: rate limiting (19), proxy confiable (4), Springdoc config (4), JWT (restantes).

## Decisiones Técnicas
- **C-3: Migración API jjwt 0.12.x** — `parseClaimsJws()` reemplazado por `parseSignedClaims()`, `.getBody()` reemplazado por `.getPayload()` en JwtAuthFilter.java. Comentario en línea 67 actualizado de `parseClaimsJws` a `parseSignedClaims` para consistencia. Tests de regresión estática (JwtAuthFilterApiMigrationTest) verifican que la API deprecada no sea reintroducida. Alternativa descartada: mantener API deprecada — genera warnings de compilación y será removida en futuras versiones de jjwt.
- **Identity Propagation con h.set()** — El filtro usa `h.set()` en lugar de `r.header()` para REEMPLAZAR headers X-User-Id y X-User-Roles, previniendo inyección de identidad por duplicación de headers.
- **Rechazo de refresh tokens** — El filtro verifica el claim "type" del JWT y solo permite access tokens. Refresh tokens son rechazados con 401.
- **Null subject protection** — Si el JWT no tiene claim "sub", el filtro rechaza con 401 en lugar de propagar null en headers.
- **C-02: UTF-8 en derivación de clave HMAC** — `JwtAuthFilter` usa `Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))` en lugar de `secret.getBytes()` con charset por defecto. Garantiza que tokens generados por `identity-services` en Windows/Linux se validen correctamente en cualquier plataforma. Alternativa descartada: mantener charset por defecto — rompe la validación cross-plataforma.
- **H-04: Validación de proxy confiable en getClientIp()** — `getClientIp()` ahora verifica si la dirección remota real es un proxy confiable (localhost o red privada RFC 1918) antes de leer X-Forwarded-For. Si el cliente conecta directamente, se ignora X-Forwarded-For y se usa la dirección remota. Si es proxy confiable, toma solo la primera IP del header (split por coma). Método `isTrustedProxy()` usa prefix matching sin dependencias externas. Alternativas descartadas: confiar en X-Forwarded-For sin validación (vulnerable a suplantación), usar librería externa para validación CIDR (innecesario para 5 rangos).
- **H-05: Springdoc deshabilitado por defecto** — `springdoc.swagger-ui.enabled` y `springdoc.api-docs.enabled` usan `${SWAGGER_ENABLED:false}` en ambos archivos YAML (local y Config Server). Solo se habilitan con env var `SWAGGER_ENABLED=true`. En `docker-compose.yml` se agregó la variable al servicio gateway para desarrollo local. No se creó clase de configuración Java — todo resuelto vía YAML. Alternativa descartada: crear clase `@Configuration` condicional (innecesario con soporte nativo de env vars en YAML).

## Criterios de Aceptación Cumplidos
- C-3) API jjwt deprecada reemplazada en gateway → `parseSignedClaims().getPayload()` en JwtAuthFilter.java. Comentario actualizado. JwtAuthFilterApiMigrationTest creado como test de regresión estática (2 tests: source scan para parseSignedClaims y getPayload).
- C-02) Derivar clave HMAC con `StandardCharsets.UTF_8` en gateway → `JwtAuthFilter` usa `secret.getBytes(StandardCharsets.UTF_8)`. `JwtAuthFilterTest` actualizado para usar UTF-8 y verificar que tokens firmados con clave Cp1252 no validen contra clave UTF-8.
- H-04) Suplantación de IP en rate limiting mitigada → `getClientIp()` valida proxy confiable antes de leer X-Forwarded-For. Si remote address no es confiable, se ignora X-Forwarded-For. Se agregó `isTrustedProxy(String ip)` con prefix matching para 127.0.0.1, ::1, 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16. 4 tests nuevos: proxy confiable (127.0.0.1), proxy no confiable (8.8.8.8), IP privada (192.168.1.1), cadena de IPs.
- H-05) Springdoc deshabilitado por defecto → `springdoc.swagger-ui.enabled: ${SWAGGER_ENABLED:false}` y `springdoc.api-docs.enabled: ${SWAGGER_ENABLED:false}` en `application.yml` (local) y `msvc-gateway.yml` (Config Server). `SWAGGER_ENABLED=true` agregado al servicio gateway en `docker-compose.yml`. 2 tests nuevos: default=false sin env var, activación con SWAGGER_ENABLED=true.

## Historial de Cambios
- 2026-07-16 — C-02: `JwtAuthFilter` usa `StandardCharsets.UTF_8` para derivar la clave HMAC. `JwtAuthFilterTest` actualizado con tests cross-plataforma.
- 2026-07-16 — C-3: parseClaimsJws() → parseSignedClaims(), getBody() → getPayload() en JwtAuthFilter.java. Comentario en línea 67 actualizado. JwtAuthFilterApiMigrationTest creado. Total: 88 tests microservice-gateway.
- 2026-07-17 — H-04: `getClientIp()` reescrito con validación de proxy confiable. Nuevo método `isTrustedProxy(String ip)` con prefix matching para redes RFC 1918. 4 tests nuevos en GlobalRateLimitingFilterTest.java.
- 2026-07-17 — H-05: Springdoc deshabilitado por defecto via `${SWAGGER_ENABLED:false}` en YAML. `docker-compose.yml` habilita Swagger solo para gateway en dev local. 4 tests nuevos en SpringdocConfigTest.java. Total: 98 tests microservice-gateway.
