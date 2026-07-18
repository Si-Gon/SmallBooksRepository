## Última Actualización
- Fecha: 2026-07-18
- Pipeline: M-02 — Agregar RBAC a catalog-service (solo ROLE_ADMIN puede crear/actualizar/eliminar libros)

## Estado Actual del Servicio
- Clases principales:
  - `Libro` (com.silvio.catalog.model) — Entidad JPA que mapea la tabla `libros`. Contiene: id, titulo, autor, isbn, editorial, anioPublicacion, idioma, genero, sinopsis, portadaUrl, disponible.
  - `LibroRepository` (com.silvio.catalog.repository) — Interface Spring Data JPA. Sus métodos devuelven `List<Libro>` excepto `findAll(Pageable)` que retorna `Page<Libro>`.
  - `CatalogController` — REST controller para operaciones CRUD del catálogo. Endpoint `GET /api/catalog` acepta paginación vía `@PageableDefault(size=20, sort="titulo")`. Métodos de escritura protegidos con `@PreAuthorize("hasRole('ADMIN')")`.
  - `CatalogService` — Capa de negocio del catálogo. `obtenerTodos(Pageable)` retorna `Page<LibroResponseDTO>` usando `findAll(pageable).map()`. Métodos de escritura (`agregar`, `actualizar`, `cambiarDisponibilidad`, `eliminar`) anotados con `@Transactional` para garantizar atomicidad.
  - `JwtAuthenticationFilter` (com.silvio.catalog.security) — Filtro `OncePerRequestFilter` que lee headers `X-User-Roles` y `X-User-Id` propagados por Gateway. Convierte roles a `SimpleGrantedAuthority` y establece `UsernamePasswordAuthenticationToken` en `SecurityContextHolder`. Limpia contexto en `finally`.
  - `SecurityConfig` (com.silvio.catalog.config) — Configuración Spring Security con `@EnableWebSecurity` + `@EnableMethodSecurity`. Protege endpoints de escritura con `hasRole('ADMIN')`. GET y `/actuator/health` son `permitAll()`. Sesión STATELESS. Filtro JWT antes de `UsernamePasswordAuthenticationFilter`.
- Endpoints expuestos:
  - `GET /api/catalog` — Lista paginada de libros. permitAll(). Parámetros: `page`, `size`, `sort`. Default: size=20, sort=titulo. Retorna `Page<LibroResponseDTO>`.
  - `GET /api/catalog/disponibles` — Lista libros disponibles. permitAll().
  - `GET /api/catalog/{id}` — Obtiene libro por ID. permitAll().
  - `GET /api/catalog/buscar` — Búsqueda por título, autor o género. permitAll().
  - `POST /api/catalog` — Crea un nuevo libro. Solo ROLE_ADMIN (SecurityConfig + @PreAuthorize).
  - `PUT /api/catalog/{id}` — Actualiza un libro existente. Solo ROLE_ADMIN.
  - `PATCH /api/catalog/{id}/disponibilidad` — Cambia disponibilidad del libro. Solo ROLE_ADMIN.
  - `DELETE /api/catalog/{id}` — Elimina un libro. Solo ROLE_ADMIN.
- Dependencias externas: MySQL (base de datos), Flyway (migraciones), E-Lending Service (consume disponibilidad vía Feign)
- Dependencias de seguridad: spring-boot-starter-security, spring-security-test (test)
- Cobertura de tests: ~95% (188 tests totales: 43 service + controller web + 8 filter + 13 integración seguridad, 0 fallos, 0 errores)

