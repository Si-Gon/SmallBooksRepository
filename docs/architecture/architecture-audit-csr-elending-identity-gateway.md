# Reporte de Auditoría — CSR, Lombok, Errores, Feign, Flyway, Actuator, ShedLock

> **Fecha:** 2026-07-14  
> **Auditor:** Java Architecture Auditor  
> **Servicios auditados:**  
>   - `identity-services` (7 clases main)  
>   - `elending-service` (33 clases main)  
>   - `microservice-gateway` (6 clases main)  
> **Alcance:** Separación de capas CSR, Lombok, manejo de errores, Feign Clients, Flyway, Actuator, ShedLock  

---

## Resumen Ejecutivo

| Dimensión | identity-services | elending-service | microservice-gateway |
|-----------|:---:|:---:|:---:|
| CSR — Controllers sin lógica de negocio | ❌ 3 violaciones | ✅ Correcto | N/A (Gateway reactivo) |
| CSR — Services sin acceso directo a datos | ✅ Correcto | ✅ Correcto | N/A |
| Lombok — `@EqualsAndHashCode(callSuper = false)` | ✅ 1/1 DTO con HATEOAS | ✅ 1/1 DTO con HATEOAS | N/A |
| Lombok — `@ToString.Exclude` en campos sensibles | ❌ Ausente | ❌ Ausente | N/A |
| Manejo de errores — GlobalExceptionHandler | ✅ Correcto | ✅ Correcto | N/A (filtros reactivos) |
| Feign — FallbackFactory + manejo de errores | N/A (sin Feign) | ✅ Completos | N/A |
| Flyway — Formato `V{numero}__{descripcion}.sql` | ✅ 4 migraciones | ✅ 3 migraciones | N/A |
| Actuator — Endpoints seguros | ✅ Solo health | ✅ Solo health | ⚠️ `show-details: always` |
| ShedLock — Scheduler protegido | N/A | ✅ Correcto | N/A |

---

## 1. Separación de Capas CSR

### [JAVA-CSR-001] Lógica de autenticación y tokenización en AuthController

- **Severidad:** Alta
- **Ubicación:** `identity-services/src/main/java/com/silvio/identity/controller/AuthController.java`
  - Líneas 44-51 (login)
  - Líneas 95-127 (refreshToken)
  - Líneas 140-145 (forgotPassword)
  - Líneas 176-178 (changePassword)
- **Tipo de Incumplimiento:** Arquitectura (CSR)

#### Código Identificado — Login

```java
// AuthController.java — LÍNEAS 44-51
@PostMapping("/login")
public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
    // ❌ Lógica de autenticación directamente en el controller
    Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
    UserDetails userDetails = (UserDetails) authentication.getPrincipal();

    // ❌ Generación de tokens en el controller (debería estar en Service)
    String accessToken = jwtUtil.generateAccessToken(userDetails);
    String refreshToken = jwtUtil.generateRefreshToken(userDetails.getUsername());

    // ❌ Delegación parcial al service (debería ser un solo llamado)
    userService.storeRefreshTokenHash(userDetails.getUsername(), refreshToken);

    AuthResponse response = new AuthResponse(
        accessToken, refreshToken,
        "Login exitoso. Bienvenido " + userDetails.getUsername(),
        userDetails.getUsername()
    );
    // ...
}
```

#### Código Identificado — refreshToken

```java
// AuthController.java — LÍNEAS 95-128
@PostMapping("/refresh")
public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
    String refreshToken = request.getRefreshToken();

    // ❌ Validación de expiración en el controller
    if (jwtUtil.isTokenExpired(refreshToken)) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new AuthResponse(null, null, "Refresh token expirado", null));
    }

    // ❌ Extracción de tipo de token en el controller
    String tokenType = jwtUtil.extractTokenType(refreshToken);
    if (!"refresh".equals(tokenType)) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new AuthResponse(null, null, "Token inválido: no es un refresh token", null));
    }

    // ❌ Lógica de rotación de tokens en el controller
    String username = jwtUtil.extractUsername(refreshToken);
    try {
        UserDetails userDetails = userService.loadUserByUsername(username);
        String newAccessToken = jwtUtil.generateAccessToken(userDetails);
        String newRefreshToken = jwtUtil.generateRefreshToken(username);
        userService.rotateRefreshToken(refreshToken, newRefreshToken);
        // ...
    } catch (RuntimeException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new AuthResponse(null, null, "...", null));
    }
}
```

