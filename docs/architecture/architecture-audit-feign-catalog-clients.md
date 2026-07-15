# Auditoría de Feign Clients — Consumo de GET /api/catalog

**Fecha:** 2026-07-15
**Auditor:** Java Architecture Auditor
**Alcance:** search-service, elending-service, y demás microservicios que consuman `catalog-service`

---

## Resumen Ejecutivo

Se detectaron **2 incumplimientos de tipo Arquitectura** en los clientes Feign que consumen `GET /api/catalog` del servicio `catalog-service`. Ambos clientes declaran el tipo de retorno como `List<T>` cuando el endpoint real devuelve `ResponseEntity<Page<T>>` (un objeto `Page` de Spring Data envuelto en `ResponseEntity`). Esto causará un **error de deserialización en tiempo de ejecución** porque Feign esperará un array JSON y recibirá un objeto con estructura `{ content: [...], pageable: {...}, totalElements: ... }`.

Ningún otro microservicio consume `GET /api/catalog` sin sub-ruta.

---

## Hallazgos

### [JAVA-ARCH-001] Tipo de retorno incorrecto en CatalogClient de search-service

- **Severidad:** Alta
- **Ubicación:** `search-service/src/main/java/com/silvio/search/client/CatalogClient.java` (Líneas 14-15)
- **Tipo de Incumplimiento:** Arquitectura

#### Código Identificado

```java
@FeignClient(name = "catalog-service")
public interface CatalogClient {

    // Obtener todos los libros del catálogo
    @GetMapping("/api/catalog")
    List<LibroCatalogDTO> obtenerTodos();
    ...
}
```

#### Justificación del Incumplimiento

El endpoint `GET /api/catalog` en `catalog-service` (`CatalogController.java`, línea 45) está definido como:

```java
@GetMapping
public ResponseEntity<Page<LibroResponseDTO>> obtenerTodos(
        @PageableDefault(size = 20, sort = "titulo") Pageable pageable) {
```

Retorna `ResponseEntity<Page<LibroResponseDTO>>`. La respuesta JSON tiene esta estructura:

```json
{
  "content": [{...}, ...],
  "pageable": { ... },
  "totalElements": 10,
  "totalPages": 1,
  "size": 20,
  "number": 0,
  ...
}
```

El FeignClient en `search-service` declara `List<LibroCatalogDTO>`, lo que hace que Feign intente deserializar el objeto `Page` directamente como si fuera un array JSON. Esto provocará un `JsonParseException` o un `CannotDeserializeException` en tiempo de ejecución al invocar `obtenerTodos()`.

Además, el método `obtenerTodos()` no acepta parámetros de paginación (`page`, `size`, `sort`), por lo que la llamada Feign no enviará ni siquiera parámetros opcionales. Aunque el servidor aplicaría los valores por defecto (`@PageableDefault`), el problema de deserialización persiste.

#### Instrucciones de Rectificación

1. Cambiar el tipo de retorno de `List<LibroCatalogDTO>` a `Page<LibroCatalogDTO>` (o al DTO equivalente `LibroCatalogDTO`).
2. Aceptar `Pageable pageable` como parámetro (o parámetros individuales `@RequestParam("page")`, `@RequestParam("size")`, `@RequestParam("sort")`) para permitir paginación controlada desde search-service.
3. Ajustar el `SearchService` (`search-service/src/main/java/com/silvio/search/service/SearchService.java`, línea 41) para iterar sobre `resultados.getContent()` en lugar de sobre la lista directamente, o convertir `Page<...>` a `List<...>` según la necesidad del negocio.
4. Actualizar el DTO `LibroCatalogDTO` si los campos difieren de `LibroResponseDTO`.

---

### [JAVA-ARCH-002] Tipo de retorno incorrecto en CatalogClient de elending-service

- **Severidad:** Alta
- **Ubicación:** `elending-service/src/main/java/com/silvio/elending/client/CatalogClient.java` (Líneas 22-24)
- **Tipo de Incumplimiento:** Arquitectura

#### Código Identificado

```java
@FeignClient(
    name = "catalog-service",
    fallbackFactory = CatalogClientFallbackFactory.class
)
public interface CatalogClient {

    // Listar todos los libros del catálogo
    @GetMapping("/api/catalog")
    List<LibroDTO> obtenerTodos();
    ...
}
```

#### Justificación del Incumplimiento

Misma desviación que JAVA-ARCH-001. El endpoint real `GET /api/catalog` retorna `ResponseEntity<Page<LibroResponseDTO>>`. El FeignClient declara `List<LibroDTO>`, lo que provocará un error de deserialización al recibir un objeto `Page` con estructura `{ content: [...], pageable: {...} }` en lugar de un array JSON plano `[{...}, {...}]`.

