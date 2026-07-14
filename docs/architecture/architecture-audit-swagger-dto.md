# Reporte de Auditoría — DTOs, Swagger/OpenAPI y Documentación General

> **Fecha:** 2026-07-14  
> **Auditor:** Java Architecture Auditor  
> **Alcance:** 11 Controllers, 30 DTOs, 7 Entidades JPA, 10 Configuraciones Swagger — en 13 microservicios  
> **Stack:** Spring Boot 3.3.11, Java 17, Spring Cloud 2023.0.5  

---

## Resumen Ejecutivo

| Dimensión | Estado | Hallazgos |
|-----------|--------|-----------|
| Exposición de entidades JPA en Controllers | ✅ Correcto | Ningún controller expone entidades directamente |
| Bean Validation en DTOs de entrada | ⚠️ Parcial | 3 DTOs con campos password carecen de `@Size` |
| Campos sensibles en DTOs de respuesta | ✅ Correcto | Ningún DTO expone passwords o tokens no intencionados |
| `@EqualsAndHashCode(callSuper = false)` | ✅ Correcto | Todas las clases con HATEOAS lo usan |
| `@Tag` en todos los Controllers | ✅ Correcto | Todos los 11 controllers tienen `@Tag` |
| `@Operation` + `@ApiResponses` en endpoints | ⚠️ Parcial | 7 endpoints de 2 controllers sin `description` en `@Operation` |
| Coherencia códigos de respuesta vs. código real | ⚠️ Parcial | 3 endpoints documentan códigos que no pueden producirse o faltan códigos reales |
| `@Schema` en DTOs | ❌ Ausente | Ningún DTO usa `@Schema` — Swagger UI sin descripciones de campo |
| `@ToString.Exclude` en entidades | ❌ Ausente | `User` entidad tiene `password` pero `@Data` expone todo via `@ToString` |
| Javadoc en métodos públicos de Service/Exception | ❌ Ausente | 9 GlobalExceptionHandlers y múltiples Services sin documentación |

---

## Auditoría de DTOs

### [JAVA-DTO-001] Campos `password` sin `@Size` en RegisterRequest

- **Severidad:** Media
- **Ubicación:** `identity-services/src/main/java/com/silvio/identity/dto/RegisterRequest.java` (Líneas 11-12)
- **Tipo de Incumplimiento:** Estándar de Codificación / Seguridad

#### Código Identificado

```java
@Data
public class RegisterRequest {
    @NotBlank
    private String username;
    @NotBlank
    private String password;       // ❌ Sin @Size(min/max)
    private Set<String> roles;
}
```

#### Justificación del Incumplimiento

El campo `password` solo tiene `@NotBlank`, lo que permite contraseñas de 1 carácter. La convención del proyecto exige validación completa en todos los DTOs de entrada. No hay restricción de longitud mínima ni máxima, lo que puede llevar a contraseñas débiles o a problemas de rendimiento con contraseñas extremadamente largas.

#### Instrucciones de Rectificación

Agregar `@Size` con valores razonables, por ejemplo:
```java
@NotBlank(message = "La contraseña es obligatoria")
@Size(min = 8, max = 128, message = "La contraseña debe tener entre 8 y 128 caracteres")
private String password;
```

---

### [JAVA-DTO-002] Campos `newPassword` sin `@Size` en ChangePasswordRequest y PasswordUpdateRequest

- **Severidad:** Media
- **Ubicación:**
  - `identity-services/src/main/java/com/silvio/identity/dto/ChangePasswordRequest.java` (Línea 12)
  - `identity-services/src/main/java/com/silvio/identity/dto/PasswordUpdateRequest.java` (Línea 12)
- **Tipo de Incumplimiento:** Estándar de Codificación / Seguridad

#### Código Identificado

```java
// ChangePasswordRequest.java
@Data
public class ChangePasswordRequest {
    @NotBlank(message = "Contraseña actual es obligatoria")
    private String currentPassword;

    @NotBlank(message = "Nueva contraseña es obligatoria")
    private String newPassword;    // ❌ Sin @Size
}
```

```java
// PasswordUpdateRequest.java
@Data
public class PasswordUpdateRequest {
    @NotBlank(message = "Token es obligatorio")
    private String token;

    @NotBlank(message = "Nueva contraseña es obligatoria")
    private String newPassword;    // ❌ Sin @Size
}
```

#### Justificación del Incumplimiento

