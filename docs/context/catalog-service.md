## Última Actualización
- Fecha: 2026-07-15
- Pipeline: Agregar índices de base de datos a tabla `libros` para optimizar consultas JPA del catálogo

## Estado Actual del Servicio
- Clases principales:
  - `Libro` (com.silvio.catalog.model) — Entidad JPA que mapea la tabla `libros`. Contiene: id, titulo, autor, isbn, editorial, anioPublicacion, idioma, genero, sinopsis, portadaUrl, disponible.
  - `LibroRepository` (com.silvio.catalog.repository) — Interface Spring Data JPA con métodos: findByTituloContainingIgnoreCase, findByAutorContainingIgnoreCase, findByGeneroIgnoreCase, findByDisponibleTrue, findByIsbn, buscarCombinado (JPQL dinámico).
  - `CatalogController` — REST controller para operaciones CRUD del catálogo.
  - `CatalogService` — Capa de negocio del catálogo.
- Endpoints expuestos: (por determinar — no modificado en este pipeline)
- Dependencias externas: MySQL (base de datos), Flyway (migraciones), E-Lending Service (consume disponibilidad vía Feign)
- Cobertura de tests: ~100% (151 tests, 0 fallos, 0 errores) — migración V3 no afecta tests existentes que usan H2 con flyway.enabled=false

## Decisiones Técnicas
- Índices B-tree (no FULLTEXT) — porque los métodos del repositorio usan `LIKE %text%` con `ContainingIgnoreCase`, que no se beneficia del índice B-tree para el prefijo `%`. Sin embargo, ayudan en `buscarCombinado` con parámetros exactos y en consultas de disponibilidad.
- Índice compuesto `(disponible, genero)` con `disponible` primero — por su alta selectividad (boolean) y porque es el filtro más común desde E-Lending.
- Índices gestionados desde Flyway, no desde anotaciones JPA — para mantener control explícito de la versión y orden de creación en todos los entornos.
- No se usó `@Table(indexes = {...})` en la entidad — se prefirió Flyway para consistencia con V1 y V2, y porque los tests con H2 no replicarían los índices correctamente.

## Criterios de Aceptación Cumplidos
- Agregar índices a columnas `titulo`, `autor`, `genero`, `disponible` → Se crearon 4 índices simples: idx_libros_titulo, idx_libros_autor, idx_libros_genero, idx_libros_disponible
- Agregar índice compuesto en `(disponible, genero)` → Se creó idx_libros_disponible_genero
- Comentarios en español consistentes con V1 y V2 → Comentarios referencian los métodos del repositorio y explican el propósito de cada índice
- Migración Flyway V3 ejecutable después de V1 y V2 → Numeración secuencial correcta, sintaxis SQL estándar MySQL

## Historial de Cambios
- 2026-07-15 — Creación de V3__agregar_indices_tabla_libros.sql con 5 índices sobre tabla `libros` para optimizar consultas del catálogo
