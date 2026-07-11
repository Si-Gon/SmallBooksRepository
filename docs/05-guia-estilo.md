# Guía de Estilo y Convenciones — SmallBooks

> **Estándares de código, estructura de paquetes, patrones y buenas prácticas para el desarrollo de microservicios en SmallBooks.**

---

## 1. Estructura de Paquetes

Cada microservicio sigue una estructura de paquetes uniforme:

```
src/main/java/com/silvio/{servicio}/
├── config/
│   └── SwaggerConfig.java              ← Configuración OpenAPI/Swagger
├── controller/
│   └── {Nombre}Controller.java         ← Endpoints REST
├── dto/
│   ├── {Nombre}RequestDTO.java         ← DTO de entrada (request body)
│   └── {Nombre}ResponseDTO.java        ← DTO de salida (extends RepresentationModel)
├── exception/
│   └── GlobalExceptionHandler.java     ← @ControllerAdvice
├── model/
│   └── {Nombre}.java                   ← Entidad JPA
├── repository/
│   └── {Nombre}Repository.java         ← Spring Data JPA Repository
└── service/
    └── {Nombre}Service.java            ← Lógica de negocio
```

Para servicios con funcionalidades adicionales:

```
├── client/                             ← Feign Clients
│   └── {Nombre}Client.java
├── security/                           ← JWT extraction
│   └── JwtExtractor.java
└── storage/                            ← Strategy Pattern
    ├── StorageService.java             ← Interfaz
    ├── DatabaseStorageService.java     ← @Primary
    └── LocalStorageService.java        ← Legacy
```

### Excepción: Identity Service e Infrastructure Services

- `identity-services/` usa `com.silvio.identity` e incluye carpetas `security/` con `JwtUtil.java`, `JwtAuthenticationFilter.java`, `SecurityConfig.java` y `JwtProperties.java`.
- `microservice-gateway/` usa `com.microservice.gateway.microservice_gateway` (naming distinto, legacy).

---

## 2. Patrón CSR Estricto

### Reglas de Dependencia

```
Controller → Service → Repository
     │           │
     ▼           ▼
    DTOs       Model/Entity
```

- **Controller**: Solo recibe/valida requests y construye respuestas con HATEOAS
- **Service**: Contiene toda la lógica de negocio y orquestación Feign
- **Repository**: Acceso a datos JPA (Spring Data)
- **Dependencias unidireccionales**: Controller → Service → Repository
- **Nunca** inyectar Repository en Controller

### Prohibiciones

- ❌ Controller no debe contener lógica de negocio
- ❌ Service no debe manejar HTTP (cabeceras, status codes)
- ❌ Repository no debe ser llamado desde Controller
- ❌ No exponer entidades JPA directamente en respuestas
- ❌ No usar `@Transactional` en Controller

---

## 3. Nombrado

### Microservicios

| Elemento | Convención | Ejemplo |
|----------|-----------|---------|
| **ID Spring** | `kebab-case` | `catalog-service` |
| **Carpeta** | `kebab-case` | `elending-service` |
| **Paquete base** | `com.silvio.{servicio}` | `com.silvio.catalog` |

### Clases Java

| Elemento | Convención | Ejemplo |
|----------|-----------|---------|
| **Controller** | `{Dominio}Controller` | `CatalogController` |
| **Service** | `{Dominio}Service` | `PrestamoService` |
| **Repository** | `{Entidad}Repository` | `LibroRepository` |
| **Entity** | `{Nombre}` | `Libro`, `Prestamo` |
| **Request DTO** | `{Operacion}RequestDTO` | `LibroRequestDTO` |
| **Response DTO** | `{Operacion}ResponseDTO` | `PrestamoResponseDTO` |
| **Feign Client** | `{Destino}Client` | `LicenseClient` |
| **Exception Handler** | `GlobalExceptionHandler` | Fijo |
| **Swagger Config** | `SwaggerConfig` | Fijo |
| **App Main** | `{Servicio}Application` | `CatalogServiceApplication` |

### Variables y Métodos

- **Variables**: `camelCase` — `libroRepository`, `prestamoService`
- **Métodos REST**: Verbos en español — `obtenerTodos()`, `crearPrestamo()`
- **Métodos privados**: `mapearADto()` para conversiones Entity→DTO
- **Constantes**: `UPPER_SNAKE_CASE` — `FORMATOS_PERMITIDOS`

---

## 4. Uso de DTOs

### Reglas

1. **Nunca exponer entidades JPA directamente** en respuestas o parámetros
2. **RequestDTO**: Contiene validaciones con Bean Validation
3. **ResponseDTO**: Extiende `RepresentationModel` para HATEOAS
4. **DTOs de comunicación Feign**: Sin validaciones, solo datos planos
5. **DTOs de servicio a servicio**: Pueden usar `@JsonIgnoreProperties(ignoreUnknown = true)`

### Patrón de Entrada/Salida