Ambos DTOs permiten contraseñas nuevas sin restricción de longitud, lo que contradice la política de seguridad esperada. Las contraseñas deben tener una longitud mínima validada a nivel de DTO, no solo a nivel de servicio.

#### Instrucciones de Rectificación

Agregar `@Size(min = 8, max = 128)` a ambos campos `newPassword`, de forma consistente con la validación que se agregue en `RegisterRequest`.

---

### [JAVA-DTO-003] Entidad `User` usa `@Data` sin excluir `password` de `@ToString`

- **Severidad:** Baja
- **Ubicación:** `identity-services/src/main/java/com/silvio/identity/model/User.java` (Línea 12)
- **Tipo de Incumplimiento:** Estándar de Codificación / Seguridad

#### Código Identificado

```java
@Entity
@Table(name = "users")
@Data        // ❌ @Data incluye @ToString — password es campo sensible
@NoArgsConstructor
public class User {
    @Column(nullable = false)
    private String password;  // BCrypt hash, pero debe protegerse
}
```

#### Justificación del Incumplimiento

`@Data` de Lombok genera `@ToString` que incluye todos los campos. Aunque actualmente no se loguea el objeto `User` completo, si en el futuro se añade un `log.info("{}", user)`, el hash BCrypt de la contraseña quedaría expuesto en los logs. La convención del proyecto exige proteger campos sensibles con `@ToString.Exclude`.

No se encontró **ningún** uso de `@ToString.Exclude` en todo el proyecto, lo que sugiere que este patrón de protección no se ha aplicado en ninguna entidad.

#### Instrucciones de Rectificación

Agregar `@ToString.Exclude` en el campo `password` (y opcionalmente en `resetToken` y `refreshTokenHash`):
```java
@ToString.Exclude
@Column(nullable = false)
private String password;
```

Además, revisar que las demás entidades (`Prestamo`, `Libro`, `ArchivoLibro`, `License`, `Notificacion`, `Suscripcion`) no contengan campos sensibles que deban excluirse.

---

### [JAVA-DTO-004] DTOs de entrada con validación correcta

- **Severidad:** Informativo
- **Estado:** ✅ Correcto

Los siguientes DTOs de entrada tienen Bean Validation completo y correcto:

| DTO | Validaciones |
|-----|-------------|
| `LibroRequestDTO` | `@NotBlank`, `@Size`, `@Pattern(ISBN)`, `@Min`/`@Max` (año) |
| `PrestamoRequestDTO` | `@NotNull`, `@Positive` |
| `AuthRequest` | `@NotBlank` en username y password |
| `LicenseRequestDTO` | `@NotNull`, `@Positive`, `@Min`/`@Max` |
| `NotificacionRequestDTO` | `@NotBlank`, `@Size`, `@NotNull` |
| `SuscripcionRequestDTO` | `@NotNull`, `@Min`/`@Max` |
| `RefreshTokenRequest` | `@NotBlank` |
| `PasswordResetRequest` | `@NotBlank` |

---

### [JAVA-DTO-005] No se exponen entidades JPA directamente — uso correcto de DTOs

- **Severidad:** Informativo
- **Estado:** ✅ Correcto

Todos los controllers retornan exclusivamente DTOs. Ninguno expone entidades `@Entity` directamente en respuestas REST. La conversión se realiza en la capa Service mediante métodos `mapearADto()` privados. Mapeo verificado en:

- `CatalogService.mapearADto(Libro)` → `LibroResponseDTO`
- `PrestamoService.mapearADto(Prestamo)` → `PrestamoResponseDTO`
- `UserService.obtenerUsuarioPorUsername()` → `UsuarioDTO`
- `LicenseService` → `LicenseResponseDTO`
- `NotificacionService` → `NotificacionDTO`
- `IngestionService` → `ArchivoLibroDTO` (excluye intencionalmente `rutaOClave`)
- `SuscripcionService` → `SuscripcionResponseDTO`

---

### [JAVA-DTO-006] `@EqualsAndHashCode(callSuper = false)` correcto en DTOs con HATEOAS

- **Severidad:** Informativo
- **Estado:** ✅ Correcto

Todas las clases que extienden `RepresentationModel` usan `@EqualsAndHashCode(callSuper = false)`:

`LibroResponseDTO`, `PrestamoResponseDTO`, `AuthResponse`, `EstadisticasDTO`, `PrestamoAnalyticsDTO`, `ArchivoLibroDTO`, `LicenseResponseDTO`, `NotificacionDTO`, `SearchResultDTO`, `SuscripcionResponseDTO`.

