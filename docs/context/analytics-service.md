## Última Actualización
- Fecha: 2026-07-15
- Pipeline: JPA performance optimization — @Transactional(readOnly=true) additions

## Estado Actual del Servicio
- Clases principales:
  - `AnalyticsService` — Capa de negocio de estadísticas. Consulta datos de préstamos vía Feign client hacia elending-service, sin acceso directo a JPA/Hibernate.
  - `LendingClient` — Feign client hacia elending-service. `obtenerTodos()` retorna `List<PrestamoAnalyticsDTO>`, `obtenerHistorial(usuarioId)` retorna `List<PrestamoAnalyticsDTO>`.
  - `AnalyticsController` — REST controller para endpoints de estadísticas.
  - `EstadisticasDTO` — DTO de respuesta con totalPrestamos, prestamosActivos, prestamosVencidos, librosMasPrestados (top 5), usuariosMasActivos (top 5).
  - `PrestamoAnalyticsDTO` — DTO de préstamo para analytics (id, libroId, usuarioId, estado, fechaInicio, fechaVencimiento).
- Endpoints expuestos:
  - `GET /api/analytics/estadisticas` — Estadísticas globales del sistema.
  - `GET /api/analytics/historial/{usuarioId}` — Historial de préstamos de un usuario específico.
- Dependencias externas: elending-service (Feign), Micrometer Tracing (observabilidad)
- Cobertura de tests: Sin tests unitarios aún

## Decisiones Técnicas
- `@Transactional(readOnly = true)` agregado a `obtenerEstadisticas()` e `historialUsuario()` — aunque el servicio solo usa Feign Clients (sin JPA directo), la anotación marca la intención de solo-lectura y habilita optimizaciones si en el futuro se agrega acceso a base de datos. Consistente con el resto del código base donde servicios read-only tienen esta anotación.

## Criterios de Aceptación Cumplidos
- Agregar `@Transactional(readOnly = true)` a métodos read-only de analytics-service → Implementado en `obtenerEstadisticas()` e `historialUsuario()`. Import agregado. Compilación verificada.

## Historial de Cambios
- 2026-07-15 — Agregado `@Transactional(readOnly = true)` a `obtenerEstadisticas()` e `historialUsuario()` para optimización de rendimiento JPA y consistencia con el código base.