```java
// Entrada — siempre con validaciones
public class LibroRequestDTO {
    @NotBlank(message = "El título es obligatorio")
    @Size(max = 200)
    private String titulo;
    
    @Pattern(regexp = "^(?:\\d{9}[\\dX]|\\d{13})$")
    private String isbn;
}

// Salida — siempre extiende RepresentationModel
public class LibroResponseDTO extends RepresentationModel<LibroResponseDTO> {
    private Long id;
    private String titulo;
    private String autor;
    private Boolean disponible;
    // ...
}
```

### Mapeo Entity→DTO

Cada Service implementa un método privado `mapearADto()`:

```java
private LibroResponseDTO mapearADto(Libro libro) {
    LibroResponseDTO dto = new LibroResponseDTO();
    dto.setId(libro.getId());
    dto.setTitulo(libro.getTitulo());
    // ... resto de campos
    return dto;
}
```

---

## 5. Validaciones con Bean Validation

### Anotaciones Comunes

| Anotación | Uso | Ejemplo |
|-----------|-----|---------|
| `@NotBlank` | Strings obligatorios | `@NotBlank(message = "...")` |
| `@Size` | Límites de longitud | `@Size(max = 200)` |
| `@NotNull` | Campos obligatorios no String | `@NotNull` |
| `@Pattern` | Validación con regex | ISBN, URL |
| `@Min` / `@Max` | Rango numérico | `@Min(value = 1450)`, `@Max(value = 2100)` |
| `@Positive` | Números positivos | `@Positive` |

### Ubicación de Validaciones

- **RequestDTO**: Anotaciones de validación
- **Entities JPA**: Algunas entidades tienen `@NotBlank`, `@NotNull` — **esto es una mezcla de responsabilidades** que se debe refactorizar
- **Path variables**: Sin validación explícita (excepto en los métodos)

### Manejo de Errores de Validación

El `GlobalExceptionHandler` captura `MethodArgumentNotValidException` y devuelve:

```json
{
    "titulo": "El título es obligatorio",
    "isbn": "El ISBN debe tener formato ISBN-10 o ISBN-13"
}
```

---

## 6. Manejo de Errores con @ControllerAdvice

### Estructura

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> manejarValidacion(...) {
        // Errores de Bean Validation → 400 BAD_REQUEST
    }
    
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> manejarRuntime(RuntimeException ex) {
        // Errores de negocio → 400/404/409/422 según mensaje
    }
}
```

### Códigos HTTP por Tipo de Error

| Situación | Código | Excepción |
|-----------|--------|-----------|
| Validación fallida | `400 BAD_REQUEST` | `MethodArgumentNotValidException` |
| Credenciales incorrectas | `401 UNAUTHORIZED` | `BadCredentialsException` |
| Usuario no encontrado | `404 NOT_FOUND` | `UsernameNotFoundException` / `RuntimeException` |
| Recurso ya existe (duplicado) | `409 CONFLICT` | `RuntimeException` (contiene "ya existe") |
| Sin copias disponibles | `422 UNPROCESSABLE_CONTENT` | `RuntimeException` (según servicio) |
| Error interno | `500 INTERNAL_SERVER_ERROR` | Excepciones no capturadas |

### Formato de Respuesta de Error

```json
{
    "error": "Mensaje descriptivo del error"
}
```

Para errores de validación:

```json
{
    "campo1": "Mensaje de error del campo 1",
    "campo2": "Mensaje de error del campo 2"
}
```

---

## 7. HATEOAS

### Convenciones

- **ResponseDTO** extiende `RepresentationModel<ResponseDTO>`
- Los enlaces se agregan desde el **Controller**, no desde el Service
- Usar `linkTo(methodOn(Controller.class).metodo()).withRel("nombre")`
- Incluir siempre `self` link

### Ejemplo

```java
// En el Controller
LibroResponseDTO dto = catalogService.obtenerPorId(id);
dto.add(linkTo(methodOn(CatalogController.class).obtenerPorId(id)).withSelfRel());
dto.add(linkTo(methodOn(CatalogController.class).obtenerTodos()).withRel("todos"));
dto.add(linkTo(methodOn(CatalogController.class).obtenerDisponibles()).withRel("disponibles"));
```

### Enlaces Comunes por Servicio

| Servicio | Enlaces incluidos |
|----------|------------------|
| Catalog | `self`, `todos`, `disponibles`, `eliminar` |
| License | `self`, `todas`, `prestar`, `devolver` |
| E-Lending | `mis-activos`, `mi-historial` |
| Notification | `mis-notificaciones`, `no-leidas`, `marcar-leida`, `marcar-todas-leidas` |
| Subscription | `mi-plan`, `cancelar` |

---

## 8. Inyección de Dependencias

- Usar **`@RequiredArgsConstructor`** (Lombok) para inyección por constructor
- **Nunca** usar `@Autowired` en campos
- Mantener las dependencias como `private final`

```java
@Service
@RequiredArgsConstructor
public class PrestamoService {
    private final PrestamoRepository prestamoRepository;
    private final LicenseClient licenseClient;
    private final SubscriptionClient subscriptionClient;
    private final NotificationClient notificationClient;
}
```

---

## 9. Feign Clients

### Convenciones

| Elemento | Convención |
|----------|-----------|
| **Anotación** | `@FeignClient(name = "nombre-servicio")` |
| **Ubicación** | Paquete `client/` |
| **Nombre** | `{Destino}Client.java` |
| **URL** | No especificar URL — se resuelve via Eureka con `lb://` |
| **Ruta base** | Incluir el path completo del destino |
| **DTOs** | DTOs planos (sin `RepresentationModel`) con `@JsonIgnoreProperties` |

