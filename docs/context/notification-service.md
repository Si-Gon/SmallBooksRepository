## Última Actualización
- Fecha: 2026-07-15 21:30
- Pipeline: Corrección de dos issues de rendimiento JPA (bulk UPDATE + @EntityGraph)

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
- **Bulk UPDATE con @Modifying** — Se reemplazó el bucle en memoria con `saveAll()` (generaba N UPDATEs individuales) por un solo `UPDATE` vía `@Modifying @Query` en `NotificacionRepository`. `clearAutomatically = true` evita contexto de persistencia obsoleto; `flushAutomatically = true` asegura consistencia antes del UPDATE masivo. Alternativa descartada: `saveAll()` con lista completa — generaba N consultas a la BD.
- **IdempotencyKey con SHA-256** — Se usa un hash de (usuarioId + "|" + tipo + "|" + mensaje) como clave única para detectar duplicados de RabbitMQ. Alternativa descartada: UUID aleatorio (no permite detección de mismo mensaje reprocesado).
- **@Transactional en marcarTodasLeidas()** — Se añadió `@Transactional` al método `marcarTodasLeidas()` para que el `@Modifying` se ejecute dentro de una transacción.

## Criterios de Aceptación Cumplidos
- 1) `NotificacionService.marcarTodasLeidas()` debe usar bulk UPDATE en lugar de cargar + iterar + saveAll() → Implementado con `@Modifying @Query("UPDATE Notificacion n SET n.leida = true WHERE n.usuarioId = :usuarioId AND n.leida = false")` en `NotificacionRepository`. Servicio llamado `marcarTodasLeidasPorUsuario()` y anotado con `@Transactional`.

## Historial de Cambios
- 2026-07-15 — Implementado bulk UPDATE con @Modifying @Query en marcarTodasLeidasPorUsuario(). Agregado @Transactional al servicio. Tests de repositorio y servicio actualizados.