El `CatalogClient` en `elending-service` tiene un `CatalogClientFallbackFactory` configurado, pero el fallback no mitigará este error porque la excepción ocurre en la **deserialización de la respuesta exitosa**, no en un error de red o timeout. El circuito no se abriría para un error de este tipo; en su lugar, el método lanzará una `FeignException` de deserialización que el `GlobalExceptionHandler` debería capturar.

#### Instrucciones de Rectificación

1. Cambiar el tipo de retorno de `List<LibroDTO>` a `Page<LibroDTO>`.
2. Aceptar `Pageable pageable` como parámetro del método para controlar la paginación.
3. Actualizar los invocadores de `obtenerTodos()` en el servicio (`elending-service`) para usar `page.getContent()` y propagar la información de paginación si es necesaria.
4. Actualizar el `CatalogClientFallbackFactory` (`elending-service/src/main/java/com/silvio/elending/client/CatalogClientFallbackFactory.java`) para que el método `obtenerTodos()` en el fallback también retorne `Page<LibroDTO>` (o `Page.empty()`).

---

### [JAVA-ARCH-003] Ausencia de paginación en llamadas Feign a GET /api/catalog

- **Severidad:** Media
- **Ubicación:** Ambos archivos:
  - `search-service/src/main/java/com/silvio/search/client/CatalogClient.java` (Línea 14)
  - `elending-service/src/main/java/com/silvio/elending/client/CatalogClient.java` (Línea 23)
- **Tipo de Incumplimiento:** Arquitectura

#### Código Identificado

```java
// search-service
@GetMapping("/api/catalog")
List<LibroCatalogDTO> obtenerTodos();

// elending-service
@GetMapping("/api/catalog")
List<LibroDTO> obtenerTodos();
```

#### Justificación del Incumplimiento

El endpoint `GET /api/catalog` está diseñado para ser paginado (acepta `Pageable` con `@PageableDefault(size = 20, sort = "titulo")`). Sin embargo, ambos FeignClients invocan el endpoint sin enviar parámetros de paginación. Aunque el servidor aplica valores por defecto (size=20), esto significa que:

- Los consumidores no tienen control sobre el tamaño de página o el orden.
- Si el catálogo crece, recibir solo 20 libros puede no ser suficiente para la lógica de negocio de `search-service` (que sincroniza todo el catálogo) o `elending-service` (que necesita el listado completo para validaciones).
- No hay manejo de paginación (página 2, 3, ...), lo que limita la escalabilidad.

#### Instrucciones de Rectificación

1. Agregar `Pageable pageable` como parámetro en ambos métodos `obtenerTodos()`.
2. Alternativamente, si la intención es obtener **todos** los registros, el endpoint del servidor debería exponer un método sin paginación (ej. `/api/catalog/todos`) que retorne `List<T>`, o bien consumir el endpoint paginado iterando sobre todas las páginas desde el cliente.
3. Si se decide mantener la paginación, documentar claramente en el `@Operation` del controller de catalog-service qué tamaño de página se recomienda.

---

## Resumen de Hallazgos

| ID | Microservicio | Archivo | Línea | Endpoint | Tipo Retorno (Cliente) | Tipo Retorno (Servidor) | Severidad |
|---|---|---|---|---|---|---|---|
| JAVA-ARCH-001 | search-service | `client/CatalogClient.java` | 14-15 | `GET /api/catalog` | `List<LibroCatalogDTO>` | `ResponseEntity<Page<LibroResponseDTO>>` | Alta |
| JAVA-ARCH-002 | elending-service | `client/CatalogClient.java` | 22-24 | `GET /api/catalog` | `List<LibroDTO>` | `ResponseEntity<Page<LibroResponseDTO>>` | Alta |
| JAVA-ARCH-003 | Ambos | — | — | `GET /api/catalog` | Sin paginación | Paginado (`Pageable`) | Media |

---

## Notas Adicionales

- **CatalogClient en search-service** también define `buscar()` y `obtenerDisponibles()` con tipo `List<T>`. El endpoint `/api/catalog/buscar` efectivamente retorna `ResponseEntity<List<LibroResponseDTO>>` (línea 104 del controller) → correcto. El endpoint `/api/catalog/disponibles` también retorna `ResponseEntity<List<LibroResponseDTO>>` (línea 64 del controller) → correcto. La discrepancia solo afecta a `obtenerTodos()`.
- **No se encontraron otros Feign clients** que consuman `GET /api/catalog` en ningún otro microservicio del proyecto.
- Los demás Feign Clients auditados (`LicenseClient`, `SubscriptionClient`, `IdentityClient`, `LendingClient`, `IngestionClient`) apuntan a otros servicios y no presentan incidencias relacionadas con este endpoint.