---

## Auditoría Swagger / OpenAPI

### [JAVA-SWAGGER-001] Endpoints sin `description` en @Operation

- **Severidad:** Media
- **Ubicación:**
  - `license-service/src/main/java/com/silvio/license/controller/LicenseController.java` (Líneas 73, 92, 112, 132)
  - `notification-service/src/main/java/com/silvio/notification/controller/NotificacionController.java` (Líneas 50, 71, 90, 109)
- **Tipo de Incumplimiento:** Estándar de Codificación / Documentación

#### Código Identificado

```java
// LicenseController.java — 4 endpoints con @Operation incompleto
@Operation(summary = "Registrar nueva licencia")                // ❌ Sin description
@Operation(summary = "Actualizar licencia")                     // ❌ Sin description
@Operation(summary = "Descontar copia al prestar")              // ❌ Sin description
@Operation(summary = "Sumar copia al devolver")                 // ❌ Sin description
```

```java
// NotificacionController.java — 4 endpoints con @Operation incompleto
@Operation(summary = "Obtener notificaciones por usuario")      // ❌ Sin description
@Operation(summary = "Obtener notificaciones no leídas")        // ❌ Sin description
@Operation(summary = "Marcar notificación como leída")           // ❌ Sin description
@Operation(summary = "Marcar todas las notificaciones como leídas") // ❌ Sin description
```

#### Justificación del Incumplimiento

La convención del proyecto exige que **todos** los endpoints tengan `@Operation` con `summary` y `description`. Estos 8 endpoints en 2 controllers solo tienen `summary`, lo que genera documentación Swagger incompleta y reduce la calidad pedagógica del API (uno de los objetivos principales del proyecto).

#### Instrucciones de Rectificación

Agregar `description` a cada `@Operation`, por ejemplo:
```java
@Operation(summary = "Registrar nueva licencia",
           description = "Crea un nuevo registro de licencia para un libro con el número de copias especificado")
```

---

### [JAVA-SWAGGER-002] Código de respuesta 413 no documentado en IngestionController.subirArchivo

- **Severidad:** Alta
- **Ubicación:** `ingestion-service/src/main/java/com/silvio/ingestion/controller/IngestionController.java` (Líneas 31-35)
- **Tipo de Incumplimiento:** Documentación / Calidad

#### Código Identificado

```java
@ApiResponses({
    @ApiResponse(responseCode = "201", description = "Archivo subido exitosamente"),
    @ApiResponse(responseCode = "400", description = "Formato no permitido — solo PDF y EPUB"),
    @ApiResponse(responseCode = "404", description = "Libro no encontrado en el catálogo")
    // ❌ Falta 413 — el GlobalExceptionHandler captura MaxUploadSizeExceededException
})
```

Sin embargo, el `GlobalExceptionHandler` del mismo servicio sí maneja `MaxUploadSizeExceededException`:

```java
@ExceptionHandler(MaxUploadSizeExceededException.class)
public ResponseEntity<Map<String, String>> manejarArchivoGrande(...) {
    // ...
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error); // 413
}
```

#### Justificación del Incumplimiento

El código realmente puede retornar HTTP 413 (PAYLOAD_TOO_LARGE) cuando el archivo subido supera los 50MB, pero Swagger no documenta este escenario. Un consumidor del API no sabría que debe manejar este código de respuesta, lo que puede causar errores no controlados en clients Feign o integraciones.

#### Instrucciones de Rectificación

Agregar el código 413 en `@ApiResponses`:
```java
@ApiResponse(responseCode = "413", description = "El archivo supera el tamaño máximo permitido (50MB)")
```

---

### [JAVA-SWAGGER-003] Código de respuesta 503 no documentado en ContentController.descargarArchivo

- **Severidad:** Media
- **Ubicación:** `content-service/src/main/java/com/silvio/content/controller/ContentController.java` (Líneas 27-32)
- **Tipo de Incumplimiento:** Documentación

#### Código Identificado

```java
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "Archivo descargado exitosamente"),
    @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
    @ApiResponse(responseCode = "403", description = "El usuario no tiene préstamo activo para este libro"),
    @ApiResponse(responseCode = "404", description = "Archivo no encontrado para el libro indicado")
    // ❌ Falta 503 — el GlobalExceptionHandler captura "No se pudo verificar" desde Feign
})
```

