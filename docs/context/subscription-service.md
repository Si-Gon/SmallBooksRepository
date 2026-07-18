## Última Actualización
- Fecha: 2026-07-18
- Pipeline: M-07 Hotfix — Revertir @SecurityScheme a configuración programática (ASM bug Spring Boot 3.3.11)

## Estado Actual del Servicio
- Clases principales:
  - `SuscripcionController` — REST controller para operaciones de suscripción. Identifica al usuario autenticado mediante `@RequestHeader("X-User-Id")` propagado por el Gateway; ya no extrae el usuario del JWT.
  - `SuscripcionService` — Capa de negocio de suscripciones. Gestiona planes BASICO/PREMIUM, límites de préstamo y cancelaciones.
  - `Suscripcion` (model) — Entidad JPA que representa la suscripción de un usuario.
  - `SuscripcionRepository` — Spring Data JPA repository para consultas de suscripción.
  - `GlobalExceptionHandler` — Maneja `MissingRequestHeaderException` como 400; se eliminó el manejador de `TokenExtraccionException`.
  - Eliminados en este pipeline: `JwtExtractor.java`, `JwtExtractorTest.java`, `TokenExtraccionException.java`.
- Endpoints expuestos:
  - `GET /api/subscriptions/mi-plan` — Plan activo del usuario autenticado (requiere header `X-User-Id`).
  - `GET /api/subscriptions/usuario/{usuarioId}` — Plan por usuario (interno, usado por E-Lending Service via Feign).
  - `POST /api/subscriptions` — Crear o cambiar suscripción (requiere header `X-User-Id`).
  - `PATCH /api/subscriptions/cancelar` — Cancelar suscripción activa (requiere header `X-User-Id`).
- Dependencias externas: base de datos (MySQL/PostgreSQL), RabbitMQ (notificaciones), E-Lending Service (Feign)
- Cobertura de tests: 58 tests, 0 fallos, 1 skipped. Controllers afectados ~95% línea; tests de integración agregados en `SuscripcionControllerIntegrationTest`.

## Decisiones Técnicas
- **M-07 Hotfix: Revertir @SecurityScheme a configuración programática** — Se eliminó `@SecurityScheme` annotation class-level (causaba `ArrayIndexOutOfBoundsException` en ASM scanner de Spring Boot 3.3.11). Reemplazado por configuración programática en `customOpenAPI()` usando modelos OpenAPI. SwaggerConfigTest migrado de static source scan a `@SpringBootTest(classes = SwaggerConfig.class, webEnvironment = NONE)`. Alternativa descartada: mantener anotación — incompatible con ASM 9.x de Spring Boot 3.3.11.
- **C-01: Eliminación de `JwtExtractor`** — `subscription-service` ya no decodifica Base64 del payload JWT sin verificar firma. Confía en el header `X-User-Id` validado e inyectado por el Gateway. Alternativa descartada: validar el JWT localmente — duplicaría el secret y la lógica de validación en cada microservicio.
- **Manejo de header ausente** — `GlobalExceptionHandler` captura `MissingRequestHeaderException` y responde 400. Los tests de "token inválido" se reemplazaron por tests de header `X-User-Id` ausente.
- **Enlaces HATEOAS con `methodOn(...)`** — Se pasa `usuarioId` o `null` en el argumento del header dentro de `methodOn(...)` porque el header no forma parte de la URI generada.
- **Validación de plan único activo** — Se mantiene la restricción de una sola suscripción activa por usuario; al crear una nueva se cancela la activa previa.

## Criterios de Aceptación Cumplidos
- **C-01: Eliminar `JwtExtractor.java` de `subscription-service`** → Eliminado junto con `JwtExtractorTest.java` y `TokenExtraccionException.java`.
- **C-01: Actualizar `SuscripcionController` para usar `@RequestHeader("X-User-Id") String usuarioId`** → Implementado en `miPlan`, `crear` y `cancelar`.
- **C-01: Actualizar `SuscripcionControllerTest`** → Se quitaron mocks de `JwtExtractor`, se usan headers `X-User-Id` y se agregó test de header ausente.
- **C-01: Mantener comentarios en español consistentes** → Comentarios y descripciones OpenAPI actualizados.

## Historial de Cambios
- 2026-07-18 — M-07 Hotfix: ASM bug fix. @SecurityScheme eliminado de SwaggerConfig, reemplazado por configuración programática. SwaggerConfigTest migrado a @SpringBootTest. Verificado: 60 tests PASS (1 skip pre-existente), JaCoCo OK.
- 2026-07-17 — M-05: @SecurityScheme en SwaggerConfig. SwaggerConfigTest static scan. Import SecuritySchemeType corregido.
- 2026-07-16 — C-01: Eliminados `JwtExtractor` y `TokenExtraccionException`. `SuscripcionController` identifica usuarios vía header `X-User-Id`. Tests actualizados; agregado `SuscripcionControllerIntegrationTest`.