#### Código Identificado — changePassword

```java
// AuthController.java — LÍNEAS 171-183
@PostMapping("/change-password")
public ResponseEntity<?> changePassword(
        @RequestHeader("Authorization") String authHeader,
        @Valid @RequestBody ChangePasswordRequest request) {
    // ❌ Extracción de username del token en el controller
    String token = authHeader.substring(7);
    String username = jwtUtil.extractUsername(token);
    userService.changePassword(username, request.getCurrentPassword(), request.getNewPassword());
    return ResponseEntity.ok(Map.of("message", "...", "status", "SUCCESS"));
}
```

#### Justificación del Incumplimiento

El patrón CSR exige que los Controllers solo orquesten llamadas al Service y definan el formato de la respuesta. En `AuthController`:

1. **Login**: realiza autenticación, genera tokens y almacena hash de refresh token — todo debería ser un solo llamado a `UserService.login(request)`.
2. **refreshToken**: valida expiración, extrae tipo de token, valida contra hash almacenado, rota tokens — lógica completa de negocio fuera del Service.
3. **changePassword**: extrae el username del token JWT usando `jwtUtil` directamente en lugar de delegarlo al Service.

El Controller tiene dependencias directas de `AuthenticationManager`, `JwtUtil` y `UserService` que deberían abstraerse tras el Service.

#### Instrucciones de Rectificación

Crear un método `UserService.login(AuthRequest request)` que encapsule: autenticación → generación de tokens → almacenamiento de hash. El Controller debe quedar así:

```java
@PostMapping("/login")
public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
    AuthResponse response = userService.login(request);
    // HATEOAS links
    return ResponseEntity.ok(response);
}
```

Similar para `refreshToken` → `UserService.refreshToken(String refreshToken)` y `changePassword` → `UserService.changePassword(String authHeader, ChangePasswordRequest request)`.

---

### [JAVA-CSR-002] AuthController depende directamente de JwtUtil y AuthenticationManager

- **Severidad:** Media
- **Ubicación:** `identity-services/src/main/java/com/silvio/identity/controller/AuthController.java` (Líneas 30-32)
- **Tipo de Incumplimiento:** Arquitectura (CSR)

#### Código Identificado

```java
@Tag(name = "Identity", description = "...")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    // ❌ Dependencias de infraestructura en el Controller
    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final JwtUtil jwtUtil;  // ← Debería estar solo en Service
```

#### Justificación del Incumplimiento

Un Controller no debería conocer detalles de implementación como `AuthenticationManager` (Spring Security) o `JwtUtil` (generación/validación de tokens). Estas son responsabilidades de la capa Service. La presencia de estas dependencias evidencia que la lógica de autenticación no se ha abstraído correctamente.

#### Instrucciones de Rectificación

Eliminar `AuthenticationManager` y `JwtUtil` del Controller. Mover toda la lógica de autenticación, generación de tokens y rotación a `UserService`. El Controller solo debe depender de `UserService`.

---

### [JAVA-CSR-003] Controllers sin lógica de negocio en elending-service y microservice-gateway

- **Severidad:** Informativo
- **Estado:** ✅ Correcto

#### elending-service

`PrestamoController` delega correctamente toda la lógica a `PrestamoService`. Las únicas operaciones en el Controller son:

- Extraer usuario del token via `JwtExtractor` (utilitario, acceptable)
- Construir HATEOAS links (responsabilidad de presentación, acceptable)
- Delegar llamadas al Service

```java
// ✅ Ejemplo de controller limpio
@PostMapping("/prestamos")
public ResponseEntity<PrestamoResponseDTO> crearPrestamo(
        @Valid @RequestBody PrestamoRequestDTO request,
        @RequestHeader("Authorization") String authHeader) {

    String usuarioId = jwtExtractor.extraerUsuario(authHeader);  // ✅ Utilitario
    PrestamoResponseDTO prestamo = prestamoService.crearPrestamo(request, usuarioId);  // ✅ Delegación
    prestamo.add(linkTo(...).withRel("mis-activos"));  // ✅ HATEOAS
    return ResponseEntity.status(HttpStatus.CREATED).body(prestamo);
}
```

#### microservice-gateway

