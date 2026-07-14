# Auditoría de Seguridad Java — SmallBooks Platform

**Auditor:** Java Security Auditor (Secret Service Mode)
**Fecha:** 2026-07-13
**Alcance:** `identity-services`, `microservice-gateway`, `elending-service`
**Enfoque:** Spring Security, JWT, CSRF, CORS, Secretos en texto plano

---

## Resumen Ejecutivo

| Severidad | Count |
|-----------|-------|
| Crítica   | 2     |
| Alta      | 3     |
| Media     | 5     |
| Baja      | 1     |

**Total: 11 hallazgos**

---

## [JAVA-SEC-001] JWT Secret en Texto Plano — Duplicado en Config Server

- **Severidad:** Crítica
- **Ubicación:**
  - `microservice-config/src/main/resources/configurations/identity-service.yml` (Línea 23)
  - `microservice-config/src/main/resources/configurations/msvc-gateway.yml` (Línea 113)
- **Componente Afectado:** Spring Cloud Config / JWT

### Código Inseguro Encontrado

```yaml
# identity-service.yml
jwt:
  secret: Duoc.1983Duoc.1983Duoc.1983Duoc.1983
  access-token-expiration: 1800000
  refresh-token-expiration: 604800000
```

```yaml
# msvc-gateway.yml
jwt:
  secret: Duoc.1983Duoc.1983Duoc.1983Duoc.1983
```

### Instrucciones de Mitigación para el Modelo Ejecutor

1. **NO** almacenar secrets JWT en archivos YAML del repositorio Git.
2. Migrar a variables de entorno o un vault (HashiCorp Vault, AWS Secrets Manager, o Spring Cloud Vault).
3. Cambiar el secret actual inmediatamente — `Duoc.1983` es un patrón institucional predecible (institución + año de fundación), vulnerable a ataques de diccionario. Usar `openssl rand -base64 64` para generar 512 bits de entropía.
4. Configurar en cada servicio:
   ```yaml
   jwt:
     secret: ${JWT_SECRET}
   ```
5. Rotar el secret periódicamente y no compartirlo entre servicios (idealmente cada servicio valida el token con la clave pública del issuer).

---

## [JAVA-SEC-002] JwtExtractor — Parseo de JWT sin Validación de Firma

- **Severidad:** Crítica
- **Ubicación:** `elending-service/src/main/java/com/silvio/elending/security/JwtExtractor.java` (Líneas 11-21)
- **Componente Afectado:** JWT Handling / Autenticación

### Código Inseguro Encontrado

```java
public String extraerUsuario(String authHeader) {
    try {
        String token = authHeader.substring(7);
        String payload = token.split("\\.")[1];
        String decodedPayload = new String(
                java.util.Base64.getUrlDecoder().decode(payload));
        return decodedPayload.split("\"sub\":\"")[1].split("\"")[0];
    } catch (Exception e) {
        throw new RuntimeException("No se pudo extraer el usuario del token");
    }
}
```

### Instrucciones de Mitigación para el Modelo Ejecutor

1. **Eliminar la clase `JwtExtractor`** — No se debe parsear el payload de un JWT sin verificar la firma. Esto permite que un atacante forje tokens arbitrarios.
2. Reemplazar con validación usando `io.jsonwebtoken.Jwts.parserBuilder().setSigningKey(...).build().parseClaimsJws(token)` igual que en `identity-services`.
3. Extraer el `subject` del `Claims` validado:
   ```java
   Claims claims = Jwts.parserBuilder()
       .setSigningKey(getSigningKey())
       .build()
       .parseClaimsJws(token)
       .getBody();
   return claims.getSubject();
   ```
4. Inyectar la clave HMAC compartida (desde variable de entorno, no hardcodeada) o idealmente usar JWKS / RSA pública del gateway.

---

## [JAVA-SEC-003] elending-service: `.anyRequest().permitAll()` sin Autenticación Real

- **Severidad:** Alta
- **Ubicación:** `elending-service/src/main/java/com/silvio/elending/config/SecurityConfig.java` (Línea 29)
- **Componente Afectado:** Spring Security

### Código Inseguro Encontrado

```java
.authorizeHttpRequests(auth -> auth
        .anyRequest().permitAll())
```

Consecuencia: **TODAS las rutas del elending-service están abiertas sin autenticación**, incluso:
- `GET /api/lending/prestamos/todos` — expone todos los préstamos del sistema
- `GET /api/lending/prestamos/historial/{usuarioId}` — permite consultar historial de cualquier usuario