El `ContentService` lanza `RuntimeException("No se pudo verificar el préstamo: ...")` cuando falla la comunicación con Lending Service via Feign, y el `GlobalExceptionHandler` lo convierte en 503 SERVICE_UNAVAILABLE.

#### Justificación del Incumplimiento

Si el servicio de préstamos está caído, el endpoint retorna 503, pero Swagger no lo documenta. Los clients Feign que consuman este endpoint no sabrán que deben manejar este código.

#### Instrucciones de Rectificación

Agregar `@ApiResponse(responseCode = "503", description = "Error de comunicación con servicios internos (Lending/Ingestion)")` en el endpoint `descargarArchivo`.

---

### [JAVA-SWAGGER-004] Código 400 documentado pero no producible en CatalogController.buscar

- **Severidad:** Baja
- **Ubicación:** `catalog-service/src/main/java/com/silvio/catalog/controller/CatalogController.java` (Líneas 86-89)
- **Tipo de Incumplimiento:** Documentación / Coherencia

#### Código Identificado

```java
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "Búsqueda realizada exitosamente"),
    @ApiResponse(responseCode = "400", description = "Parámetros de búsqueda inválidos")  // ❌ No producible
})
@GetMapping("/buscar")
public ResponseEntity<List<LibroResponseDTO>> buscar(
        @RequestParam(required = false) String titulo,
        @RequestParam(required = false) String autor,
        @RequestParam(required = false) String genero) {
    return ResponseEntity.ok(catalogService.buscar(titulo, autor, genero));
}
```

Los tres parámetros son `required = false` sin anotaciones de validación. No hay `@Valid` ni anotaciones Bean Validation. El `GlobalExceptionHandler` no puede retornar 400 para este endpoint porque no hay `MethodArgumentNotValidException` posible.

#### Justificación del Incumplimiento

Swagger documenta un código de respuesta (400) que el endpoint nunca puede producir en su estado actual. Esto genera confianza falsa en los consumidores del API y contradice el principio de documentación fiable.

#### Instrucciones de Rectificación

Opción A (recomendada): Eliminar el `@ApiResponse(responseCode = "400")` si no se va a agregar validación.

Opción B: Agregar validación a los parámetros de búsqueda, por ejemplo `@Size(max = 200)` en cada uno, para que 400 sea posible.

---

### [JAVA-SWAGGER-005] Falta código 400 en CatalogController.cambiarDisponibilidad

- **Severidad:** Baja
- **Ubicación:** `catalog-service/src/main/java/com/silvio/catalog/controller/CatalogController.java` (Líneas 142-145)
- **Tipo de Incumplimiento:** Documentación

#### Código Identificado

```java
@ApiResponses({
    @ApiResponse(responseCode = "200", description = "Disponibilidad actualizada"),
    @ApiResponse(responseCode = "404", description = "Libro no encontrado")
    // ❌ Falta 400 — @RequestParam Boolean disponible es required=true por defecto
})
@PatchMapping("/{id}/disponibilidad")
public ResponseEntity<LibroResponseDTO> cambiarDisponibilidad(
        @PathVariable Long id,
        @RequestParam Boolean disponible) {  // Si falta, Spring retorna 400
```

El parámetro `disponible` es `@RequestParam` sin `required = false`, por lo que si no se envía, Spring retorna 400 Bad Request automáticamente **antes** de llegar al método del controller.

#### Justificación del Incumplimiento

El endpoint puede retornar 400 (parámetro requerido faltante) pero Swagger no lo documenta. La documentación debe reflejar todos los códigos de error posibles.

#### Instrucciones de Rectificación

Agregar `@ApiResponse(responseCode = "400", description = "Parámetro 'disponible' es obligatorio")` en `@ApiResponses`.

---

### [JAVA-SWAGGER-006] Todos los endpoints tienen @Tag

- **Severidad:** Informativo
- **Estado:** ✅ Correcto

Los 11 controllers en el proyecto tienen `@Tag` correctamente definido:

| Controller | @Tag Name | Descripción |
|-----------|-----------|-------------|
| `CatalogController` | Catalog | Gestión del catálogo de libros |
| `PrestamoController` | E-Lending | Gestión de préstamos digitales |
| `AuthController` | Identity | Autenticación, registro y gestión de contraseñas |
| `UserController` | Users | Consulta de datos de usuarios |
| `AnalyticsController` | Analytics | Métricas y estadísticas globales |
| `ContentController` | Content Delivery | Entrega de contenido digital |
| `IngestionController` | Ingestion | Carga y gestión de archivos PDF/EPUB |
| `LicenseController` | Licenses | Gestión de licencias y control de copias |
| `NotificacionController` | Notifications | Gestión de notificaciones |
| `SearchController` | Search | Búsqueda y descubrimiento de libros |
| `SuscripcionController` | Subscriptions | Gestión de suscripciones |