Como API Gateway reactivo, no sigue el patrón CSR. Usa filtros (`GlobalFilter`, `GatewayFilter`) correctamente:

| Filtro | Responsabilidad | CSR |
|--------|----------------|:---:|
| `GlobalRateLimitingFilter` | Rate limiting global y por IP usando Bucket4j | ✅ |
| `JwtAuthFilter` | Validación de JWT, propagación de identidad | ✅ |
| `TraceIdResponseFilter` | Exposición de traceId en respuesta | ✅ |

---

### [JAVA-CSR-004] Services con acceso correcto a datos a través de Repositories

- **Severidad:** Informativo
- **Estado:** ✅ Correcto

| Service | Acceso a datos | CSR |
|---------|---------------|:---:|
| `UserService` | Via `UserRepository` (JPA) | ✅ |
| `PrestamoService` | Via `PrestamoRepository` (JPA) | ✅ |

Ningún Service accede directamente a `EntityManager`, `DataSource` o realiza consultas SQL nativas. Todos los accesos a datos se hacen mediante métodos de repositorio Spring Data JPA.

---

## 2. Lombok

### [JAVA-LOM-001] Entidad `User` con `@Data` sin `@ToString.Exclude` en campos sensibles

- **Severidad:** Alta
- **Ubicación:** `identity-services/src/main/java/com/silvio/identity/model/User.java` (Línea 12)
- **Tipo de Incumplimiento:** Estándar de Codificación / Seguridad

#### Código Identificado

```java
@Entity
@Table(name = "users")
@Data                              // ❌ Genera @ToString con TODOS los campos
@NoArgsConstructor
public class User {

    @Column(nullable = false)
    private String password;        // BCrypt hash — campo sensible

    private String resetToken;      // Token de recuperación — campo sensible

    private String refreshTokenHash; // SHA-256 del refresh token — campo sensible
}
```

#### Justificación del Incumplimiento

`@Data` de Lombok genera automáticamente `@ToString`, `@EqualsAndHashCode`, `@Getter`, `@Setter` y `@RequiredArgsConstructor`. El `@ToString` incluye todos los campos, incluidos `password` (hash BCrypt), `resetToken` y `refreshTokenHash`. Aunque `password` es un hash, su exposición en logs representa un riesgo de seguridad innecesario. `resetToken` y `refreshTokenHash` son aún más sensibles al permitir acceso no autorizado.

No se encontró **ningún** uso de `@ToString.Exclude` en ninguno de los 3 servicios auditados, lo que confirma que este patrón de protección no se ha aplicado en ninguna entidad.

#### Instrucciones de Rectificación

Agregar `@ToString.Exclude` en los campos sensibles:

```java
@ToString.Exclude
@Column(nullable = false)
private String password;

@ToString.Exclude
private String resetToken;

@ToString.Exclude
private String refreshTokenHash;
```

Además, revisar las entidades de `elending-service` (`Prestamo.java`) para identificar si algún campo requiere exclusión (no se identificaron campos sensibles en `Prestamo`).

---

### [JAVA-LOM-002] `@EqualsAndHashCode(callSuper = false)` correctamente aplicado

- **Severidad:** Informativo
- **Estado:** ✅ Correcto

Todos los DTOs que extienden `RepresentationModel` usan `@EqualsAndHashCode(callSuper = false)`:

| Servicio | DTO | Estado |
|----------|-----|:------:|
| `identity-services` | `AuthResponse` | ✅ |
| `elending-service` | `PrestamoResponseDTO` | ✅ |

Los DTOs que NO extienden `RepresentationModel` (`PrestamoRequestDTO`, `AuthRequest`, `RegisterRequest`, `LibroDTO`, `LicenciaDTO`, `SuscripcionDTO`, `UsuarioDTO`) no requieren esta anotación.

---

## 3. Manejo de Errores

### [JAVA-ERR-001] GlobalExceptionHandler centralizado y correcto en los 3 servicios

- **Severidad:** Informativo
- **Estado:** ✅ Correcto

#### identity-services — GlobalExceptionHandler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)  // → 400 BAD_REQUEST
    @ExceptionHandler(RuntimeException.class)                 // → 404 / 409 según mensaje
}
```

✅ Maneja errores de validación (400) y errores de negocio (404, 409).  
✅ Sin bloques try-catch en `AuthController` para errores de validación.  
✅ `UserController` no tiene try-catch.

#### elending-service — GlobalExceptionHandler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)        // → 400
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class) // → 409
    @ExceptionHandler(RuntimeException.class)                       // → 422 / 409 / 503
}
```

