## Última Actualización
- Fecha: 2026-07-29
- Pipeline: AC-01 — Actuator: management.endpoints.web.exposure.include: health (ya tenía spring-boot-starter-actuator)

## Estado Actual del Servicio
- spring-boot-starter-actuator (ya existía). management.endpoints.web.exposure.include: health — expone /actuator/health.
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
- **Z-01: NotificacionNotFoundException reemplazado por java.util.NoSuchElementException** — Se eliminó la clase zombie `NotificacionNotFoundException`. El throw en `NotificacionService.marcarLeida()` se reemplazó por `NoSuchElementException`. Alternativa descartada: mantener la excepción personalizada — cero referencias activas.
- **M-10: @ExceptionHandler(NoSuchElementException.class) → 404 NOT_FOUND** — Se agregó handler en GlobalExceptionHandler para capturar `NoSuchElementException` (lanzado por `Optional.orElseThrow()`) y retornar 404 en lugar de 500. Sigue exactamente el patrón de subscription-service. Test unitario en GlobalExceptionHandlerTest verifica NoSuchElementException → 404. Alternativa descartada: usar ResponseEntityExceptionHandler — más complejo sin beneficio para un caso simple.
- **IdempotencyKey con SHA-256** — Se usa un hash de (usuarioId + "|" + tipo + "|" + mensaje) como clave única para detectar duplicados de RabbitMQ. Alternativa descartada: UUID aleatorio (no permite detección de mismo mensaje reprocesado).
- **@Transactional en marcarTodasLeidas()** — Se añadió `@Transactional` al método `marcarTodasLeidas()` para que el `@Modifying` se ejecute dentro de una transacción.

## Criterios de Aceptación Cumplidos
- **M-01: Validar X-User-Id vs {usuarioId} en endpoints vulnerables** → Implementado via helper `validarAccesoUsuario()` en 3 endpoints: `obtenerPorUsuario`, `obtenerNoLeidas`, `marcarTodasLeidas`. AccesoDenegadoException→403. Tests: mismo usuario 200, otro usuario 403, admin 200, header ausente 403 (12 tests agregados).
- 1) `NotificacionService.marcarTodasLeidas()` debe usar bulk UPDATE en lugar de cargar + iterar + saveAll() → Implementado con `@Modifying @Query("UPDATE Notificacion n SET n.leida = true WHERE n.usuarioId = :usuarioId AND n.leida = false")` en `NotificacionRepository`. Servicio llamado `marcarTodasLeidasPorUsuario()` y anotado con `@Transactional`.
- **Z-01) Reemplazar NotificacionNotFoundException por NoSuchElementException en NotificacionService** → `NotificacionService.marcarLeida()` usa `NoSuchElementException` en lugar de `NotificacionNotFoundException`. Clase zombie eliminada. Tests actualizados. Compilación verificada.
- **M-10) NoSuchElementException → 404 NOT_FOUND en GlobalExceptionHandler** → Handler `@ExceptionHandler(NoSuchElementException.class)` retorna 404 con mensaje descriptivo. Test `noSuchElement_debeRetornar404()` en GlobalExceptionHandlerTest verifica 404. Build: 143 tests PASS.

## Historial de Cambios
- 2026-07-20 — M-10: @ExceptionHandler(NoSuchElementException.class) → 404 NOT_FOUND verificado. Handler y test ya implementados. 143 tests PASS.
- 2026-07-20 — Z-01: NotificacionNotFoundException eliminada, reemplazada por NoSuchElementException en NotificacionService.marcarLeida(). Tests actualizados (service + controller). Compilación verificada.
- 2026-07-18 — M-01 IDOR: Agregada validación de acceso en 3 endpoints. Helper `validarAccesoUsuario()` con admin bypass. AccesoDenegadoException + handler 403 en GlobalExceptionHandler. Tests IDOR con 4 escenarios por endpoint (12 tests). Tests: 28 controller tests PASS.
- 2026-07-18 — M-07 Hotfix: ASM bug fix. @SecurityScheme eliminado de SwaggerConfig, reemplazado por configuración programática. SwaggerConfigTest migrado a @SpringBootTest. Verificado: 132 tests PASS (31 skips pre-existentes), JaCoCo OK.
- 2026-07-17 — M-05: @SecurityScheme en SwaggerConfig. SwaggerConfigTest static scan. Import SecuritySchemeType corregido.
- 2026-07-15 — Implementado bulk UPDATE con @Modifying @Query en marcarTodasLeidasPorUsuario(). Agregado @Transactional al servicio. Tests de repositorio y servicio actualizados.
- 2026-07-29 — AC-01: management.endpoints.web.exposure.include: health agregado (spring-boot-starter-actuator ya existía).