Si alguien accede al servicio directamente (puerto 8087, sin pasar por el gateway), no hay protección.

### Instrucciones de Mitigación para el Modelo Ejecutor

1. Reemplazar `permitAll()` con requerimiento de autenticación:
   ```java
   .authorizeHttpRequests(auth -> auth
           .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
           .anyRequest().authenticated())
   ```
2. El `JwtAuthenticationFilter` en elending-service debe **validar la firma del token** (no solo extraerlo), de modo que si el gateway falla o es bypaseado, el servicio sigue protegido.
3. Para los endpoints internos `/prestamos/todos` y `/prestamos/historial/{usuarioId}`, agregar restricción por rol:
   ```java
   .requestMatchers("/api/lending/prestamos/todos").hasRole("ADMIN")
   ```

---

## [JAVA-SEC-004] Endpoints Internos Expuestos sin Autenticación

- **Severidad:** Alta
- **Ubicación:**
  - `elending-service/src/main/java/com/silvio/elending/controller/PrestamoController.java` (Líneas 106-113, 122-131)
- **Componente Afectado:** API / Authorization

### Código Inseguro Encontrado

```java
@GetMapping("/prestamos/todos")
public ResponseEntity<List<PrestamoResponseDTO>> obtenerTodos() {
    List<PrestamoResponseDTO> prestamos = prestamoService.obtenerTodos();
    // ... sin validación de autenticación ni roles
}

@GetMapping("/prestamos/historial/{usuarioId}")
public ResponseEntity<List<PrestamoResponseDTO>> obtenerHistorialPorId(
        @PathVariable String usuarioId) {
    List<PrestamoResponseDTO> prestamos = prestamoService.obtenerHistorial(usuarioId);
    // ... sin validación de autenticación ni roles
}
```

### Instrucciones de Mitigación para el Modelo Ejecutor

1. Proteger `obtenerTodos()` con `@PreAuthorize("hasRole('ADMIN')")` o restricción a IP de internal-network.
2. Proteger `obtenerHistorialPorId()` con verificación de que el usuario autenticado sea el propietario o tenga rol ADMIN:
   ```java
   @PreAuthorize("authentication.name == #usuarioId or hasRole('ROLE_ADMIN')")
   ```
3. Agregar `@EnableMethodSecurity` en `SecurityConfig` para habilitar `@PreAuthorize`.

---

## [JAVA-SEC-005] Contraseña de Base de Datos en Blanco en Todos los Servicios

- **Severidad:** Alta
- **Ubicación:**
  - `microservice-config/src/main/resources/configurations/identity-service.yml` (Línea 10)
  - `microservice-config/src/main/resources/configurations/elending-service.yml` (Línea 13)
  - (y 5 servicios adicionales — patrón replicado)
- **Componente Afectado:** Spring DataSource / Infraestructura

### Código Inseguro Encontrado

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/db_identity?useSSL=false&serverTimezone=UTC
    username: root
    password:
    driver-class-name: com.mysql.cj.jdbc.Driver
