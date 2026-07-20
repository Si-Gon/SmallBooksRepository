## Última Actualización
- Fecha: 2026-07-20
- Pipeline: M-04 — validación de magic bytes en subirArchivo()

## Estado Actual del Servicio
- Clases principales:
  - `ArchivoLibro` — Entidad JPA que mapea la tabla `archivos_libros`. Contiene metadatos del archivo (nombre, formato, tamaño, ruta) y el contenido binario (LONGBLOB) cargado bajo demanda vía `@Basic(fetch = FetchType.LAZY)`.
  - `ArchivoLibroInfo` — Proyección cerrada de interfaz Spring Data que excluye el campo LONGBLOB `datos`. Usada en consultas de solo metadatos.
  - `ArchivoLibroRepository` — Repositorio Spring Data JPA con métodos `findByLibroId()` (entidad completa), `findInfoByLibroId()` (proyección sin blob) y `existsByLibroId()`.
  - `IngestionService` — Lógica de negocio: subir, obtener info, obtener bytes, eliminar archivos. Contiene `validarMagicBytes(byte[], String)` que verifica que los primeros 4 bytes del contenido coincidan con el formato declarado (PDF: `%PDF`, EPUB: firma ZIP).
  - `ArchivoLibroDTO` — DTO HATEOAS con los 6 campos públicos de metadatos (excluye `rutaOClave` y `datos`).
  - `DatabaseStorageService` / `LocalStorageService` — Implementaciones de `StorageService` para almacenamiento en DB (LONGBLOB) o disco local.
  - `IngestionController` — REST controller que expone los endpoints.
- Endpoints expuestos:
  - `POST /api/libros/{libroId}/archivo` — Sube un archivo (PDF/EPUB) asociado a un libro.
  - `GET /api/libros/{libroId}/archivo/info` — Obtiene metadatos del archivo (usa proyección, sin LONGBLOB).
  - `GET /api/libros/{libroId}/archivo` — Descarga el archivo completo (bytes).
  - `DELETE /api/libros/{libroId}/archivo` — Elimina el archivo.
- Dependencias externas:
  - MySQL (tabla `archivos_libros` con columna `datos` tipo `LONGBLOB`)
  - Micrometer Tracing (observabilidad)
- Cobertura de tests: ~100% línea en `IngestionService` y `DatabaseStorageService`; ~95% en `validarMagicBytes()` (todas las ramas: null/vacío, <4, =4 correcto, >4 correcto, >4 incorrecto para PDF y EPUB); 66 tests totales, 0 fallos

## Decisiones Técnicas
- **M-04: validarMagicBytes() — validación de contenido real del archivo** — Se agregó método privado `validarMagicBytes(byte[], String)` que lee los primeros 4+ bytes y los compara con las firmas esperadas (`PDF_MAGIC = 0x25 0x50 0x44 0x46 [%PDF]`, `EPUB_MAGIC = 0x50 0x4B 0x03 0x04 [ZIP]`). Se invoca después de `storageService.guardar()` y antes de `archivoRepository.save()`. Archivos null, vacíos o con menos de 4 bytes se rechazan con `FormatoNoPermitidoException`. Alternativa descartada: Apache Tika (dependencia externa innecesaria — 4 bytes bastan para PDF y EPUB).
- **M-07 Hotfix: Revertir @SecurityScheme a configuración programática** — Se eliminó `@SecurityScheme` annotation class-level (causaba `ArrayIndexOutOfBoundsException` en ASM scanner de Spring Boot 3.3.11). Reemplazado por configuración programática en `customOpenAPI()` usando modelos OpenAPI. SwaggerConfigTest migrado de static source scan a `@SpringBootTest(classes = SwaggerConfig.class, webEnvironment = NONE)`. Alternativa descartada: mantener anotación — incompatible con ASM 9.x de Spring Boot 3.3.11.
- `@Basic(fetch = FetchType.LAZY)` en campo `datos` (LONGBLOB) — Por defecto `@Lob` usa EAGER; se cambió a LAZY para evitar cargar el blob en cada consulta. Hibernate usa bytecode instrumentation para la carga perezosa.
- Proyección `ArchivoLibroInfo` — Se creó una proyección cerrada de interfaz con 7 campos (id, libroId, nombreArchivo, formato, tamanio, rutaOClave, fechaSubida) para que `obtenerInfo()` nunca toque la columna LONGBLOB. Alternativa descartada: `@EntityGraph` o `@Query` explícito (más verboso, misma eficacia).
- `obtenerBytes()` y `eliminar()` siguen usando `findByLibroId()` — Es correcto: necesitan la entidad completa para acceder a `rutaOClave` y al BLOB. La carga perezosa del blob es segura vía Hibernate.
- `rutaOClave` está en la proyección pero no en el DTO — Por diseño, es un detalle interno de infraestructura que no debe exponerse al cliente.
- **Z-01: ArchivoNoEncontradoException reemplazado por java.util.NoSuchElementException** — Se eliminó la clase zombie `ArchivoNoEncontradoException`. Los throws en `IngestionService` se reemplazaron por `NoSuchElementException`. Alternativa descartada: mantener la exception personalizada — no se lanzaba desde producción consistente.
- **M-10: @ExceptionHandler(NoSuchElementException.class) → 404 NOT_FOUND** — Se agregó handler en GlobalExceptionHandler para capturar `NoSuchElementException` (lanzado por `Optional.orElseThrow()`) y retornar 404 en lugar de 500. Sigue exactamente el patrón de subscription-service. Test unitario en GlobalExceptionHandlerTest verifica NoSuchElementException → 404. Alternativa descartada: usar ResponseEntityExceptionHandler — más complejo sin beneficio para un caso simple.

