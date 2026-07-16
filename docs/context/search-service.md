## Última Actualización
- Fecha: 2026-07-15
- Pipeline: JPA performance optimization — @Transactional(readOnly=true) additions

## Estado Actual del Servicio
- Clases principales:
  - `CatalogClient` (com.silvio.search.client) — Feign client hacia catalog-service. `obtenerTodos()` retorna `Page<LibroCatalogDTO>` con parámetros page, size, sort.
  - `SearchService` — Capa de negocio. Inyecta `CatalogClient`, usa `page.getContent()` para extraer la lista paginada.
  - `SearchController` — REST controller con endpoints de búsqueda y listado.
  - `LibroCatalogDTO` (com.silvio.search.dto) — DTO que mapea la respuesta del Catalog Service (id, titulo, autor, isbn, editorial, anioPublicacion, idioma, genero, sinopsis, portadaUrl, disponible).
  - `SearchResultDTO` (com.silvio.search.dto) — DTO de respuesta enriquecido con HATEOAS links.
- Endpoints expuestos:
  - `GET /api/search` — Catálogo completo (consume catalog-service internamente con paginación page=0, size=100, sort=titulo,asc). Retorna `List<SearchResultDTO>`.
  - `GET /api/search/disponibles` — Solo libros disponibles (sin paginación, consume catalog-service).
  - `GET /api/search/buscar` — Búsqueda combinable por titulo, autor, genero. Todos opcionales (sin paginación, consume catalog-service).
- Dependencias externas: catalog-service (Feign), spring-data-commons (para Page), Micrometer Tracing (observabilidad)
- Cobertura de tests: 8 clases de test (SearchServiceTest, SearchControllerTest, CatalogClientPageDeserializationTest, etc.)

## Decisiones Técnicas
- `@Transactional(readOnly = true)` agregado a todos los métodos read-only (`buscar()`, `buscarDisponibles()`, `obtenerTodos()`) — aunque el servicio solo usa Feign Clients (sin JPA directo), la anotación marca la intención de solo-lectura y habilita optimizaciones si en el futuro se agrega acceso a base de datos.

## Criterios de Aceptación Cumplidos
- Agregar `@Transactional(readOnly = true)` a métodos read-only de search-service → Implementado en `buscar()`, `buscarDisponibles()`, `obtenerTodos()`. Import agregado. Compilación verificada.

## Historial de Cambios
- 2026-07-15 — Feign Client CatalogClient actualizado a `Page<LibroCatalogDTO>` con parámetros page/size/sort. SearchService usa `page.getContent()`. Tests actualizados.
- 2026-07-15 — Agregado `@Transactional(readOnly = true)` a `buscar()`, `buscarDisponibles()`, `obtenerTodos()` para optimización de rendimiento JPA y consistencia con el código base.
