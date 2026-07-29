## Última Actualización
- Fecha: 2026-07-29
- Pipeline: AC-01+AC-03 — Actuator + Trazabilidad Feign real

## Estado Actual del Servicio
- spring-boot-starter-actuator agregado. Expone /actuator/health.
- spring.cloud.openfeign.micrometer.enabled: true — Feign crea observaciones Micrometer.
- TracePropagationInterceptor: propaga headers B3 (X-B3-TraceId, X-B3-SpanId) en llamadas Feign a catalog-service.
- Clases principales:
  - `CatalogClient` — Feign client hacia catalog-service. `obtenerTodos()` retorna `Page<LibroCatalogDTO>` con parámetros page, size, sort. Circuit Breaker habilitado con `fallbackFactory`.
  - `CatalogClientFallbackFactory` — FallbackFactory para CatalogClient. Cuando catalog-service no responde, `obtenerTodos()` devuelve `Page.empty()`, `buscar()` y `obtenerDisponibles()` devuelven `Collections.emptyList()`.
  - `SearchService` — Capa de negocio. Inyecta `CatalogClient`, usa `page.getContent()` para extraer la lista paginada.
  - `SearchController` — REST controller con endpoints de búsqueda y listado.
  - `LibroCatalogDTO` — DTO que mapea la respuesta del Catalog Service (id, titulo, autor, isbn, editorial, anioPublicacion, idioma, genero, sinopsis, portadaUrl, disponible).
  - `SearchResultDTO` — DTO de respuesta enriquecido con HATEOAS links.
- Endpoints expuestos:
  - `GET /api/search` — Catálogo completo (consume catalog-service internamente con paginación page=0, size=100, sort=titulo,asc). Retorna `List<SearchResultDTO>`.
  - `GET /api/search/disponibles` — Solo libros disponibles (sin paginación, consume catalog-service).
  - `GET /api/search/buscar` — Búsqueda combinable por titulo, autor, genero. Todos opcionales (sin paginación, consume catalog-service).
- Dependencias externas: catalog-service (Feign), spring-data-commons (para Page), Micrometer Tracing (observabilidad), Resilience4j (Circuit Breaker)
- Cobertura de tests: 86 tests (CatalogClientFallbackFactoryTest: 16, Resilience4jConfigIntegrationTest: 7, más tests existentes). 0 fallos.

## Decisiones Técnicas
- **M-07 Hotfix: Revertir @SecurityScheme a configuración programática** — Se eliminó `@SecurityScheme` annotation class-level (causaba `ArrayIndexOutOfBoundsException` en ASM scanner de Spring Boot 3.3.11). Reemplazado por configuración programática en `customOpenAPI()` usando modelos OpenAPI. SwaggerConfigTest migrado de static source scan a `@SpringBootTest(classes = SwaggerConfig.class, webEnvironment = NONE)`. Alternativa descartada: mantener anotación — incompatible con ASM 9.x de Spring Boot 3.3.11.
- `@Transactional(readOnly = true)` agregado a todos los métodos read-only (`buscar()`, `buscarDisponibles()`, `obtenerTodos()`) — aunque el servicio solo usa Feign Clients (sin JPA directo), la anotación marca la intención de solo-lectura y habilita optimizaciones si en el futuro se agrega acceso a base de datos.
- FallbackFactory en lugar de `fallback` simple — permite loguear la causa exacta del error de conexión. Consistente con el patrón de elending-service.
- `spring.cloud.openfeign.circuitbreaker.enabled: true` — habilita el wrapper de Circuit Breaker de Resilience4j sobre cada `@FeignClient`.
- El nombre de instancia `catalog-service` en `resilience4j.circuitbreaker.instances` coincide exactamente con el `name` del `@FeignClient`.
- Configuración de Resilience4j idéntica a elending-service: sliding-window-size=10, failure-rate-threshold=50%, wait-duration-in-open-state=30s, timeout global de 5s.

## Criterios de Aceptación Cumplidos
- Agregar `@Transactional(readOnly = true)` a métodos read-only de search-service → Implementado en `buscar()`, `buscarDisponibles()`, `obtenerTodos()`. Import agregado. Compilación verificada.
- Agregar FallbackFactory para CatalogClient (fallback retorna `Page.empty()` para `obtenerTodos`, `Collections.emptyList()` para `buscar` y `obtenerDisponibles`) → Implementado en `CatalogClientFallbackFactory`.
- Agregar `spring.cloud.openfeign.circuitbreaker.enabled: true` en application.yml → Implementado.
- Agregar Resilience4j circuitbreaker + timelimiter config en application.yml → Implementado con valores idénticos a elending-service.
- Agregar dependencia `spring-cloud-starter-circuitbreaker-resilience4j` en pom.xml → Implementado.

## Historial de Cambios
- 2026-07-20 — Z-01: Eliminada ErrorConsultaCatalogoException (zombie pura, 0 referencias). Sin cambios en código — nunca fue instanciada.
- 2026-07-18 — M-07 Hotfix: ASM bug fix. @SecurityScheme eliminado de SwaggerConfig, reemplazado por configuración programática. SwaggerConfigTest migrado a @SpringBootTest. Verificado: 88 tests PASS, JaCoCo OK.
- 2026-07-17 — M-05: @SecurityScheme en SwaggerConfig. SwaggerConfigTest static scan. Import SecuritySchemeType corregido.
- 2026-07-15 — Feign Client CatalogClient actualizado a `Page<LibroCatalogDTO>` con parámetros page/size/sort. SearchService usa `page.getContent()`. Tests actualizados.
- 2026-07-15 — Agregado `@Transactional(readOnly = true)` a `buscar()`, `buscarDisponibles()`, `obtenerTodos()` para optimización de rendimiento JPA y consistencia con el código base.
- 2026-07-16 — Agregado Circuit Breaker + FallbackFactory a CatalogClient siguiendo el patrón de elending-service. Resilience4j config en application.yml. Dependencia en pom.xml.
- 2026-07-29 — AC-01: Agregado spring-boot-starter-actuator + management.endpoints.web.exposure.include: health.
- 2026-07-29 — AC-03: Agregado spring.cloud.openfeign.micrometer.enabled: true + TracePropagationInterceptor. Trazabilidad fin-a-fin via Feign.