## Criterios de Aceptación Cumplidos
- Agregar `@Basic(fetch = FetchType.LAZY)` al campo `datos` → Ya estaba presente en `ArchivoLibro.java:42-45`. Verificado.
- Crear proyección `ArchivoLibroInfo` con 7 campos → Ya existía en `repository/ArchivoLibroInfo.java`. Verificado.
- Agregar `findInfoByLibroId(Long)` en el repositorio → Ya existía en `ArchivoLibroRepository.java:14`. Verificado.
- Refactorizar `obtenerInfo()` para usar la proyección → Ya estaba refactorizado en `IngestionService.java:78-86`. Verificado.
- Tests actualizados para verificar que `obtenerInfo()` usa la proyección y no `findByLibroId()` → 54 tests, 0 fallos. Verificado.
- **Z-01) Reemplazar ArchivoNoEncontradoException por NoSuchElementException en IngestionService** → `IngestionService` usa `NoSuchElementException` en lugar de `ArchivoNoEncontradoException`. Clase zombie eliminada. Tests de service y controller actualizados. Compilación verificada.
- **M-10) NoSuchElementException → 404 NOT_FOUND en GlobalExceptionHandler** → Handler `@ExceptionHandler(NoSuchElementException.class)` retorna 404 con mensaje descriptivo. Test `noSuchElement_debeRetornar404()` en GlobalExceptionHandlerTest verifica 404. Build: 57 tests PASS.
- **M-04) Validación de magic bytes en subirArchivo()** → Método privado `validarMagicBytes()` rechaza archivos cuyo contenido real no coincide con el formato declarado. PDF debe comenzar con `%PDF` (0x25 0x50 0x44 0x46). EPUB debe comenzar con firma ZIP (0x50 0x4B 0x03 0x04). Archivos vacíos o <4 bytes también rechazados. 5 tests positivos (PDF/EPUB válidos, exactamente 4 bytes), 5 tests negativos (contenido falso, vacío, <4 bytes, PNG renombrado). 20 tests en IngestionServiceTest, 66 totales, 0 fallos.

## Historial de Cambios
- 2026-07-20 — M-04: validarMagicBytes() agregado en IngestionService. Validación de contenido real (firmas %PDF y ZIP). 10 tests nuevos/actualizados (positivos, negativos, casos borde). 66 tests PASS.
- 2026-07-20 — M-10: @ExceptionHandler(NoSuchElementException.class) → 404 NOT_FOUND verificado. Handler y test ya implementados. 57 tests PASS.
- 2026-07-20 — Z-01: ArchivoNoEncontradoException eliminada, reemplazada por NoSuchElementException en IngestionService. Tests actualizados (service + controller). Compilación verificada.
- 2026-07-18 — M-07 Hotfix: ASM bug fix. @SecurityScheme eliminado de SwaggerConfig, reemplazado por configuración programática. SwaggerConfigTest migrado a @SpringBootTest. Verificado: 64 tests PASS (1 skip pre-existente), JaCoCo OK.
- 2026-07-17 — M-05: @SecurityScheme en SwaggerConfig. SwaggerConfigTest static scan. Import SecuritySchemeType corregido.
- 2026-07-15 — Fix crítico de rendimiento JPA: `@Basic(fetch = FetchType.LAZY)` en LONGBLOB, proyección `ArchivoLibroInfo`, `findInfoByLibroId()` en repositorio, `obtenerInfo()` refactorizado para usar proyección. Tests actualizados.