---

### [JAVA-SWAGGER-007] Ausencia de @Schema en todos los DTOs

- **Severidad:** Baja
- **Ubicación:** Todos los DTOs del proyecto (30 archivos)
- **Tipo de Incumplimiento:** Documentación / Calidad

#### Justificación del Incumplimiento

Ningún DTO utiliza `@Schema` de Swagger. Aunque SpringDoc OpenAPI genera el schema automáticamente a partir de los campos Java, sin `@Schema(description = "...")` y `@Schema(example = "...")` la documentación generada carece de descripciones de campo y ejemplos. Esto reduce la usabilidad de Swagger UI para consumidores del API.

#### Instrucciones de Rectificación

Agregar `@Schema` en campos clave de DTOs de entrada y salida, por ejemplo:
```java
@Schema(description = "ISBN del libro en formato ISBN-10 o ISBN-13", example = "9781234567890")
private String isbn;
```

Priorizar DTOs de entrada expuestos en endpoints públicos: `LibroRequestDTO`, `RegisterRequest`, `AuthRequest`, `PrestamoRequestDTO`, `SuscripcionRequestDTO`, `LicenseRequestDTO`.

---

## Auditoría de Documentación General

### [JAVA-DOC-001] GlobalExceptionHandlers sin documentación javadoc

- **Severidad:** Baja
- **Ubicación:** Los 9 archivos `GlobalExceptionHandler.java` en todos los microservicios de negocio
- **Tipo de Incumplimiento:** Documentación / Estándar de Codificación

#### Código Identificado

```java
@RestControllerAdvice
public class GlobalExceptionHandler {           // ❌ Sin javadoc de clase

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> manejarRuntimeException(   // ❌ Sin javadoc
            RuntimeException ex) {
        // ...
    }
}
```

#### Justificación del Incumplimiento

Los `GlobalExceptionHandler` son componentes críticos del sistema que determinan los códigos de respuesta HTTP para cada escenario de error. La ausencia de javadoc en los métodos públicos dificulta el mantenimiento y la comprensión de la lógica de manejo de errores, especialmente cuando hay lógica condicional para determinar el status HTTP basado en el mensaje de la excepción.

#### Instrucciones de Rectificación

Agregar javadoc a cada método `@ExceptionHandler` documentando:
- Qué excepción captura
- Bajo qué condiciones retorna cada código HTTP
- Ejemplo de mensajes de error que produce cada status

---

### [JAVA-DOC-002] AuthController retorna `ResponseEntity<?>` en lugar de DTOs tipados

- **Severidad:** Media
- **Ubicación:** `identity-services/src/main/java/com/silvio/identity/controller/AuthController.java` (Líneas 75, 139, 155, 172)
- **Tipo de Incumplimiento:** Arquitectura / Documentación

#### Código Identificado

```java
@PostMapping("/register")
public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {  // ❌ ? en lugar de DTO
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(Map.of(
            "message", "...",
            "username", request.getUsername(),
            "status", "CREATED"
        ));
}

@PostMapping("/forgot-password")
public ResponseEntity<?> forgotPassword(@Valid @RequestBody PasswordResetRequest request) {  // ❌ ?
    return ResponseEntity.ok(Map.of(
        "message", "...",
        "resetToken", resetToken,
        "instruction", "..."
    ));
}
```

#### Justificación del Incumplimiento

El uso de `ResponseEntity<?>` y `Map.of()` impide que Swagger genere un schema de respuesta preciso. La documentación OpenAPI para estos endpoints aparece como `object` genérico sin campos documentados. La convención del proyecto exige DTOs tipados para todas las respuestas REST.

#### Instrucciones de Rectificación

Crear DTOs de respuesta específicos:
- `RegisterResponseDTO` con `message`, `username`, `status`
- `PasswordResetResponseDTO` con `message`, `resetToken`, `instruction`
- `PasswordChangeResponseDTO` con `message`, `status`

Y cambiar los métodos a `ResponseEntity<RegisterResponseDTO>`, etc.

---

### [JAVA-DOC-003] Controllers sin javadoc de clase

