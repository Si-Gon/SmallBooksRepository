# Auditoría de Controladores REST — Validación y Manejo de Errores

**Fecha:** 2026-07-15
**Alcance:** 11 controladores REST, 10 GlobalExceptionHandlers, 7 DTOs de entrada
**Auditor:** Java Architecture Auditor

---

## Resumen Ejecutivo

| Dominio | Estado |
|---|---|
| `@Valid` en `@RequestBody` | ✅ Correcto — todos los controladores lo usan |
| Bean Validation en DTOs de entrada | ✅ Correcto — todos los DTOs tienen anotaciones |
| `@PathVariable` sin validación explícita | ⚠️ No usan `@Validated` ni constraint annotations |
| GlobalExceptionHandler — fuga de mensajes | ❌ **CRÍTICO** — los 10 handlers exponen `ex.getMessage()` al cliente |
| FeignException — fuga de detalles downstream | ❌ **CRÍTICO** — 4 servicios exponen el mensaje Feign completo |
| Handlers ausentes para excepciones MVC comunes | ❌ **ALTO** — 0 de 10 handlers cubren `MethodArgumentTypeMismatch`, `HttpMessageNotReadable`, etc. |

---

## [JAVA-CONTROLLER-001] RuntimeException catch-all expone mensajes de excepción al cliente

- **Severidad:** Alta
- **Ubicación:** Los 10 archivos `GlobalExceptionHandler.java` de todos los módulos (analytics-service, catalog-service, content-service, elending-service, identity-services, ingestion-service, license-service, notification-service, search-service, subscription-service)
- **Tipo de Incumplimiento:** Calidad / Seguridad

### Código Identificado

```java
// Patrón IDÉNTICO en los 10 GlobalExceptionHandler:
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<Map<String, String>> manejarRuntimeException(
        RuntimeException ex) {
    Map<String, String> error = new HashMap<>();
    error.put("error", ex.getMessage());        // <-- FUGA: mensaje crudo al cliente
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
}
```

### Justificación del Incumplimiento

El mensaje de una excepción (`ex.getMessage()`) NO está diseñado para consumo externo. Para errores no contemplados por handlers específicos, el mensaje puede contener:

- Trazas internas de Spring (`Failed to convert value of type 'java.lang.String' to required type 'java.lang.Long'`)
- Fragmentos de StackTrace si el mensaje excepcionalmente los incluye
- Detalles de la implementación interna (nombres de clases, tipos de datos, configuraciones)
- Información de ruta de archivos del servidor
- Mensajes de error de base de datos (SQL syntax, constraint names)

Esto viola el principio de **Fail Safe** — nunca exponer detalles internos al cliente.

### Instrucciones de Rectificación

Reemplazar en todos los `GlobalExceptionHandler.java`:

```java
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<Map<String, Object>> manejarRuntimeException(
        RuntimeException ex) {
    // Loggear la traza completa para diagnóstico interno
    log.error("Error interno no manejado: {}", ex.getMessage(), ex);
    
    Map<String, Object> error = new HashMap<>();
    error.put("error", "Error interno del servidor");
    error.put("codigo", "ERR-500");
    // Opcional: incluir UUID de correlación para trazabilidad
    error.put("traceId", /* obtener traceId del contexto MDC */);
    
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
}
```