```

### Instrucciones de Mitigación para el Modelo Ejecutor

1. Configurar la contraseña de base de datos exclusivamente mediante variables de entorno:
   ```yaml
   spring:
     datasource:
       password: ${DB_PASSWORD}
   ```
2. Para desarrollo local, usar un archivo `.env` fuera del repositorio Git.
3. Agregar `spring.datasource.password` al `.gitignore` de los archivos de configuración local.

---

## [JAVA-SEC-006] JwtUtil — Clave de Firma sin Validación de Longitud Mínima

- **Severidad:** Media
- **Ubicación:** `identity-services/src/main/java/com/silvio/identity/security/JwtUtil.java` (Líneas 25-28)
- **Componente Afectado:** JWT

### Código Inseguro Encontrado

```java
private Key getSigningKey() {
    byte[] keyBytes = jwtProperties.getSecret().getBytes();
    return Keys.hmacShaKeyFor(keyBytes);
}
```

### Instrucciones de Mitigación para el Modelo Ejecutor

1. Validar que la clave tenga al menos 256 bits (32 bytes) para HS256 antes de usarla:
   ```java
   private Key getSigningKey() {
       byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
       if (keyBytes.length < 32) {
           throw new IllegalArgumentException(
               "JWT secret must be at least 256 bits (32 characters) for HS256");
       }
       return Keys.hmacShaKeyFor(keyBytes);
   }
   ```
2. Especificar explícitamente `StandardCharsets.UTF_8` en `getBytes()` para evitar dependencia del charset por defecto de la plataforma.

---

## [JAVA-SEC-007] Refresh Token sin Rotación ni Revocación

- **Severidad:** Media
- **Ubicación:**
  - `identity-services/src/main/java/com/silvio/identity/controller/AuthController.java` (Líneas 91-115)
  - `identity-services/src/main/java/com/silvio/identity/security/JwtUtil.java` (Líneas 47-58)
- **Componente Afectado:** JWT / Autenticación

### Código Inseguro Encontrado

```java
public String generateRefreshToken(String username) {
    // ...
    .setExpiration(new Date(System.currentTimeMillis() + jwtProperties.getRefreshTokenExpiration()))
    .signWith(getSigningKey(), SignatureAlgorithm.HS256)
    .compact();
}
```

El refresh token:
- Tiene validez de **7 días** sin posibilidad de revocación
- No se invalida al usarlo (no hay rotación)
- Si es robado, un atacante puede refrescar sesiones por 7 días

### Instrucciones de Mitigación para el Modelo Ejecutor

1. Implementar **rotación de refresh tokens**: al usar un refresh token, invalidarlo y generar uno nuevo.
   ```java
   // En el refresh endpoint:
   // 1. Invalidar el refresh token anterior (blacklist en DB/Redis)
   // 2. Generar nuevo access + refresh token
   // 3. Si el token ya fue usado (replay detection) → revocar TODOS los tokens del usuario
   ```
2. Implementar **blacklist de refresh tokens** en Redis con expiración automática (TTL = refresh token TTL).
3. Almacenar un `jti` (JWT ID) en los refresh tokens para identificar tokens individuales en la blacklist.

---

## [JAVA-SEC-008] Excepción Handler Expone Mensajes Internos de Negocio

- **Severidad:** Media
- **Ubicación:** `identity-services/src/main/java/com/silvio/identity/exception/GlobalExceptionHandler.java` (Líneas 44-58)
- **Componente Afectado:** Exception Handling / Information Disclosure

### Código Inseguro Encontrado

```java
@ExceptionHandler(RuntimeException.class)
public ResponseEntity<Map<String, String>> manejarRuntimeException(RuntimeException ex) {
    Map<String, String> error = new HashMap<>();
    error.put("error", ex.getMessage());  // Expone mensaje interno al cliente
    // ...
}
```

### Instrucciones de Mitigación para el Modelo Ejecutor

1. No exponer `ex.getMessage()` directamente al cliente. Usar mensajes genéricos y loguear el detalle:
   ```java
   @ExceptionHandler(RuntimeException.class)
   public ResponseEntity<Map<String, String>> manejarRuntimeException(RuntimeException ex) {
       log.error("Error interno: ", ex);
       Map<String, String> error = new HashMap<>();
       error.put("error", "Error interno del servidor");
       return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
   }
   ```
2. Para excepciones controladas (como "usuario ya existe"), usar excepciones específicas con `@ResponseStatus`.

---

## [JAVA-SEC-009] Conexiones JDBC con SSL Deshabilitado

- **Severidad:** Media
- **Ubicación:** Múltiples archivos en `microservice-config/src/main/resources/configurations/` (7 servicios)
- **Componente Afectado:** Spring DataSource

### Código Inseguro Encontrado

```yaml
url: jdbc:mysql://localhost:3306/db_identity?useSSL=false&serverTimezone=UTC
```

### Instrucciones de Mitigación para el Modelo Ejecutor

1. Habilitar SSL para conexiones JDBC en entornos que no sean localhost:
   ```yaml
   url: jdbc:mysql://${DB_HOST}:3306/db_identity?useSSL=true&requireSSL=true&serverTimezone=UTC
   ```
2. Configurar el truststore con el certificado del servidor MySQL.
3. Para desarrollo local sobre `localhost`, `useSSL=false` es aceptable, pero debe sobrescribirse en producción vía variables de entorno.

---

## [JAVA-SEC-010] forgot-password Expone el Reset Token en la Respuesta

- **Severidad:** Media
- **Ubicación:** `identity-services/src/main/java/com/silvio/identity/controller/AuthController.java` (Líneas 125-133)
- **Componente Afectado:** API / Password Management

### Código Inseguro Encontrado

```java
@PostMapping("/forgot-password")
public ResponseEntity<?> forgotPassword(@Valid @RequestBody PasswordResetRequest request) {
    String resetToken = userService.createPasswordResetToken(request.getUsername());
    return ResponseEntity.ok(Map.of(
        "message", " Si el usuario existe, se ha generado un token de recuperación",
        "resetToken", resetToken,       // <-- Expone el token en la respuesta HTTP
        "instruction", "Usa este token en POST /auth/reset-password con tu nueva contraseña"
    ));
}
```

### Instrucciones de Mitigación para el Modelo Ejecutor

1. En producción, **nunca** devolver el reset token en la respuesta HTTP. Enviarlo por email o canal seguro.
2. Para desarrollo, se puede mantener pero agregar un flag `app.development-mode` que controle esta conducta:
   ```java
   if (appProperties.isDevelopmentMode()) {
       response.put("resetToken", resetToken);
   }
   ```
3. Idealmente, implementar un endpoint separado para desarrollo que permita obtener el token bajo autenticación admin.

---

## [JAVA-SEC-011] CORS No Configurado (Ausencia de Control)

- **Severidad:** Baja
- **Ubicación:**
  - `identity-services/src/main/java/com/silvio/identity/config/SecurityConfig.java`
  - `elending-service/src/main/java/com/silvio/elending/config/SecurityConfig.java`
- **Componente Afectado:** Spring Security / CORS

### Código Inseguro Encontrado

```java
// SecurityConfig.java en identity-services — sin .cors()
http
    .csrf(csrf -> csrf.disable())
    // No hay configuración CORS