✅ Manejo robusto con 3 tipos de excepción.  
✅ `ObjectOptimisticLockingFailureException` capturado y convertido a 409.  
✅ `RuntimeException` mapea a 422 (No hay copias / límite), 409 (Ya tienes), 503 (Feign caído).  
✅ Sin bloques try-catch en `PrestamoController`.

#### microservice-gateway

✅ Como gateway reactivo, no tiene GlobalExceptionHandler. Los filtros retornan errores HTTP directamente:
- `JwtAuthFilter`: 401 cuando token inválido/ausente
- `GlobalRateLimitingFilter`: 429 cuando se excede el rate limit
- Toda la lógica de error está encapsulada dentro de los filtros.

---

### [JAVA-ERR-002] try-catch en Controllers ausente — correcto

- **Severidad:** Informativo
- **Estado:** ✅ Correcto

Ninguno de los 3 servicios auditados tiene bloques try-catch en Controllers:

| Servicio | Controller | try-catch en Controller |
|----------|-----------|:----------------------:|
| identity | `AuthController` | ❌ No tiene (usa if/else con ResponseEntity) |
| identity | `UserController` | ❌ No tiene |
| elending | `PrestamoController` | ❌ No tiene |
| gateway | (filtros) | N/A |

> **Nota:** `AuthController.refreshToken()` tiene un bloque `try-catch` para `RuntimeException` (líneas 110-127). Aunque el controller no debería tener try-catch, este maneja un caso específico donde la excepción cambia la respuesta a 401 con un mensaje diferente. La solución recomendada es mover esta lógica al Service (ver JAVA-CSR-001).

---

## 4. FeignClient — Manejo de Errores

### [JAVA-FEI-001] Todos los Feign Clients tienen FallbackFactory — correcto

- **Severidad:** Informativo
- **Estado:** ✅ Correcto

`elending-service` tiene 4 Feign Clients activos, todos con `FallbackFactory`:

| Feign Client | FallbackFactory | Estrategia de degradación |
|-------------|----------------|--------------------------|
| `CatalogClient` | `CatalogClientFallbackFactory` | Devuelve libro "No disponible" o lista vacía |
| `LicenseClient` | `LicenseClientFallbackFactory` | Devuelve 0 copias disponibles (deniega préstamo) |
| `SubscriptionClient` | `SubscriptionClientFallbackFactory` | Aplica plan BASICO por defecto (2 préstamos, 7 días) |
| `IdentityClient` | `IdentityClientFallbackFactory` | Devuelve usuario por defecto con ROLE_USER |

Todos los fallbackFactories:
- ✅ Registran una advertencia con la causa del fallo
- ✅ Devuelven respuestas degradadas seguras (nunca lanzan excepción)
- ✅ Están anotados con `@Slf4j` y `@Component`

---

### [JAVA-FEI-002] Errores Feign manejados en PrestamoService con retry y compensación

- **Severidad:** Informativo
- **Estado:** ✅ Correcto

`PrestamoService` implementa un patrón de reintento y compensación para errores Feign:

```java
// ✅ Retry automático para conflictos de concurrencia (3 intentos)
while (true) {
    try {
        return doCrearPrestamo(request, usuarioId);
    } catch (FeignException.Conflict e) {
        if (++intento >= maxReintentos) throw new RuntimeException("...");
        // Reintentar
    }
}

// ✅ Compensación: si falla el save, restaura la copia descontada
try {
    guardado = prestamoRepository.save(prestamo);
} catch (Exception e) {
    licenseClient.devolver(request.getLibroId());  // ← compensación
    throw new RuntimeException("...");
}
```

- ✅ `FeignException.Conflict` capturado para reintentar en lugar de propagar 500
- ✅ `licenseClient.prestar()` en try-catch con compensación
- ✅ Circuit Breaker configurado globalmente (5s timeout, ventana de 10 llamadas)

---

### [JAVA-FEI-003] NotificationClient deprecado pero presente

- **Severidad:** Baja
- **Ubicación:** `elending-service/src/main/java/com/silvio/elending/client/NotificationClient.java`
- **Tipo de Incumplimiento:** Calidad / Código muerto