- **Severidad:** Baja
- **Ubicación:** Todos los controllers
- **Tipo de Incumplimiento:** Documentación

#### Justificación del Incumplimiento

Aunque todos los controllers tienen `@Tag` con descripción, ninguno tiene javadoc de clase. La convención del proyecto sugiere que las clases públicas deben tener javadoc explicando responsabilidades y dependencias.

#### Ejemplo de lo esperado

```java
/**
 * Controlador REST para la gestión del catálogo de libros.
 * Expone operaciones CRUD sobre la entidad Libro.
 * Dependencias: CatalogService.
 */
@Tag(name = "Catalog", description = "Gestión del catálogo de libros de SmallBooks")
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {
```

---

### [JAVA-DOC-004] Services sin javadoc en métodos públicos

- **Severidad:** Baja
- **Ubicación:** Todos los servicios del proyecto
- **Tipo de Incumplimiento:** Documentación

#### Justificación del Incumplimiento

Los métodos públicos en la capa Service —especialmente aquellos con lógica compleja como `PrestamoService.doCrearPrestamo()` (68 líneas) o `PrestamoService.cerrarPrestamosVencidos()` (60 líneas)— carecen de javadoc que explique el flujo, los pasos y las condiciones de error. Algunos servicios tienen comentarios en línea útiles, pero no siguen el formato javadoc estándar.

#### Instrucciones de Rectificación

Agregar javadoc al menos en los métodos públicos más complejos, documentando:
- Propósito del método
- Parámetros y su origen (ej. "usuarioId extraído del token JWT")
- Excepciones lanzadas y bajo qué condiciones
- Flujo principal (pasos numerados)

---

## Resumen de Hallazgos por Severidad

| ID | Severidad | Hallazgo |
|----|-----------|----------|
| JAVA-SWAGGER-002 | 🔴 Alta | Código 413 no documentado en IngestionController |
| JAVA-DTO-001 | 🟡 Media | `RegisterRequest.password` sin `@Size` |
| JAVA-DTO-002 | 🟡 Media | `newPassword` sin `@Size` en 2 DTOs |
| JAVA-SWAGGER-001 | 🟡 Media | 8 endpoints sin `description` en @Operation |
| JAVA-SWAGGER-003 | 🟡 Media | Código 503 no documentado en ContentController |
| JAVA-DOC-002 | 🟡 Media | AuthController usa `ResponseEntity<?>` en 4 endpoints |
| JAVA-DTO-003 | 🟢 Baja | `User` entidad con `@Data` y password sin `@ToString.Exclude` |
| JAVA-SWAGGER-004 | 🟢 Baja | Código 400 documentado pero no producible en CatalogController.buscar |
| JAVA-SWAGGER-005 | 🟢 Baja | Falta código 400 en CatalogController.cambiarDisponibilidad |
| JAVA-SWAGGER-007 | 🟢 Baja | Ausencia de `@Schema` en todos los DTOs |
| JAVA-DOC-001 | 🟢 Baja | GlobalExceptionHandlers sin javadoc |
| JAVA-DOC-003 | 🟢 Baja | Controllers sin javadoc de clase |
| JAVA-DOC-004 | 🟢 Baja | Services sin javadoc en métodos públicos |

---

## Buenas Prácticas Confirmadas ✅

| Aspecto | Detalle |
|---------|---------|
| Separación CSR | Controller → Service → Repository estricta, sin lógica de negocio en controllers |
| DTOs vs Entidades | Todos los controllers retornan DTOs, ningún JPA Entity se expone vía REST |
| `@Valid` en inputs | Todos los endpoints que reciben body usan `@Valid` |
| HATEOAS | Todos los DTOs de respuesta extienden `RepresentationModel` con links |
| `@EqualsAndHashCode(callSuper = false)` | Correcto en todas las clases con HATEOAS |
| `@Tag` en Controllers | Todos los 11 controllers tienen `@Tag` |
| `@Operation` + `@ApiResponses` | La mayoría de los endpoints (44/52) tienen documentación completa |
| Cobertura de validación | `LibroRequestDTO` es ejemplar con validaciones completas (ISBN, año, URL) |
| Exclusión de datos sensibles | `ArchivoLibroDTO` excluye intencionalmente `rutaOClave` |
| Consistencia de nomenclatura | DTOs siguen convención `{Nombre}{Request/Response}DTO` |

---

*Reporte generado por el Java Architecture Auditor — modo solo lectura, sin modificación de código.*
