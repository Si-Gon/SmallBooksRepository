## Última Actualización
- Fecha: 2026-07-15
- Pipeline: JPA performance optimization — @Transactional(readOnly=true) additions

## Estado Actual del Servicio
- Clases principales:
  - `ContentService` — Capa de negocio de contenido. Verifica préstamos activos vía Feign client hacia elending-service, luego obtiene archivos vía Feign client hacia ingestion-service. Sin acceso directo a JPA/Hibernate.
  - `LendingClient` — Feign client hacia elending-service. `obtenerPrestamosActivos(authHeader)` retorna `List<PrestamoDTO>`.
  - `IngestionClient` — Feign client hacia ingestion-service. `obtenerBytes(libroId)` retorna `byte[]`.
  - `ContentController` — REST controller para descarga de archivos.
  - `PrestamoDTO` — DTO de préstamo (id, libroId, usuarioId, estado, fechaInicio, fechaVencimiento).
  - `AccesoDenegadoException` — Excepción lanzada cuando el usuario no tiene préstamo activo del libro solicitado.
- Endpoints expuestos:
  - `GET /api/content/archivo/{libroId}` — Descargar archivo de un libro (requiere préstamo activo, autenticación vía header).
- Dependencias externas: elending-service (Feign), ingestion-service (Feign), Micrometer Tracing (observabilidad)
- Cobertura de tests: Sin tests unitarios aún

## Decisiones Técnicas
- `@Transactional(readOnly = true)` agregado a `obtenerArchivo()` — aunque el servicio solo usa Feign Clients (sin JPA directo), la anotación marca la intención de solo-lectura y habilita optimizaciones si en el futuro se agrega acceso a base de datos. Consistente con el resto del código base.

## Criterios de Aceptación Cumplidos
- Agregar `@Transactional(readOnly = true)` a métodos read-only de content-service → Implementado en `obtenerArchivo()`. Import agregado. Compilación verificada.

## Historial de Cambios
- 2026-07-15 — Agregado `@Transactional(readOnly = true)` a `obtenerArchivo()` para optimización de rendimiento JPA y consistencia con el código base.