## Decisiones Técnicas
- **M-02: JwtAuthenticationFilter basado en X-User-Roles (NO en Authorization)** — A diferencia de elending-service que preserva el JWT para Feign, catalog-service lee los headers `X-User-Roles` y `X-User-Id` propagados por Gateway. El Gateway ya validó el JWT; catalog-service solo traduce headers a autoridades Spring Security. Alternativa descartada: copiar el filtro de elending-service — no serviría porque catalog-service no necesita reenviar el JWT a otros servicios.
- **M-02: Seguridad en dos capas (SecurityConfig + @PreAuthorize)** — SecurityConfig protege las rutas HTTP a nivel de infraestructura, y `@PreAuthorize` en el controller actúa como defensa en profundidad. Si alguien modifica SecurityConfig y olvida proteger una ruta, `@PreAuthorize` aún bloquea el endpoint.
- **M-02: TestSecurityConfig con excludeFilters** — `@WebMvcTest` existente en CatalogControllerTest usa `excludeFilters` para excluir `SecurityConfig.class` y `JwtAuthenticationFilter.class` + `@TestConfiguration` interna con `permitAll()`. Esto evita que los 42 tests existentes requieran autenticación. Alternativa descartada: `@WithMockUser` en cada test — hubiera requerido modificar 42 tests.
- **M-02: @EnableMethodSecurity es necesario** — Sin esta anotación en SecurityConfig, `@PreAuthorize` en el controller no tiene efecto. Spring Security 6 requiere habilitación explícita de method-security.
- **M-07 Hotfix: Revertir @SecurityScheme a configuración programática** — Se eliminó `@SecurityScheme` annotation class-level (causaba `ArrayIndexOutOfBoundsException` en ASM scanner de Spring Boot 3.3.11, rompiendo `@WebMvcTest`, `@SpringBootTest` y JaCoCo). Reemplazado por configuración programática en `customOpenAPI()` usando modelos OpenAPI. Alternativa descartada: mantener anotación `@SecurityScheme` — incompatible con ASM 9.x de Spring Boot 3.3.11.
- **M-07 Hotfix: SwaggerConfigTest Spring Context** — Reemplazado el static source scan por `@SpringBootTest(classes = SwaggerConfig.class, webEnvironment = NONE)` con `@ActiveProfiles("test")`. Verifica el bean OpenAPI real.
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
- **M-02) Agregar spring-boot-starter-security a catalog-service** — Ya estaba en pom.xml, no se requirió cambio.
- **M-02) Crear JwtAuthenticationFilter** — Implementado: lee X-User-Roles, split por coma, null/blank → emptyList, mapea a SimpleGrantedAuthority. Lee X-User-Id como principal. clearContext() en finally. Comentarios en español.
- **M-02) Crear SecurityConfig** — Implementado: permitAll() para GET /api/catalog/** y /actuator/health. hasRole('ADMIN') para POST/PUT/PATCH/DELETE. Filtro JWT antes de UsernamePasswordAuthenticationFilter. @EnableMethodSecurity.
- **M-02) Agregar @PreAuthorize en CatalogController** — Implementado en agregar(), actualizar(), cambiarDisponibilidad(), eliminar(). Sin @Transactional añadido.
- **M-02) Tests del filtro** — 8 tests: ROLE_ADMIN, ROLE_USER, sin header, header blank, múltiples roles, espacios extra, comas vacías, limpieza SecurityContext.
- **M-02) Tests de integración RBAC** — 13 tests: POST/PUT/PATCH/DELETE con ROLE_USER→403, con ROLE_ADMIN→201/200/200/204, GET sin auth→200, múltiples roles incluyendo ADMIN→201.
- **M-02) No modificar tests existentes fuera de catalog-service** — CatalogControllerTest usa excludeFilters + TestSecurityConfig, ningún test existente fue modificado.
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
- 2026-07-18 — M-02: RBAC implementado. JwtAuthenticationFilter creado (X-User-Roles → SimpleGrantedAuthority). SecurityConfig con hasRole('ADMIN') en POST/PUT/PATCH/DELETE. @PreAuthorize en controller. 8 tests filter + 13 tests integración. 188 tests totales, BUILD SUCCESS.
- 2026-07-18 — M-07 Hotfix: ASM bug fix. @SecurityScheme eliminado de SwaggerConfig, reemplazado por configuración programática de modelos OpenAPI. SwaggerConfigTest migrado de static source scan a @SpringBootTest. Verificado: 167 tests PASS, JaCoCo OK.
- 2026-07-17 — M-05: @SecurityScheme agregado en SwaggerConfig. Import SecuritySchemeType corregido a .enums. SwaggerConfigTest static scan. @SecurityRequirement en @Operation de CatalogController.
- 2026-07-15 — Creación de V3__agregar_indices_tabla_libros.sql con 5 índices sobre tabla `libros` para optimizar consultas del catálogo
- 2026-07-15 19:38 — Paginación en `GET /api/catalog`: `obtenerTodos()` refactorizado para aceptar `Pageable`, controller con `@PageableDefault(size=20, sort="titulo")`, Swagger actualizado, +7 tests de paginación agregados
- 2026-07-17 — H-03: @Transactional agregado a 4 métodos de escritura (agregar, actualizar, cambiarDisponibilidad, eliminar). +4 tests de rollback en CatalogServiceTest. Total tests: 43, 0 fallos.
