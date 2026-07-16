## Última Actualización
- Fecha: 2026-07-16
- Pipeline: Agregar Circuit Breaker y FallbackFactory a Feign Clients en content-service

## Estado Actual del Servicio
- Clases principales:
  - `ContentService` — Capa de negocio de contenido. Verifica préstamos activos vía Feign client hacia elending-service, luego obtiene archivos vía Feign client hacia ingestion-service. Sin acceso directo a JPA/Hibernate.
  - `LendingClient` — Feign client hacia elending-service. `obtenerPrestamosActivos(authHeader)` retorna `List<PrestamoDTO>`. Circuit Breaker habilitado con `fallbackFactory`.
  - `LendingClientFallbackFactory` — FallbackFactory para LendingClient. Cuando elending-service no responde, devuelve `Collections.emptyList()`.
  - `IngestionClient` — Feign client hacia ingestion-service. `obtenerBytes(libroId)` retorna `byte[]`. Circuit Breaker habilitado con `fallbackFactory`.
  - `IngestionClientFallbackFactory` — FallbackFactory para IngestionClient. Cuando ingestion-service no responde, devuelve `new byte[0]`.
  - `ContentController` — REST controller para descarga de archivos.
  - `PrestamoDTO` — DTO de préstamo (id, libroId, usuarioId, estado, fechaInicio, fechaVencimiento).
  - `AccesoDenegadoException` — Excepción lanzada cuando el usuario no tiene préstamo activo del libro solicitado.
- Endpoints expuestos:
  - `GET /api/content/archivo/{libroId}` — Descargar archivo de un libro (requiere préstamo activo, autenticación vía header).
- Dependencias externas: elending-service (Feign), ingestion-service (Feign), Micrometer Tracing (observabilidad), Resilience4j (Circuit Breaker)
- Cobertura de tests: 71 tests (LendingClientFallbackFactoryTest: 7, IngestionClientFallbackFactoryTest: 7, Resilience4jConfigIntegrationTest: 10, más tests existentes). 0 fallos.

## Decisiones Técnicas
- `@Transactional(readOnly = true)` agregado a `obtenerArchivo()` — aunque el servicio solo usa Feign Clients (sin JPA directo), la anotación marca la intención de solo-lectura y habilita optimizaciones si en el futuro se agrega acceso a base de datos. Consistente con el resto del código base.
- FallbackFactory en lugar de `fallback` simple — permite loguear la causa exacta del error de conexión, útil para diagnóstico en multi-instancia. Consistente con el patrón de elending-service.
- `spring.cloud.openfeign.circuitbreaker.enabled: true` — habilita el wrapper de Circuit Breaker de Resilience4j sobre cada `@FeignClient`.
- Los nombres de instancia en `resilience4j.circuitbreaker.instances` (`elending-service`, `ingestion-service`) coinciden exactamente con el `name` del `@FeignClient`.
- Configuración de Resilience4j idéntica a elending-service: sliding-window-size=10, failure-rate-threshold=50%, wait-duration-in-open-state=30s, timeout global de 5s.

## Criterios de Aceptación Cumplidos
- Agregar `@Transactional(readOnly = true)` a métodos read-only de content-service → Implementado en `obtenerArchivo()`. Import agregado. Compilación verificada.
- Agregar FallbackFactory para LendingClient (fallback retorna lista vacía) → Implementado en `LendingClientFallbackFactory`.
- Agregar FallbackFactory para IngestionClient (fallback retorna byte[] vacío) → Implementado en `IngestionClientFallbackFactory`.
- Agregar `spring.cloud.openfeign.circuitbreaker.enabled: true` en application.yml → Implementado.
- Agregar Resilience4j circuitbreaker + timelimiter config en application.yml → Implementado con valores idénticos a elending-service.
- Agregar dependencia `spring-cloud-starter-circuitbreaker-resilience4j` en pom.xml → Implementado (gestionada por BOM del parent).

## Historial de Cambios
- 2026-07-15 — Agregado `@Transactional(readOnly = true)` a `obtenerArchivo()` para optimización de rendimiento JPA y consistencia con el código base.
- 2026-07-16 — Agregado Circuit Breaker + FallbackFactory a LendingClient e IngestionClient siguiendo el patrón de elending-service. Resilience4j config en application.yml. Dependencia en pom.xml.