### Ejemplo

```java
@FeignClient(name = "license-service")
public interface LicenseClient {
    @GetMapping("/api/licenses/{libroId}")
    LicenciaDTO obtenerLicencia(@PathVariable("libroId") Long libroId);

    @PutMapping("/api/licenses/{libroId}/prestar")
    LicenciaDTO prestar(@PathVariable("libroId") Long libroId);
}
```

### Manejo de Errores Feign

- Envolver llamadas Feign en **try-catch**
- Servicios críticos: propagar la excepción
- Servicios no críticos (notificaciones): log warning, continuar flujo

---

## 10. Testing con Given/When/Then

### Estructura de Tests

```java
class CatalogServiceTest {
    
    @Test
    void testObtenerPorId_CuandoExiste_RetornaLibro() {
        // Given
        given(libroRepository.findById(1L)).willReturn(Optional.of(libro));
        
        // When
        LibroResponseDTO resultado = catalogService.obtenerPorId(1L);
        
        // Then
        assertThat(resultado.getTitulo()).isEqualTo("Don Quijote");
        verify(libroRepository).findById(1L);
    }
    
    @Test
    void testObtenerPorId_CuandoNoExiste_LanzaExcepcion() {
        // Given
        given(libroRepository.findById(99L)).willReturn(Optional.empty());
        
        // When / Then
        assertThrows(RuntimeException.class, () -> catalogService.obtenerPorId(99L));
    }
}
```

### Tipos de Tests

| Tipo | Clase testeada | Técnica | Cobertura |
|------|---------------|---------|-----------|
| **Unitarios (Service)** | `*Service.java` | Mockito + JUnit 5 | Lógica de negocio, casos borde |
| **REST (Controller)** | `*Controller.java` | MockMvc | HTTP status, JSON, HATEOAS, validaciones |

### Convenciones de Nombrado

```
test{Método}_{Contexto}_{ResultadoEsperado}
```

Ejemplos:
- `testObtenerPorId_CuandoExiste_RetornaLibro()`
- `testCrearPrestamo_CuandoLimiteExcedido_LanzaExcepcion()`
- `testAgregar_CuandoIsbnDuplicado_LanzaExcepcion()`

### Microservicios con Tests (66 tests, 0 fallos)

| Microservicio | Clase Test | Tipo | Tests |
|--------------|-----------|------|-------|
| catalog-service | `CatalogServiceTest` | Service | 10 |
| elending-service | `PrestamoServiceTest` | Service | 10 |
| identity-service | `UserServiceTest` | Service | 13 |
| license-service | `LicenseControllerTest` | Controller | 12 |
| subscription-service | `SuscripcionControllerTest` | Controller | 11 |
| notification-service | `NotificacionControllerTest` | Controller | 10 |

---

## 11. Logging

- Usar **SLF4J + Lombok** con `@Slf4j`
- Niveles: `info` para flujo normal, `warn` para situaciones esperadas, `error` para fallos
- Incluir IDs de negocio en logs: usuarioId, libroId, prestamoId
- No loguear contraseñas ni tokens JWT completos

```java
@Slf4j
@Service
public class PrestamoService {
    public PrestamoResponseDTO crearPrestamo(...) {
        log.info("Iniciando creación de préstamo — usuario: {}, libro: {}", usuarioId, libroId);
        // ...
        log.error("COMPENSACIÓN FALLIDA — inconsistencia en copias del libro: {}", libroId);
    }
}
```

---

## 12. JWT y Seguridad

- Extraer usuario del JWT en cada microservicio via `JwtExtractor` (no llamar a identity-service)
- El token JWT viaja en el header `Authorization: Bearer {token}`
- Los microservicios que necesitan el usuario: elending, subscription, content
- El Gateway valida el token, pero los microservicios también deben extraer el usuario del mismo token

---

## 13. Transacciones

- `@Transactional` solo en métodos **Service** que modifican datos
- Las llamadas Feign **no** participan en la transacción JPA local
- Patrón de compensación manual para mantener consistencia entre servicios

---

## 14. Checklist de Código

- [ ] ¿Sigue el patrón CSR?
- [ ] ¿Los DTOs de entrada tienen validaciones?
- [ ] ¿Los ResponseDTO extienden `RepresentationModel`?
- [ ] ¿Los HATEOAS links se agregan en el Controller?
- [ ] ¿Los Feign clients tienen try-catch?
- [ ] ¿Los mensajes de log incluyen IDs de negocio?
- [ ] ¿No se exponen entidades JPA?
- [ ] ¿Las dependencias son inyectadas por constructor?
- [ ] ¿Los tests siguen Given/When/Then?
- [ ] ¿El código compila y pasa los tests?