Agregar el logger en cada handler:

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// en la clase:
private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
```

---

## [JAVA-CONTROLLER-002] FeignException handler expone mensaje completo del error downstream

- **Severidad:** Alta
- **Ubicación:**
  - `analytics-service/src/main/java/com/silvio/analytics/exception/GlobalExceptionHandler.java` (Líneas 32-43)
  - `content-service/src/main/java/com/silvio/content/exception/GlobalExceptionHandler.java` (Líneas 40-52)
  - `elending-service/src/main/java/com/silvio/elending/exception/GlobalExceptionHandler.java` (Líneas 28-40)
  - `search-service/src/main/java/com/silvio/search/exception/GlobalExceptionHandler.java` (Líneas 23-36)
- **Tipo de Incumplimiento:** Seguridad / Calidad

### Código Identificado

```java
@ExceptionHandler(FeignException.class)
public ResponseEntity<Map<String, String>> manejarFeignException(
        FeignException ex) {
    Map<String, String> error = new HashMap<>();
    error.put("error", "Error de comunicación con servicio externo");
    error.put("detalle", ex.getMessage());   // <-- FUGA: mensaje completo Feign
    int status = ex.status();
    if (status <= 0) {
        status = HttpStatus.SERVICE_UNAVAILABLE.value();
    }
    return ResponseEntity.status(status).body(error);
}
```

### Justificación del Incumplimiento

`FeignException.getMessage()` puede contener:

- El body completo de la respuesta HTTP del servicio downstream (incluyendo mensajes de error internos, stacktraces, nombres de servidores)
- La URL completa del endpoint llamado (incluyendo paths internos)
- Headers de la respuesta HTTP
- Códigos de estado originales

Esta información expone la topología interna de microservicios y posiblemente datos sensibles del sistema downstream.

### Instrucciones de Rectificación

```java
@ExceptionHandler(FeignException.class)
public ResponseEntity<Map<String, String>> manejarFeignException(
        FeignException ex) {
    log.error("Error de comunicación Feign: status={}, url={}, mensaje={}",
            ex.status(), ex.request() != null ? ex.request().url() : "N/A", ex.getMessage(), ex);
    
    Map<String, String> error = new HashMap<>();
    error.put("error", "Error de comunicación con servicio externo");
    // NO incluir ex.getMessage() como detalle
    error.put("codigo", "ERR-503");
    
    int status = ex.status();
    if (status <= 0) {
        status = HttpStatus.SERVICE_UNAVAILABLE.value();
    }
    return ResponseEntity.status(status).body(error);
}
```

---

## [JAVA-CONTROLLER-003] Ausencia de handlers para MethodArgumentTypeMismatchException en todos los módulos

- **Severidad:** Media
- **Ubicación:** Todos los 10 archivos `GlobalExceptionHandler.java` (ninguno maneja esta excepción)
- **Tipo de Incumplimiento:** Calidad

### Código Identificado

Las siguientes rutas aceptan `@PathVariable` de tipo `Long` y lanzarían `MethodArgumentTypeMismatchException` si se recibe un valor no numérico:

| Controlador | Ruta | PathVariable |
|---|---|---|
| `CatalogController.java:78` | `GET /api/catalog/{id}` | `Long id` |
| `CatalogController.java:138` | `PUT /api/catalog/{id}` | `Long id` |
| `CatalogController.java:160` | `PATCH /api/catalog/{id}/disponibilidad` | `Long id` |
| `CatalogController.java:176` | `DELETE /api/catalog/{id}` | `Long id` |
| `ContentController.java:37` | `GET /api/content/{libroId}` | `Long libroId` |
| `IngestionController.java:43` | `POST /api/ingestion/upload/{libroId}` | `Long libroId` |
| `IngestionController.java:69` | `GET /api/ingestion/{libroId}` | `Long libroId` |
| `IngestionController.java:92` | `GET /api/ingestion/{libroId}/bytes` | `Long libroId` |
| `IngestionController.java:110` | `DELETE /api/ingestion/{libroId}` | `Long libroId` |
| `LicenseController.java:61` | `GET /api/licenses/{libroId}` | `Long libroId` |
| `LicenseController.java:107` | `PUT /api/licenses/{libroId}` | `Long libroId` |
| `LicenseController.java:130` | `PUT /api/licenses/{libroId}/prestar` | `Long libroId` |
| `LicenseController.java:152` | `PUT /api/licenses/{libroId}/devolver` | `Long libroId` |
| `NotificacionController.java:105` | `PATCH /api/notifications/{id}/leer` | `Long id` |

### Justificación del Incumplimiento

Al enviar un valor no numérico (ej. `GET /api/catalog/abc`), Spring lanza `MethodArgumentTypeMismatchException` que **no tiene handler específico** en ningún `GlobalExceptionHandler`. Cae al catch-all `RuntimeException` que expone:

```json
{
  "error": "Failed to convert value of type 'java.lang.String' to required type 'java.lang.Long'; 
            nested exception is java.lang.NumberFormatException: For input string: \"abc\""
}
```

Esto revela tipos internos de Java (`java.lang.Long`), el nombre del parámetro, y el valor intentado.

### Instrucciones de Rectificación

Agregar en todos los `GlobalExceptionHandler.java`:

```java
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@ExceptionHandler(MethodArgumentTypeMismatchException.class)
public ResponseEntity<Map<String, String>> manejarTipoArgumentoInvalido(
        MethodArgumentTypeMismatchException ex) {
    log.warn("Tipo de argumento inválido para parámetro '{}': se esperaba {} pero se recibió '{}'",
            ex.getName(), ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "?",
            ex.getValue());
    
    Map<String, String> error = new HashMap<>();
    error.put("error", "El valor proporcionado para '" + ex.getName() + "' no es válido");
    error.put("codigo", "ERR-400");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
}
```

---

## [JAVA-CONTROLLER-004] Ausencia de handler para HttpMessageNotReadableException en módulos con @RequestBody

- **Severidad:** Media
- **Ubicación:** Los 6 módulos que exponen endpoints con `@RequestBody` y no manejan esta excepción:
  - `catalog-service/exception/GlobalExceptionHandler.java`
  - `elending-service/exception/GlobalExceptionHandler.java`
  - `identity-services/exception/GlobalExceptionHandler.java`
  - `license-service/exception/GlobalExceptionHandler.java`
  - `notification-service/exception/GlobalExceptionHandler.java`
  - `subscription-service/exception/GlobalExceptionHandler.java`
- **Tipo de Incumplimiento:** Calidad

### Código Identificado

Al enviar JSON malformado a cualquier endpoint `@PostMapping` o `@PutMapping`, se lanza `HttpMessageNotReadableException`. Ningún handler la captura específicamente; cae al `RuntimeException` catch-all.

### Justificación del Incumplimiento

`HttpMessageNotReadableException.getMessage()` contiene detalles de parsing JSON como:
- `"JSON parse error: Cannot deserialize value of type \`java.lang.Long\` from String \"abc\""`
- `"Required request body is missing"`

Estos detalles pueden revelar información sobre la estructura esperada del DTO (tipos de campos, nombres de propiedades).

### Instrucciones de Rectificación

Agregar en los 6 módulos:

```java
import org.springframework.http.converter.HttpMessageNotReadableException;