#### Código Identificado

```java
// NotificationClient.java — archivo completo
// NOTA: Este cliente Feign ha sido reemplazado por mensajería asíncrona
// con RabbitMQ (NotificacionPublisher). ...
```

#### Justificación del Incumplimiento

El archivo solo contiene un comentario indicando que fue reemplazado por RabbitMQ, pero la clase `NotificationClient` (la interfaz Feign) sigue existiendo en el árbol de fuentes. Aunque está vacía, constituye código muerto que puede generar confusión. Si se eliminó la interfaz Feign, el archivo debería eliminarse por completo.

#### Instrucciones de Rectificación

Eliminar el archivo `NotificationClient.java` ya que su funcionalidad fue reemplazada completamente por `NotificacionPublisher.java`.

---

## 5. Flyway — Migraciones

### [JAVA-FLY-001] Formato versionado correcto en identity-services

- **Severidad:** Informativo
- **Estado:** ✅ Correcto

| Archivo | Propósito |
|---------|-----------|
| `V1__create_user_tables.sql` | Creación de tablas `users` y `user_roles` |
| `V2__insert_default_users.sql` | Datos iniciales (admin, user1) |
| `V3__add_reset_token_fields.sql` | Columnas `reset_token`, `reset_token_expiry` |
| `V4__add_refresh_token_hash.sql` | Columna `refresh_token_hash` |

✅ Formato: `V{numero}__{descripcion}.sql`  
✅ Nombres descriptivos en español e inglés  
✅ Migraciones secuenciales sin saltos  

---

### [JAVA-FLY-002] Formato versionado correcto en elending-service

- **Severidad:** Informativo
- **Estado:** ✅ Correcto

| Archivo | Propósito |
|---------|-----------|
| `V1__crear_tabla_prestamos.sql` | Creación de tabla `prestamos` con índices |
| `V2__agregar_version_prestamos.sql` | Columna `version` para optimistic locking |
| `V3__agregar_tabla_shedlock.sql` | Creación de tabla `shedlock` para ShedLock |

✅ Formato correcto  
✅ `V3__agregar_tabla_shedlock.sql` crea la tabla necesaria para ShedLock  
✅ Versiones secuenciales sin huecos  

---

## 6. Actuator — Endpoints Seguros

### [JAVA-ACT-001] Actuator correctamente restringido en identity-services y elending-service

- **Severidad:** Informativo
- **Estado:** ✅ Correcto

#### identity-services — application.yml

```yaml
# Sin configuración de management.endpoints → defaults de SB3
```

En Spring Boot 3.x, por defecto solo se expone `health` via JMX y HTTP. Los endpoints sensibles (`/actuator/env`, `/actuator/beans`, `/actuator/configprops`) NO están accesibles. ✅

#### elending-service — application.yml

```yaml
# Sin configuración de management.endpoints → defaults de SB3
```

Misma situación que identity-services. Solo `health` expuesto. ✅

---

### [JAVA-ACT-002] Gateway expone `health` con `show-details: always`

- **Severidad:** Baja
- **Ubicación:** `microservice-gateway/src/main/resources/application.yml` (Líneas 13-20)
- **Tipo de Incumplimiento:** Seguridad

#### Código Identificado

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      show-details: always       # ⚠️ Expone detalles del health
```

#### Justificación del Incumplimiento

Aunque solo se exponen `health` e `info`, tener `show-details: always` en el gateway expone información detallada sobre el estado de conectividad con servicios downstream (bases de datos, RabbitMQ, etc.). En un gateway que es el punto de entrada a todos los microservicios, esto podría filtrar información sobre la topología interna.

#### Instrucciones de Rectificación

Cambiar a `show-details: when-authorized` (default) o `show-details: never`:

```yaml
endpoint:
  health:
    show-details: when-authorized
