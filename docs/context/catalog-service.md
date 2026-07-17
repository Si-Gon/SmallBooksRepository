## Última Actualización
- Fecha: 2026-07-17
- Pipeline: Fix H-03: @Transactional en métodos de escritura de CatalogService

## Estado Actual del Servicio
- Clases principales:
  - `Libro` (com.silvio.catalog.model) — Entidad JPA que mapea la tabla `libros`. Contiene: id, titulo, autor, isbn, editorial, anioPublicacion, idioma, genero, sinopsis, portadaUrl, disponible.
  - `LibroRepository` (com.silvio.catalog.repository) — Interface Spring Data JPA. Sus métodos devuelven `List<Libro>` excepto `findAll(Pageable)` que retorna `Page<Libro>`.
  - `CatalogController` — REST controller para operaciones CRUD del catálogo. Endpoint `GET /api/catalog` acepta paginación vía `@PageableDefault(size=20, sort="titulo")`.
  - `CatalogService` — Capa de negocio del catálogo. `obtenerTodos(Pageable)` retorna `Page<LibroResponseDTO>` usando `findAll(pageable).map()`. Métodos de escritura (`agregar`, `actualizar`, `cambiarDisponibilidad`, `eliminar`) anotados con `@Transactional` para garantizar atomicidad.
- Endpoints expuestos:
  - `GET /api/catalog` — Lista paginada de libros. Parámetros: `page`, `size`, `sort`. Default: size=20, sort=titulo. Retorna `Page<LibroResponseDTO>`.
  - `GET /api/catalog/disponibles` — Lista libros disponibles (sin paginación).
  - `GET /api/catalog/{id}` — Obtiene libro por ID.
  - `GET /api/catalog/buscar` — Búsqueda por título, autor o género (sin paginación).
  - `POST /api/catalog` — Crea un nuevo libro.
  - `PUT /api/catalog/{id}` — Actualiza un libro existente.
  - `PATCH /api/catalog/{id}/disponibilidad` — Cambia disponibilidad del libro.
  - `DELETE /api/catalog/{id}` — Elimina un libro.
- Dependencias externas: MySQL (base de datos), Flyway (migraciones), E-Lending Service (consume disponibilidad vía Feign)
- Cobertura de tests: ~95% (43 tests service + tests controller, 0 fallos, 0 errores)

## Decisiones Técnicas
- Índices B-tree (no FULLTEXT) — porque los métodos del repositorio usan `LIKE %text%` con `ContainingIgnoreCase`, que no se beneficia del índice B-tree para el prefijo `%`. Sin embargo, ayudan en `buscarCombinado` con parámetros exactos y en consultas de disponibilidad.
- Índice compuesto `(disponible, genero)` con `disponible` primero — por su alta selectividad (boolean) y porque es el filtro más común desde E-Lending.
- Índices gestionados desde Flyway, no desde anotaciones JPA — para mantener control explícito de la versión y orden de creación en todos los entornos.
- No se usó `@Table(indexes = {...})` en la entidad — se prefirió Flyway para consistencia con V1 y V2, y porque los tests con H2 no replicarían los índices correctamente.
- `Pageable` como parámetro en `obtenerTodos()` en lugar de crear un wrapper propio — Spring Data Web Support convierte automáticamente los parámetros `page`, `size`, `sort` de la request en un `Pageable`, eliminando necesidad de parsing manual.
- `@PageableDefault(size=20, sort="titulo")` para evitar que clientes mal configurados traigan todos los registros — si no se envía paginación, el default es 20 elementos ordenados por título.
- HATEOAS links en `obtenerPorId()`, `agregar()` y `actualizar()` usan `Pageable.unpaged()` — Spring HATEOAS solo necesita la firma del método para generar la URL, no ejecuta la consulta real.
- Los endpoints `/disponibles` y `/buscar` no se migraron a paginación — no usan `findAll()` y no estaban en el alcance del pipeline.
- **H-03: @Transactional en métodos de escritura** — Se agregó `@Transactional` (sin `readOnly`) a los 4 métodos de escritura: `agregar()`, `actualizar()`, `cambiarDisponibilidad()`, `eliminar()`. Import `org.springframework.transaction.annotation.Transactional` ya existía en L14, no se duplicó. Métodos de lectura (`obtenerTodos`, `obtenerDisponibles`, `obtenerPorId`, `buscar`) ya tenían `@Transactional(readOnly = true)` y no se modificaron. Alternativa descartada: usar `@Transactional` en la clase completa — afectaría métodos de lectura innecesariamente.

## Criterios de Aceptación Cumplidos
- Agregar índices a columnas `titulo`, `autor`, `genero`, `disponible` → Se crearon 4 índices simples: idx_libros_titulo, idx_libros_autor, idx_libros_genero, idx_libros_disponible
- Agregar índice compuesto en `(disponible, genero)` → Se creó idx_libros_disponible_genero
- Comentarios en español consistentes con V1 y V2 → Comentarios referencian los métodos del repositorio y explican el propósito de cada índice
- Migración Flyway V3 ejecutable después de V1 y V2 → Numeración secuencial correcta, sintaxis SQL estándar MySQL
- Refactorizar `CatalogService.obtenerTodos()` para aceptar `Pageable` y retornar `Page<LibroResponseDTO>` → Implementado con `findAll(pageable).map(this::mapearADto)`
- Actualizar `CatalogController.obtenerTodos()` con `@PageableDefault(size=20, sort="titulo")` retornando `ResponseEntity<Page<LibroResponseDTO>>` → Implementado con metadatos de paginación y HATEOAS links en contenido
- Actualizar Swagger `@ApiResponse` para reflejar respuesta paginada → Descripción actualizada a "Lista paginada obtenida exitosamente"
- Actualizar tests para usar `Pageable.unpaged()` o `PageRequest.of()` → Tests existentes actualizados y 7 nuevos tests de paginación agregados (4 en service, 3 en controller)
- **H-03)** Agregar `@Transactional` a métodos de escritura en CatalogService → Implementado en `agregar()`, `actualizar()`, `cambiarDisponibilidad()`, `eliminar()`. Import existente no duplicado. Tests de rollback agregados (verifican propagación de RuntimeException).

## Historial de Cambios
- 2026-07-15 — Creación de V3__agregar_indices_tabla_libros.sql con 5 índices sobre tabla `libros` para optimizar consultas del catálogo
- 2026-07-15 19:38 — Paginación en `GET /api/catalog`: `obtenerTodos()` refactorizado para aceptar `Pageable`, controller con `@PageableDefault(size=20, sort="titulo")`, Swagger actualizado, +7 tests de paginación agregados
- 2026-07-17 — H-03: @Transactional agregado a 4 métodos de escritura (agregar, actualizar, cambiarDisponibilidad, eliminar). +4 tests de rollback en CatalogServiceTest. Total tests: 43, 0 fallos.