@ExceptionHandler(HttpMessageNotReadableException.class)
public ResponseEntity<Map<String, String>> manejarMensajeNoLegible(
        HttpMessageNotReadableException ex) {
    log.warn("Cuerpo de solicitud malformado: {}", ex.getMessage());
    
    Map<String, String> error = new HashMap<>();
    error.put("error", "El cuerpo de la solicitud contiene datos inválidos o está mal formado");
    error.put("codigo", "ERR-400");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
}
```

---

## [JAVA-CONTROLLER-005] Ausencia de handler para MissingServletRequestParameterException

- **Severidad:** Media
- **Ubicación:** Todos los `GlobalExceptionHandler.java`. Afecta especialmente:
  - `catalog-service/controller/CatalogController.java:162` — `@RequestParam Boolean disponible` (required por defecto)
  - `catalog-service/controller/CatalogController.java:99-103` — `@RequestParam(required = false)` aunque no required, si se pasa vacío puede causar issues
  - Todos los controladores con `@RequestParam`
- **Tipo de Incumplimiento:** Calidad

### Código Identificado

```java
// CatalogController.java:162
@PatchMapping("/{id}/disponibilidad")
public ResponseEntity<LibroResponseDTO> cambiarDisponibilidad(
        @PathVariable Long id,
        @RequestParam Boolean disponible) {  // required=true por defecto
    ...
}
```

### Justificación del Incumplimiento

Si no se envía el parámetro `disponible`, se lanza `MissingServletRequestParameterException`. Sin handler específico, cae al catch-all `RuntimeException` que expone el mensaje interno, revelando el nombre del parámetro esperado y su tipo.

### Instrucciones de Rectificación

Agregar en todos los `GlobalExceptionHandler.java`:

```java
import org.springframework.web.bind.MissingServletRequestParameterException;

@ExceptionHandler(MissingServletRequestParameterException.class)
public ResponseEntity<Map<String, String>> manejarParametroFaltante(
        MissingServletRequestParameterException ex) {
    Map<String, String> error = new HashMap<>();
    error.put("error", "El parámetro '" + ex.getParameterName() + "' es obligatorio");
    error.put("codigo", "ERR-400");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
}
```

---

## [JAVA-CONTROLLER-006] Ausencia de handler para HttpRequestMethodNotSupportedException

- **Severidad:** Baja
- **Ubicación:** Todos los `GlobalExceptionHandler.java`
- **Tipo de Incumplimiento:** Calidad

### Justificación del Incumplimiento

Cuando se envía un método HTTP no soportado (ej. `DELETE` en una ruta que solo acepta `GET`), Spring lanza `HttpRequestMethodNotSupportedException`. Sin handler específico, la respuesta por defecto de Spring Boot puede incluir la lista de métodos soportados en el body o devolver un error 405 genérico con posibles trazas.

### Instrucciones de Rectificación

```java
import org.springframework.web.HttpRequestMethodNotSupportedException;