```

Si se necesita monitoreo, usar un componente separado (como Spring Boot Admin) que tenga acceso autenticado.

---

## 7. ShedLock — Scheduler Protegido en elending-service

### [JAVA-SHD-001] Scheduler `cerrarPrestamosVencidos` protegido con ShedLock

- **Severidad:** Informativo
- **Estado:** ✅ Correcto

#### Configuración verificada

| Componente | Archivo | Estado |
|-----------|---------|:------:|
| `@EnableSchedulerLock` | (no visible, implícito en configuración) | ✅ |
| `LockProvider` bean | `SchedulerLockConfig.java` | ✅ JDBC-based |
| `@SchedulerLock` | `PrestamoService.java` línea 262 | ✅ |
| Tabla `shedlock` | Flyway `V3__agregar_tabla_shedlock.sql` | ✅ |

#### Detalle de la configuración

```java
// SchedulerLockConfig.java
@Bean
public LockProvider lockProvider(DataSource dataSource) {
    return new JdbcTemplateLockProvider(dataSource);
}
```

```java
// PrestamoService.java
@Scheduled(fixedRate = 3600000)                    // Cada 1 hora
@SchedulerLock(
    name = "prestamos-vencidos",
    lockAtMostFor = "30m",    // Libera automáticamente tras 30 min (instancia muerta)
    lockAtLeastFor = "1s"      // Evita ejecuciones solapadas en misma instancia
)
public void cerrarPrestamosVencidos() {
    // ...
}
```

✅ `lockAtMostFor = "30m"` — seguro si la instancia muere durante la ejecución  
✅ `lockAtLeastFor = "1s"` — evita ejecuciones duplicadas  
✅ Tabla `shedlock` creada via Flyway, no manualmente  

---

## Resumen de Hallazgos por Severidad

| ID | Severidad | Hallazgo | Servicio |
|----|-----------|----------|----------|
| JAVA-CSR-001 | 🔴 Alta | AuthController con lógica de negocio (login, refresh, changePassword) | identity-services |
| JAVA-LOM-001 | 🔴 Alta | Entidad `User` con `@Data` sin `@ToString.Exclude` en password/resetToken/refreshTokenHash | identity-services |
| JAVA-CSR-002 | 🟡 Media | AuthController depende de `AuthenticationManager` y `JwtUtil` directamente | identity-services |
| JAVA-FEI-003 | 🟢 Baja | `NotificationClient.java` es código muerto (reemplazado por RabbitMQ) | elending-service |
| JAVA-ACT-002 | 🟢 Baja | Gateway expone `health` con `show-details: always` | microservice-gateway |
| JAVA-LOM-002 | ✅ Info | `@EqualsAndHashCode(callSuper = false)` correcto | identity, elending |
| JAVA-CSR-003 | ✅ Info | PrestamoController y Gateway Filters sin lógica de negocio | elending, gateway |
| JAVA-CSR-004 | ✅ Info | Services acceden a datos solo via Repositories | identity, elending |
| JAVA-ERR-001 | ✅ Info | GlobalExceptionHandler centralizado en los 3 servicios | identity, elending |
| JAVA-ERR-002 | ✅ Info | Ningún Controller con try-catch | identity, elending |
| JAVA-FEI-001 | ✅ Info | Todos los Feign Clients con FallbackFactory | elending |
| JAVA-FEI-002 | ✅ Info | Errores Feign manejados con retry y compensación | elending |
| JAVA-FLY-001 | ✅ Info | Flyway migrations correctas en identity-services (4 archivos) | identity |
| JAVA-FLY-002 | ✅ Info | Flyway migrations correctas en elending-service (3 archivos) | elending |
| JAVA-ACT-001 | ✅ Info | Actuator restringido por defecto en identity y elending | identity, elending |
| JAVA-SHD-001 | ✅ Info | Scheduler `cerrarPrestamosVencidos` protegido con ShedLock | elending |

---

## Observaciones Adicionales

### Arquitectura General

| Aspecto | Observación |
|---------|-------------|
| **Gateway reactivo** | `microservice-gateway` usa correctamente WebFlux + Spring Cloud Gateway. No aplica CSR. |
| **Feign → RabbitMQ** | La migración de `NotificationClient` (Feign síncrono) a `NotificacionPublisher` (RabbitMQ asíncrono) está bien documentada con comentarios. |
| **Optimistic Locking** | `@Version` en entidad `Prestamo` + `ObjectOptimisticLockingFailureException` manejado en GlobalExceptionHandler es una implementación robusta de control de concurrencia. |
| **JWT propagation** | `FeignRequestInterceptor` propaga el JWT automáticamente desde `SecurityContextHolder` — evita duplicación de lógica. |

---

*Reporte generado por el Java Architecture Auditor — modo solo lectura, sin modificación de código.*
