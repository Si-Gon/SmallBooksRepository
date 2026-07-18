## Última Actualización
- Fecha: 2026-07-18 02:30
- Pipeline: M-01 IDOR — Validación de acceso en 5 endpoints de 3 microservicios

## Estado Actual del Servicio
- Clases principales:
  - `NotificacionService` — orquesta la creación, consulta y marcado de notificaciones. Usa idempotencyKey para evitar duplicados vía RabbitMQ.
  - `Notificacion` (entidad JPA) — modelo con usuarioId, tipo, mensaje, fechaEnvio, leida, idempotencyKey.
  - `NotificacionRepository` — repositorio JPA con métodos de consulta y bulk UPDATE.
  - `NotificacionController` — expone endpoints REST.
  - `NotificacionEventListener` — consume eventos de RabbitMQ para crear notificaciones.
- Endpoints expuestos:
  - `POST /api/notificaciones` — crear notificación (idempotente vía SHA-256)
  - `GET /api/notificaciones/usuario/{usuarioId}` — obtener todas ordenadas por fecha DESC
  - `GET /api/notificaciones/usuario/{usuarioId}/no-leidas` — obtener solo no leídas
  - `PUT /api/notificaciones/{id}/leer` — marcar una como leída
  - `PUT /api/notificaciones/usuario/{usuarioId}/leer-todas` — marcar todas como leídas (bulk UPDATE)
- Dependencias externas: RabbitMQ (notificaciones), PostgreSQL (BD compartida)
- Cobertura de tests: ~85% (130 tests, 0 failures, 31 skipped por RabbitMQ)

## Decisiones Técnicas
- **M-01 IDOR: validación de acceso via header X-User-Id** — Se agregó helper `validarAccesoUsuario()` que compara `X-User-Id` header (propagado por Gateway) con `{usuarioId}` path variable. Lanza `AccesoDenegadoException` si no coinciden o si el header está ausente. ROLE_ADMIN puede acceder a cualquier usuarioId (admin bypass). Alternativa descartada: Spring Security — no aplica porque microservicio no tiene SecurityContext (validación es pura lógica de negocio en controller).
- **M-07 Hotfix: Revertir @SecurityScheme a configuración programática** — Se eliminó `@SecurityScheme` annotation class-level (causaba `ArrayIndexOutOfBoundsException` en ASM scanner de Spring Boot 3.3.11). Reemplazado por configuración programática en `customOpenAPI()` usando modelos OpenAPI. SwaggerConfigTest migrado de static source scan a `@SpringBootTest(classes = SwaggerConfig.class, webEnvironment = NONE)`. Alternativa descartada: mantener anotación — incompatible con ASM 9.x de Spring Boot 3.3.11.
- **Bulk UPDATE con @Modifying** — Se reemplazó el bucle en memoria con `saveAll()` (generaba N UPDATEs individuales) por un solo `UPDATE` vía `@Modifying @Query` en `NotificacionRepository`. `clearAutomatically = true` evita contexto de persistencia obsoleto; `flushAutomatically = true` asegura consistencia antes del UPDATE masivo. Alternativa descartada: `saveAll()` con lista completa — generaba N consultas a la BD.
- **IdempotencyKey con SHA-256** — Se usa un hash de (usuarioId + "|" + tipo + "|" + mensaje) como clave única para detectar duplicados de RabbitMQ. Alternativa descartada: UUID aleatorio (no permite detección de mismo mensaje reprocesado).
- **@Transactional en marcarTodasLeidas()** — Se añadió `@Transactional` al método `marcarTodasLeidas()` para que el `@Modifying` se ejecute dentro de una transacción.

## Criterios de Aceptación Cumplidos
- **M-01: Validar X-User-Id vs {usuarioId} en endpoints vulnerables** → Implementado via helper `validarAccesoUsuario()` en 3 endpoints: `obtenerPorUsuario`, `obtenerNoLeidas`, `marcarTodasLeidas`. AccesoDenegadoException→403. Tests: mismo usuario 200, otro usuario 403, admin 200, header ausente 403 (12 tests agregados).
- 1) `NotificacionService.marcarTodasLeidas()` debe usar bulk UPDATE en lugar de cargar + iterar + saveAll() → Implementado con `@Modifying @Query("UPDATE Notificacion n SET n.leida = true WHERE n.usuarioId = :usuarioId AND n.leida = false")` en `NotificacionRepository`. Servicio llamado `marcarTodasLeidasPorUsuario()` y anotado con `@Transactional`.

## Historial de Cambios
- 2026-07-18 — M-01 IDOR: Agregada validación de acceso en 3 endpoints. Helper `validarAccesoUsuario()` con admin bypass. AccesoDenegadoException + handler 403 en GlobalExceptionHandler. Tests IDOR con 4 escenarios por endpoint (12 tests). Tests: 28 controller tests PASS.
- 2026-07-18 — M-07 Hotfix: ASM bug fix. @SecurityScheme eliminado de SwaggerConfig, reemplazado por configuración programática. SwaggerConfigTest migrado a @SpringBootTest. Verificado: 132 tests PASS (31 skips pre-existentes), JaCoCo OK.
- 2026-07-17 — M-05: @SecurityScheme en SwaggerConfig. SwaggerConfigTest static scan. Import SecuritySchemeType corregido.
- 2026-07-15 — Implementado bulk UPDATE con @Modifying @Query en marcarTodasLeidasPorUsuario(). Agregado @Transactional al servicio. Tests de repositorio y servicio actualizados.