@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
public ResponseEntity<Map<String, String>> manejarMetodoNoSoportado(
        HttpRequestMethodNotSupportedException ex) {
    Map<String, String> error = new HashMap<>();
    error.put("error", "Método HTTP no soportado para esta ruta");
    error.put("codigo", "ERR-405");
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(error);
}
```

---

## [JAVA-CONTROLLER-007] PathVariables sin validación de dominio ni anotaciones de constraint

- **Severidad:** Baja
- **Ubicación:** Todos los controladores — ningún `@PathVariable` tiene anotaciones de validación como `@Min`, `@Positive`, `@NotBlank`, etc.
- **Tipo de Incumplimiento:** Estándar de Codificación

### Código Identificado

```java
// Ejemplo representativo en CatalogController.java:78
@GetMapping("/{id}")
public ResponseEntity<LibroResponseDTO> obtenerPorId(
        @Parameter(description = "ID del libro", required = true)
        @PathVariable Long id) {   // Sin @Min, @Positive u otra constraint
    ...
}
```

### Justificación del Incumplimiento

Si bien Spring Boot rechaza automáticamente valores no numéricos (ver Finding 003), no hay validación semántica:

- IDs negativos o cero son aceptados
- Cadenas vacías para `@PathVariable String` son aceptadas
- No hay límites superiores para IDs numéricos

La validación de `@PathVariable` requiere activar `@Validated` a nivel de clase.

### Instrucciones de Rectificación

Agregar `@Validated` en la clase y constraints en los parámetros:

```java
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Positive;

@Tag(name = "Catalog", ...)
@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
@Validated  // <-- Activa validación de parámetros
public class CatalogController {

    @GetMapping("/{id}")
    public ResponseEntity<LibroResponseDTO> obtenerPorId(
            @Parameter(description = "ID del libro", required = true)
            @Positive(message = "El ID del libro debe ser un número positivo")
            @PathVariable Long id) { ... }
```

Y en el GlobalExceptionHandler agregar handler para `ConstraintViolationException`:

```java
import jakarta.validation.ConstraintViolationException;

@ExceptionHandler(ConstraintViolationException.class)
public ResponseEntity<Map<String, String>> manejarViolacionValidacion(
        ConstraintViolationException ex) {
    Map<String, String> errores = new HashMap<>();
    ex.getConstraintViolations().forEach(violation ->
        errores.put(violation.getPropertyPath().toString(), violation.getMessage())
    );
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errores);
}
```

---

## [JAVA-CONTROLLER-008] AuthController.register() usa ResponseEntity<?> perdiendo tipo concreto

- **Severidad:** Baja
- **Ubicación:** `identity-services/src/main/java/com/silvio/identity/controller/AuthController.java` (Línea 57)
- **Tipo de Incumplimiento:** Estándar de Codificación

### Código Identificado

```java
@PostMapping("/register")
public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
```

### Justificación del Incumplimiento

El uso de `?` como tipo genérico rompe la documentación de Swagger/OpenAPI para el endpoint `register`. Swagger no puede inferir el esquema de respuesta, resultando en documentación incompleta. El resto de endpoints retornan tipos concretos como `ResponseEntity<AuthResponse>`.

### Instrucciones de Rectificación

Crear un DTO `RegisterResponseDTO` o reutilizar un Map genérico con tipo concreto:

```java
@PostMapping("/register")
public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
    ...
}
```

---

## Resumen de Hallazgos

| ID | Severidad | Hallazgo | Módulos Afectados |
|---|---|---|---|
| CONTROLLER-001 | 🔴 Alta | RuntimeException catch-all expone mensajes al cliente | 10/10 |
| CONTROLLER-002 | 🔴 Alta | FeignException handler expone detalles downstream | 4/10 |
| CONTROLLER-003 | 🟡 Media | Falta handler para MethodArgumentTypeMismatchException | 10/10 |
| CONTROLLER-004 | 🟡 Media | Falta handler para HttpMessageNotReadableException | 6/10 |
| CONTROLLER-005 | 🟡 Media | Falta handler para MissingServletRequestParameterException | 10/10 |
| CONTROLLER-006 | 🟢 Baja | Falta handler para HttpRequestMethodNotSupportedException | 10/10 |
| CONTROLLER-007 | 🟢 Baja | PathVariables sin anotaciones de constraint | 11/11 |
| CONTROLLER-008 | 🟢 Baja | AuthController.register() usa ResponseEntity<?> | 1/11 |

### Aspectos Verificados sin Hallazgos

| Aspecto | Resultado |
|---|---|
| `@Valid` en todos los `@RequestBody` | ✅ 100% cobertura |
| Bean Validation en todos los DTOs de entrada | ✅ Todos los campos relevantes anotados |
| `@ToString.Exclude` en campos sensibles | ✅ Contraseñas, tokens protegidos |
| `@EqualsAndHashCode(callSuper = false)` en DTOs con HATEOAS | ✅ RepresentationModel usa el default correcto |
| Swagger `@Tag` + `@Operation` + `@ApiResponses` | ✅ Todos los endpoints documentados |
| Códigos de respuesta documentados coinciden con implementación | ✅ Sin discrepancias mayores |

---

*Reporte generado por Java Architecture Auditor — Solo lectura, sin modificación de código.*