```

### Instrucciones de Mitigación para el Modelo Ejecutor

1. Agregar configuración CORS explícita en ambos SecurityConfigs para evitar comportamientos default del contenedor:
   ```java
   @Bean
   public CorsConfigurationSource corsConfigurationSource() {
       CorsConfiguration configuration = new CorsConfiguration();
       configuration.setAllowedOrigins(Arrays.asList(
           System.getenv("ALLOWED_ORIGINS").split(",")));
       configuration.setAllowedMethods(Arrays.asList("GET","POST","PUT","DELETE"));
       configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
       configuration.setAllowCredentials(true);
       
       UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
       source.registerCorsConfiguration("/**", configuration);
       return source;
   }
   ```
2. **No usar** `allowedOrigins("*")` con `allowCredentials(true)` — eso permite cualquier origen con credenciales (violación CORS).

---

## Checklist de Verificación Adicional

| Item | Estado | Comentario |
|------|--------|------------|
| Versión Spring Boot | OK | `3.3.11` — soportada hasta Nov 2025, CVEs conocidos parcheados |
| Versión Spring Cloud | OK | `2023.0.5` — compatible con Boot 3.3.x |
| jjwt 0.11.5 | ⚠️ Anticuado | jjwt 0.12.x tiene mejoras de seguridad. 0.11.5 usa `io.jsonwebtoken` legacy APIs |
| Bucket4j 7.6.0 | OK | Versión estable reciente |
| ShedLock 5.13.0 | OK | Versión estable |
| BCryptPasswordEncoder | OK | Buena práctica |
| Logging de contraseñas | OK | No se loggean contraseñas (solo usernames) |
| SpringDoc OpenAPI | OK | `2.5.0` |
| JaCoCo 0.8.11 | OK | Versión estable |

---

## Recomendaciones Prioritarias (Orden de Ejecución)

1. **[CRÍTICO]** Reemplazar JWT secret hardcodeado por variable de entorno + cambiar clave inmediatamente
2. **[CRÍTICO]** Eliminar `JwtExtractor` y reemplazar por validación de firma JWT en elending-service
3. **[ALTA]** Reemplazar `permitAll()` por `authenticated()` en elending-service SecurityConfig
4. **[ALTA]** Configurar contraseñas de base de datos via entorno (no vacías)
5. **[ALTA]** Proteger endpoints internos `/prestamos/todos` y `/prestamos/historial/{usuarioId}`
6. **[MEDIA]** Implementar rotación de refresh tokens
7. **[MEDIA]** Sanitizar mensajes de error en GlobalExceptionHandler
8. **[MEDIA]** Habilitar SSL en conexiones JDBC para entornos no-locales
9. **[MEDIA]** No exponer resetToken en forgot-password response
10. **[BAJA]** Agregar configuración CORS explícita

---

*Reporte generado por Java Security Auditor. Este es un proyecto académico — se priorizaron vulnerabilidades aplicables al contexto educativo.*
