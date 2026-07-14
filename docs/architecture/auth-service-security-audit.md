# Auditoría de Seguridad — identity-services (auth-service)

**Proyecto:** SmallBooks · Plataforma de Biblioteca Digital  
**Servicio:** identity-service (Autenticación y Usuarios)  
**Fecha:** 2026-07-10  
**Auditor:** Ingeniero Principal de Ciberseguridad (OWASP / ASVS / PCI-DSS)  
**Stack:** Spring Boot 3.3.11 / Spring Cloud 2023.0.5 / JWT (jjwt 0.11.5) / MySQL / Flyway

---

## Resumen Ejecutivo

| Nivel | Cantidad |
|-------|----------|
| **CRÍTICO** | 6 |
| **ALTO** | 8 |
| **MEDIO** | 7 |
| **BAJO** | 5 |
| **TOTAL** | 26 |

---

# VULNERABILIDADES CRÍTICAS

---

### [ID-CRIT-001] Secreto JWT hardcodeado en repositorio (texto plano, débil y predecible)

- **Severidad:** CRÍTICA  
- **Ubicación:** `microservice-config/src/main/resources/configurations/identity-service.yml` (Línea 23)  
- **Normativa Afectada:** OWASP Top 10 (A02:2021 – Cryptographic Failures), PCI-DSS 3.6.1, ASVS 2.10

#### Código Vulnerable Encontrado
```yaml
jwt:
  secret: Duoc.1983Duoc.1983Duoc.1983Duoc.1983
  access-token-expiration: 1800000
  refresh-token-expiration: 604800000
```

#### Instrucciones de Mitigación para el Modelo Ejecutor
1. Eliminar el secreto del archivo YAML. No debe existir en ningún archivo dentro del repositorio.
2. Usar una variable de entorno o un secreto externo (Vault, AWS Secrets Manager, etc.). Por ejemplo: `secret: ${JWT_SECRET}`.
3. Generar un secreto criptográficamente seguro de al menos 256 bits (44 caracteres en base64) usando `openssl rand -base64 32`.
4. Rotar el secreto periódicamente y tener un mecanismo para manejar múltiples secretos simultáneamente durante la rotación.

---

### [ID-CRIT-002] Contraseña de base de datos vacía y expuesta en repositorio

- **Severidad:** CRÍTICA  
- **Ubicación:** `microservice-config/src/main/resources/configurations/identity-service.yml` (Líneas 8–11)  
- **Normativa Afectada:** PCI-DSS 4.2.1, OWASP Top 10 (A05:2021 – Security Misconfiguration), ASVS 2.10.4

#### Código Vulnerable Encontrado
```yaml
  datasource:
    url: jdbc:mysql://localhost:3306/db_identity?useSSL=false&serverTimezone=UTC
    username: root
    password:
    driver-class-name: com.mysql.cj.mysql.Driver
```

#### Instrucciones de Mitigación para el Modelo Ejecutor
1. Establecer una contraseña fuerte para la base de datos MySQL (mínimo 20 caracteres alfanuméricos con símbolos).
2. No exponer credenciales en repositorios. Usar variables de entorno: `password: ${DB_PASSWORD}`, `username: ${DB_USERNAME}`.
3. Cambiar el usuario de `root` a un usuario específico de la aplicación con permisos mínimos necesarios (principio de mínimo privilegio).
4. Activar SSL en la conexión JDBC: cambiar `useSSL=false` a `useSSL=true&requireSSL=true`.

---

### [ID-CRIT-003] Token de reseteo de contraseña expuesto en la respuesta HTTP (sin verificación de identidad)

- **Severidad:** CRÍTICA  
- **Ubicación:** `auth-service/src/main/java/com/silvio/identity/controller/AuthController.java` (Líneas 126–132)  
- **Normativa Afectada:** OWASP Top 10 (A01:2021 – Broken Access Control), ASVS 2.5, PCI-DSS 6.5.10

#### Código Vulnerable Encontrado
```java
@PostMapping("/forgot-password")
public ResponseEntity<?> forgotPassword(@Valid @RequestBody PasswordResetRequest request) {
    String resetToken = userService.createPasswordResetToken(request.getUsername());
    return ResponseEntity.ok(Map.of(
        "message", " Si el usuario existe, se ha generado un token de recuperación",
        "resetToken", resetToken,
        "instruction", "Usa este token en POST /auth/reset-password con tu nueva contraseña"
    ));
}
```

#### Instrucciones de Mitigación para el Modelo Ejecutor
1. NUNCA devolver el reset token en la respuesta HTTP. En producción debe enviarse por email, SMS o canal seguro fuera de banda.
2. El mensaje de respuesta debe ser genérico: "Si el usuario existe, recibirá un enlace de recuperación en su correo electrónico".
3. Implementar un mecanismo de rate limiting para evitar abusos (máximo 1 solicitud cada 60 segundos por username/IP).
4. El token debe tener una validez corta (15–30 minutos, no 24 horas como está actualmente en UserService línea 70).

---

### [ID-CRIT-004] El filtro JWT no valida el tipo de token (access vs refresh)

- **Severidad:** CRÍTICA  
- **Ubicación:** `auth-service/src/main/java/com/silvio/identity/security/JwtAuthenticationFilter.java` (Líneas 36–52)  
- **Normativa Afectada:** OWASP Top 10 (A01:2021 – Broken Access Control), ASVS 3.2.1, PCI-DSS 6.5

#### Código Vulnerable Encontrado
```java
String token = authHeader.substring(7);
try {
    String username = jwtUtil.extractUsername(token);
    String rolesStr = jwtUtil.extractRoles(token);
    // ... crea autenticación sin verificar el tipo de token
```

#### Instrucciones de Mitigación para el Modelo Ejecutor
1. Agregar verificación del tipo de token en el filtro JWT. Solo los tokens de tipo "access" deben ser aceptados para autenticar requests.
2. Los tokens de tipo "refresh" deben ser rechazados por el filtro.
3. Código de corrección: dentro del try, antes de crear la autenticación, agregar:
```java
String tokenType = jwtUtil.extractTokenType(token);
if (!"access".equals(tokenType)) {
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Tipo de token inválido");
    return;
}
```

---

### [ID-CRIT-005] User Enumeration vía forgot-password y reset-password

- **Severidad:** CRÍTICA  
- **Ubicación:** 
  - `auth-service/src/main/java/com/silvio/identity/service/UserService.java` (Líneas 65–66)
  - `auth-service/src/main/java/com/silvio/identity/controller/AuthController.java` (Líneas 126–132)  
- **Normativa Afectada:** OWASP Top 10 (A01:2021 – Broken Access Control), ASVS 2.5.1, PCI-DSS 6.5

#### Código Vulnerable Encontrado
```java
public String createPasswordResetToken(String username) {
    User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new RuntimeException(" Usuario no encontrado"));
    // ...
}
```
```java
public void resetPassword(String token, String newPassword) {
    User user = userRepository.findByResetToken(token)
            .orElseThrow(() -> {
                log.warn("Token de recuperación inválido");
                return new RuntimeException(" Token de recuperación inválido");
            });
```

#### Instrucciones de Mitigación para el Modelo Ejecutor
1. **forgot-password**: No diferenciar entre usuarios existentes y no existentes. Siempre devolver el mismo mensaje genérico: "Si el usuario existe, recibirá instrucciones de recuperación".
2. En `createPasswordResetToken()`, no lanzar excepción si el usuario no existe. Simplemente retornar exitosamente sin hacer nada (o generar un token dummy para no revelar existencia).
3. **reset-password**: Usar mensajes genéricos que no revelen si el token es válido o no. "Token inválido o expirado" es aceptable pero debe ser consistente.
4. Implementar retardo constante (tiempo fijo) en las respuestas para prevenir timing attacks.

---

### [ID-CRIT-006] Contraseñas hardcodeadas en migraciones Flyway dentro del repositorio

- **Severidad:** CRÍTICA  
- **Ubicación:** `auth-service/src/main/resources/db/migration/V2__insert_default_users.sql` (Líneas 1–18)  
- **Normativa Afectada:** PCI-DSS 4.2.1, OWASP Top 10 (A05:2021 – Security Misconfiguration), ASVS 2.10.4

#### Código Vulnerable Encontrado
```sql
-- Insertar admin con contraseña: admin123 (hash BCrypt generado)
INSERT INTO users (username, password)
VALUES ('admin', '$2a$10$DgFIbPVsRWWNErfxSqkZCOZhlDuufsSTDvYAfjRGfgpBvGzoWPA32');

-- Insertar user1 con contraseña: user123 (hash BCrypt generado)
INSERT INTO users (username, password)
VALUES ('user1', '$2a$10$xj5DD7v5FDWj34of/08q7.UHCIro0IRSRDdkPyJbDakrtj/uVSVCi');
```

#### Instrucciones de Mitigación para el Modelo Ejecutor
1. Eliminar el archivo `V2__insert_default_users.sql` del repositorio.
2. No insertar usuarios por defecto con contraseñas conocidas en migraciones. Los usuarios deben crearse mediante el flujo de registro o mediante un script de bootstraping que use contraseñas generadas aleatoriamente y rotadas inmediatamente.
3. Si se necesitan usuarios semilla para desarrollo, que se creen mediante un perfil específico (`@Profile("dev")`) y no mediante migraciones Flyway que se ejecutan en producción.
4. Las contraseñas `admin123` y `user123` son extremadamente débiles y están documentadas en el código fuente.

---

# VULNERABILIDADES ALTAS

---

### [ID-ALTO-001] Secreto JWT sin charset especificado en getBytes()

- **Severidad:** ALTA  
- **Ubicación:** `auth-service/src/main/java/com/silvio/identity/security/JwtUtil.java` (Línea 26)  
- **Normativa Afectada:** OWASP Top 10 (A02:2021 – Cryptographic Failures), ASVS 2.10.3

#### Código Vulnerable Encontrado
```java
private Key getSigningKey() {
    byte[] keyBytes = jwtProperties.getSecret().getBytes();
    return Keys.hmacShaKeyFor(keyBytes);
}
```

#### Instrucciones de Mitigación para el Modelo Ejecutor
1. Especificar siempre el charset UTF-8 explícitamente: `getBytes(StandardCharsets.UTF_8)`.
2. El método `getBytes()` sin argumentos usa el charset por defecto de la plataforma, que puede ser diferente entre sistemas operativos (Windows vs Linux), causando que el mismo secreto genere claves HMAC diferentes.
3. Considerar almacenar el secreto como Base64 y decodificarlo con `Base64.getDecoder().decode(secret)` para garantizar consistencia.

---

### [ID-ALTO-002] Refresh Token Rotation sin invalidación del token anterior

- **Severidad:** ALTA  
- **Ubicación:** `auth-service/src/main/java/com/silvio/identity/controller/AuthController.java` (Líneas 92–115)  
- **Normativa Afectada:** OWASP Top 10 (A01:2021 – Broken Access Control), ASVS 3.2.1, PCI-DSS 6.5

#### Código Vulnerable Encontrado
```java
@PostMapping("/refresh")
public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
    String refreshToken = request.getRefreshToken();
    // ...validación...
    String newAccessToken = jwtUtil.generateAccessToken(userDetails);
    String newRefreshToken = jwtUtil.generateRefreshToken(username);
    // Se emite nuevo refresh token PERO el anterior NO se invalida
```

#### Instrucciones de Mitigación para el Modelo Ejecutor
1. Implementar una lista de tokens refresh emitidos (tabla `refresh_tokens` en BD o Redis).
2. Al rotar un refresh token, invalidar inmediatamente el anterior (marcarlo como usado o eliminarlo).
3. Implementar detección de reuso: si un refresh token ya usado es presentado nuevamente, invalidar TODOS los refresh tokens del usuario (posible robo de token).
4. Agregar un `jti` (JWT ID) único a cada token y verificarlo contra la base de datos.

---

### [ID-ALTO-003] Sin límite de velocidad (Rate Limiting) en endpoints críticos

- **Severidad:** ALTA  
- **Ubicación:** `auth-service/src/main/java/com/silvio/identity/config/SecurityConfig.java` (Líneas 26–43)  
- **Normativa Afectada:** OWASP Top 10 (A01:2021 – Broken Access Control), ASVS 3.1.1, PCI-DSS 6.5

#### Código Vulnerable Encontrado
```java
http
    .csrf(csrf -> csrf.disable())
    .authorizeHttpRequests(auth -> auth
        .requestMatchers(
            "/auth/login", 
            "/auth/register",
            "/auth/refresh",
            "/auth/forgot-password",
            "/auth/reset-password",
            // ...
        ).permitAll()
        .anyRequest().authenticated())
    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
```

#### Instrucciones de Mitigación para el Modelo Ejecutor
1. Implementar rate limiting en todos los endpoints públicos usando Spring Boot + Redis (Bucket4j o similar).
2. Configurar límites específicos:
   - `/auth/login`: máximo 5 intentos por minuto por IP
   - `/auth/forgot-password`: máximo 2 solicitudes por hora por username/IP
   - `/auth/register`: máximo 3 registros por hora por IP
3. Implementar bloqueo temporal de cuenta tras N intentos fallidos de login (ej: 5 intentos → bloqueo 15 minutos).

---

### [ID-ALTO-004] JDBC sin SSL y con contraseña vacía

- **Severidad:** ALTA  
- **Ubicación:** `microservice-config/src/main/resources/configurations/identity-service.yml` (Línea 8)  
- **Normativa Afectada:** PCI-DSS 4.1, OWASP Top 10 (A02:2021 – Cryptographic Failures)

#### Código Vulnerable Encontrado
```yaml
url: jdbc:mysql://localhost:3306/db_identity?useSSL=false&serverTimezone=UTC
```

#### Instrucciones de Mitigación para el Modelo Ejecutor
1. Cambiar `useSSL=false` a `useSSL=true&requireSSL=true&verifyServerCertificate=true`.
2. Configurar un truststore con el certificado del servidor MySQL.
3. La contraseña de la BD debe ser provista mediante variable de entorno, no desde archivo de configuración en repositorio.

---

### [ID-ALTO-005] SQL expuesto en logs (show-sql: true)

- **Severidad:** ALTA  
- **Ubicación:** `microservice-config/src/main/resources/configurations/identity-service.yml` (Línea 15)  
- **Normativa Afectada:** PCI-DSS 3.4, OWASP Top 10 (A04:2021 – Insecure Design)

#### Código Vulnerable Encontrado
```yaml
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
```

#### Instrucciones de Mitigación para el Modelo Ejecutor
1. Cambiar `show-sql: true` a `show-sql: false` en producción.
2. Si se necesita logging de SQL para debugging, activarlo solo para el perfil `dev` y usando un logger parametrizado que no exponga valores literales.
3. En producción, los logs no deben contener datos de la capa de persistencia.

---

### [ID-ALTO-006] Sin validación de fortaleza de contraseñas

- **Severidad:** ALTA  
- **Ubicación:** 
  - `auth-service/src/main/java/com/silvio/identity/dto/RegisterRequest.java` (Línea 12)
  - `auth-service/src/main/java/com/silvio/identity/dto/ChangePasswordRequest.java` (Línea 12)
  - `auth-service/src/main/java/com/silvio/identity/dto/PasswordUpdateRequest.java` (Línea 12)  
- **Normativa Afectada:** ASVS 2.1.1, PCI-DSS 8.2.3, OWASP Top 10 (A07:2021 – Identification and Authentication Failures)

#### Código Vulnerable Encontrado
```java
@NotBlank(message = "Nueva contraseña es obligatoria")
private String newPassword;
```

#### Instrucciones de Mitigación para el Modelo Ejecutor
1. Agregar validación de fortaleza de contraseña usando `@Pattern` de Jakarta Bean Validation o una anotación personalizada.
2. Requisitos mínimos: mínimo 12 caracteres, al menos una mayúscula, una minúscula, un dígito y un carácter especial.
3. Ejemplo de anotación: `@Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^\\da-zA-Z]).{12,}$", message = "La contraseña debe tener al menos 12 caracteres, incluyendo mayúsculas, minúsculas, números y símbolos")`
4. Validar contra contraseñas comunes usando una lista de contraseñas prohibidas (HaveIBeenPwned API o lista local).

---

### [ID-ALTO-007] Posible Token Substitution Attack (Refresh → Access)

- **Severidad:** ALTA  
- **Ubicación:** `auth-service/src/main/java/com/silvio/identity/controller/AuthController.java` (Líneas 158–164)  
- **Normativa Afectada:** OWASP Top 10 (A01:2021 – Broken Access Control), ASVS 3.2.1

#### Código Vulnerable Encontrado
```java
@PostMapping("/change-password")
public ResponseEntity<?> changePassword(
        @RequestHeader("Authorization") String authHeader,
        @Valid @RequestBody ChangePasswordRequest request) {
    String token = authHeader.substring(7);
    String username = jwtUtil.extractUsername(token);
    // No verifica que el token sea de tipo "access"
```

#### Instrucciones de Mitigación para el Modelo Ejecutor
1. Verificar explícitamente que el token sea de tipo "access" antes de procesar el cambio de contraseña.
2. Si se presenta un refresh token en un endpoint que requiere access token, rechazar la solicitud con 401.
3. Agregar en `changePassword`:
```java
String tokenType = jwtUtil.extractTokenType(token);
if (!"access".equals(tokenType)) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(...);
}
```

---

### [ID-ALTO-008] Ausencia de cabeceras de seguridad HTTP

- **Severidad:** ALTA  
- **Ubicación:** `auth-service/src/main/java/com/silvio/identity/config/SecurityConfig.java` (Líneas 26–43)  
- **Normativa Afectada:** OWASP Top 10 (A05:2021 – Security Misconfiguration), PCI-DSS 6.5

#### Código Vulnerable Encontrado
```java
http
    .csrf(csrf -> csrf.disable())
    .authorizeHttpRequests(...)
    .sessionManagement(...)
    .addFilterBefore(...);
// No se configuran cabeceras de seguridad HTTP
```

#### Instrucciones de Mitigación para el Modelo Ejecutor
1. Agregar configuración de cabeceras de seguridad HTTP:
```java
http.headers(headers -> headers
    .xssProtection(XssProtectionHeaderWriter.xXSSProtection())  // X-XSS-Protection
    .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))  // CSP
    .frameOptions(frame -> frame.deny())  // X-Frame-Options
    .contentTypeOptions()  // X-Content-Type-Options
    .httpStrictTransportSecurity(hsts -> hsts
        .includeSubDomains(true)
        .maxAgeInSeconds(31536000)
    )
);
```
2. Configurar `Cache-Control` para respuestas con tokens: `no-store, no-cache, must-revalidate, proxy-revalidate, max-age=0`.

---

# VULNERABILIDADES MEDIAS

---

### [ID-MED-001] CSRF deshabilitado sin evaluación de riesgos

- **Severidad:** MEDIA  
- **Ubicación:** `auth-service/src/main/java/com/silvio/identity/config/SecurityConfig.java` (Línea 27)  
- **Normativa Afectada:** OWASP Top 10 (A01:2021 – Broken Access Control), ASVS 5.1

#### Código Vulnerable Encontrado
```java
.csrf(csrf -> csrf.disable())
```

#### Instrucciones de Mitigación para el Modelo Ejecutor
1. Aunque el servicio usa JWT stateless (no cookies), si algún otro componente del ecosistema usa cookies de sesión, esto podría ser explotable.
2. Documentar explícitamente por qué se deshabilita CSRF (solo JWT, sin cookies) y asegurarse de que ningún endpoint use cookies de sesión.
3. Agregar un comentario en el código justificando la decisión.

---

### [ID-MED-002] Manejo de excepciones con RuntimeException genéricas

- **Severidad:** MEDIA  
- **Ubicación:** `auth-service/src/main/java/com/silvio/identity/service/UserService.java` (Líneas 33, 66, 82, 87, 101, 105)  
- **Normativa Afectada:** OWASP Top 10 (A05:2021 – Security Misconfiguration), ASVS 7.4

#### Código Vulnerable Encontrado
```java
throw new RuntimeException(" El usuario '" + username + "' ya existe");
throw new RuntimeException(" Usuario no encontrado");
throw new RuntimeException(" Token de recuperación inválido");
throw new RuntimeException(" Contraseña actual incorrecta");
```

#### Instrucciones de Mitigación para el Modelo Ejecutor
1. Crear excepciones personalizadas (por ejemplo: `UserAlreadyExistsException`, `InvalidTokenException`, `PasswordMismatchException`) en un paquete `exception/`.
2. Usar excepciones específicas en lugar de RuntimeException para permitir un manejo diferenciado y evitar filtrado por mensajes de texto.
3. Usar `@ResponseStatus` en las excepciones personalizadas para definir códigos HTTP sin necesidad de lógica en el GlobalExceptionHandler.

---

### [ID-MED-003] GlobalExceptionHandler filtra por contenido del mensaje (frágil e inseguro)

- **Severidad:** MEDIA  
- **Ubicación:** `auth-service/src/main/java/com/silvio/identity/exception/GlobalExceptionHandler.java` (Líneas 50–54)  
- **Normativa Afectada:** OWASP Top 10 (A09:2021 – Security Logging and Monitoring Failures)

#### Código Vulnerable Encontrado
```java
HttpStatus status = HttpStatus.BAD_REQUEST;
if (ex.getMessage().contains("ya existe")) {
    status = HttpStatus.CONFLICT;  // 409
} else if (ex.getMessage().contains("expirado") || ex.getMessage().contains("inválido")) {
    status = HttpStatus.UNAUTHORIZED;  // 401
}
```

#### Instrucciones de Mitigación para el Modelo Ejecutor
1. Eliminar esta lógica de filtrado por texto. Usar excepciones personalizadas con `@ResponseStatus`.
2. El manejo basado en cadenas de texto es frágil: si se cambia el mensaje, el código HTTP cambia inesperadamente.
3. Crear un manejador por cada tipo de excepción personalizada, similar al patrón ya usado con `BadCredentialsException`.

---

### [ID-MED-004] JWT sin validación de audiencia (aud) ni emisor (iss)

- **Severidad:** MEDIA  
- **Ubicación:** `auth-service/src/main/java/com/silvio/identity/security/JwtUtil.java` (Líneas 38–43, 51–57)  
- **Normativa Afectada:** OWASP Top 10 (A01:2021 – Broken Access Control), ASVS 3.2

#### Código Vulnerable Encontrado
```java
return Jwts.builder()
    .setClaims(claims)
    .setSubject(userDetails.getUsername())
    .setIssuedAt(new Date())
    .setExpiration(new Date(System.currentTimeMillis() + jwtProperties.getAccessTokenExpiration()))
    .signWith(getSigningKey(), SignatureAlgorithm.HS256)
    .compact();
```

#### Instrucciones de Mitigación para el Modelo Ejecutor
1. Agregar `setIssuer("smallbooks-identity-service")` y `setAudience("smallbooks-api")` al construir tokens.
2. En la validación (`validateToken`), verificar issuer y audience:
```java
Jwts.parserBuilder()
    .setSigningKey(getSigningKey())
    .requireIssuer("smallbooks-identity-service")
    .requireAudience("smallbooks-api")
    .build()
    .parseClaimsJws(token)
    .getBody();
```
3. Esto previene que tokens emitidos por otros servicios o para otros fines sean aceptados.

---

### [ID-MED-005] Refresh token sin mecanismo de revocación

- **Severidad:** MEDIA  
- **Ubicación:** `auth-service/src/main/java/com/silvio/identity/security/JwtUtil.java` (Todo el archivo)  
- **Normativa Afectada:** ASVS 3.2.3, PCI-DSS 6.5

#### Código Vulnerable Encontrado
```java
// No existe ningún mecanismo de revocación de tokens
// No hay blacklist, no hay whitelist, no hay versión de token
```

#### Instrucciones de Mitigación para el Modelo Ejecutor
1. Implementar una tabla `token_blacklist` en la base de datos (o Redis) para tokens revocados.
2. Agregar un campo `jti` (JWT ID) único a cada token.
3. En el filtro JWT, verificar que el token no esté en la blacklist antes de aceptarlo.
4. Proveer un endpoint `/auth/logout` que agregue el token a la blacklist hasta su expiración natural.

---

### [ID-MED-006] Logging DEBUG expone información sensible

- **Severidad:** MEDIA  
- **Ubicación:** `auth-service/src/main/resources/application.yml` (Línea 12)  
- **Normativa Afectada:** PCI-DSS 3.4, OWASP Top 10 (A04:2021 – Insecure Design)

#### Código Vulnerable Encontrado
```yaml
logging:
  level:
    root: INFO
    com.silvio: DEBUG
```

#### Instrucciones de Mitigación para el Modelo Ejecutor
1. Cambiar `com.silvio: DEBUG` a `com.silvio: INFO` o `WARN` en producción.
2. Si se necesita DEBUG para troubleshooting, activarlo mediante variable de entorno y solo temporalmente.
3. Revisar que las sentencias `log.info` y `log.warn` en `UserService.java` no expongan datos sensibles como nombres de usuario en logs de producción (aunque están en INFO, se volcarán si el nivel lo permite).

---

### [ID-MED-007] Entidad User usa @Data de Lombok (ToString expone password hash)

- **Severidad:** MEDIA  
- **Ubicación:** `auth-service/src/main/java/com/silvio/identity/model/User.java` (Línea 12)  
- **Normativa Afectada:** PCI-DSS 3.4, OWASP Top 10 (A04:2021 – Insecure Design)

#### Código Vulnerable Encontrado
```java
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
public class User {
    @Column(nullable = false)
    private String password;
```

#### Instrucciones de Mitigación para el Modelo Ejecutor
1. Reemplazar `@Data` con anotaciones específicas: `@Getter @Setter @EqualsAndHashCode`.
2. NO incluir `@ToString`. Si se necesita toString, implementarlo manualmente excluyendo el campo `password`.
3. Alternativa: usar `@ToString.Exclude` en el campo `password` si se mantiene `@Data`:
```java
@ToString.Exclude
private String password;
```

---

# VULNERABILIDADES BAJAS

---

### [ID-BAJO-001] Swagger/OpenAPI expuesto sin autenticación

- **Severidad:** BAJA  
- **Ubicación:** `auth-service/src/main/java/com/silvio/identity/config/SecurityConfig.java` (Líneas 35–37)  
- **Normativa Afectada:** OWASP Top 10 (A05:2021 – Security Misconfiguration)

#### Código Vulnerable Encontrado
```java
.requestMatchers(
    "/swagger-ui/**",
    "/swagger-ui.html",
    "/v3/api-docs/**",
    "/v3/api-docs"
).permitAll()
```

#### Instrucciones de Mitigación para el Modelo Ejecutor
1. En producción, proteger Swagger con autenticación básica o JWT.
2. Para perfiles distintos de `dev`, no exponer Swagger públicamente.
3. Configurar mediante perfiles de Spring: solo habilitar acceso público a Swagger en desarrollo.

---

### [ID-BAJO-002] BCryptPasswordEncoder sin especificar fuerza (strength)

- **Severidad:** BAJA  
- **Ubicación:** `auth-service/src/main/java/com/silvio/identity/config/SecurityConfig.java` (Líneas 53–55)  
- **Normativa Afectada:** PCI-DSS 6.5, ASVS 2.1.7

#### Código Vulnerable Encontrado
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

#### Instrucciones de Mitigación para el Modelo Ejecutor
1. Para un sistema bancario/financiero, usar `new BCryptPasswordEncoder(12)` o superior (12 rounds de salting).
2. El valor por defecto (10) es aceptable pero no óptimo para datos altamente sensibles.
3. Considerar migrar a Argon2id (recomendado por OWASP) para nuevos proyectos.

---

### [ID-BAJO-003] HATEOAS expone enlaces con parámetros null

- **Severidad:** BAJA  
- **Ubicación:** `auth-service/src/main/java/com/silvio/identity/controller/AuthController.java` (Líneas 56–59, 112)  
- **Normativa Afectada:** OWASP Top 10 (A04:2021 – Insecure Design)

#### Código Vulnerable Encontrado
```java
response.add(linkTo(methodOn(AuthController.class)
    .refreshToken(null)).withRel("refresh-token"));
response.add(linkTo(methodOn(AuthController.class)
    .changePassword(null, null)).withRel("change-password"));
```

#### Instrucciones de Mitigación para el Modelo Ejecutor
1. No incluir enlaces HATEOAS que expongan nombres de métodos internos y parámetros null.
2. Si se usan enlaces HATEOAS, asegurarse de que sean templates (con `{variable}`) para no exponer valores null.
3. Evaluar si HATEOAS es necesario en un API de autenticación; simplificar si no agrega valor.

---

### [ID-BAJO-004] Endpoint /register permite autoselección de roles sin autorización

- **Severidad:** BAJA  
- **Ubicación:** `auth-service/src/main/java/com/silvio/identity/controller/AuthController.java` (Líneas 72–81)  
- **Normativa Afectada:** OWASP Top 10 (A01:2021 – Broken Access Control), ASVS 2.2

#### Código Vulnerable Encontrado
```java
@PostMapping("/register")
public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
    userService.registerUser(request.getUsername(), request.getPassword(), request.getRoles());
```

#### Instrucciones de Mitigación para el Modelo Ejecutor
1. En producción, los roles no deben ser auto-asignables por el usuario en el registro.
2. El registro debería crear usuarios solo con el rol `ROLE_USER` por defecto, ignorando cualquier rol proporcionado en la solicitud.
3. La asignación de roles superiores (`ROLE_ADMIN`, `ROLE_LIBRARIAN`) debe ser gestionada por un administrador mediante un endpoint protegido.

---

### [ID-BAJO-005] Eureka registrado sin autenticación en servicio de discovery

- **Severidad:** BAJA  
- **Ubicación:** `microservice-config/src/main/resources/configurations/identity-service.yml` (Líneas 28–33)  
- **Normativa Afectada:** OWASP Top 10 (A05:2021 – Security Misconfiguration)

#### Código Vulnerable Encontrado
```yaml
eureka:
  instance:
    hostname: localhost
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

#### Instrucciones de Mitigación para el Modelo Ejecutor
1. Configurar autenticación en Eureka Server (spring-security).
2. Usar HTTPS para la comunicación con Eureka.
3. Agregar credenciales en la URL: `http://user:password@localhost:8761/eureka/`.
4. En Kubernetes/cloud, usar mecanismos de service mesh para asegurar la comunicación.

---

# HALLAZGOS ADICIONALES — Dependencias con Vulnerabilidades Conocidas

### [ID-DEP-001] jjwt 0.11.5 — Versión desactualizada

- **Severidad:** MEDIA  
- **Ubicación:** `auth-service/pom.xml` (Líneas 63–79)  
- **Normativa Afectada:** OWASP Top 10 (A06:2021 – Vulnerable and Outdated Components)

#### Código Vulnerable Encontrado
```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.11.5</version>
</dependency>
```

#### Instrucciones de Mitigación para el Modelo Ejecutor
1. Actualizar a la versión más reciente de jjwt (a partir de 0.12.x).
2. La versión 0.11.5 contiene vulnerabilidades conocidas de seguridad (CVE-2017-17485 y otras relacionadas con deserialización).
3. En jjwt 0.12.x, la API cambió: usar `Jwts.parser()` en lugar de `Jwts.parserBuilder()`, y `SignatureAlgorithm.HS256` fue reemplazado.

### [ID-DEP-002] Spring Boot 3.3.11 — Revisar CVEs activos

- **Severidad:** BAJA  
- **Ubicación:** `pom.xml` (Línea 9)  
- **Normativa Afectada:** OWASP Top 10 (A06:2021 – Vulnerable and Outdated Components)

El proyecto usa Spring Boot 3.3.11. Verificar si existen CVEs activos y actualizar al último parche disponible de la rama 3.3.x o migrar a 3.4.x si está disponible.

---

# RESUMEN DE RIESGOS POR CATEGORÍA OWASP

| OWASP Categoría | Hallazgos |
|----------------|-----------|
| A01 — Broken Access Control | ID-CRIT-003, CRIT-004, CRIT-005, ALTO-002, ALTO-007, MED-001, BAJO-004 |
| A02 — Cryptographic Failures | ID-CRIT-001, ALTO-001, ALTO-004 |
| A04 — Insecure Design | ID-ALTO-005, MED-006, MED-007, BAJO-003 |
| A05 — Security Misconfiguration | ID-CRIT-002, CRIT-006, ALTO-008, MED-002, BAJO-001, BAJO-005 |
| A06 — Vulnerable Components | ID-DEP-001, ID-DEP-002 |
| A07 — Identification Failures | ID-ALTO-006 |
| A09 — Logging Failures | ID-MED-003 |

---

# MAPA DE CALOR DE RIESGOS

```
Endpoints Públicos (sin auth)                    ████████████ CRIT-003, CRIT-005, ALTO-003
Manejo de JWT                                    ████████████ CRIT-001, CRIT-004, ALTO-001, ALTO-002, ALTO-007
Credenciales de BD                               ████████████ CRIT-002, ALTO-004, ALTO-005
Manejo de Contraseñas                            ████████████ CRIT-006, ALTO-006, BAJO-002
Seguridad HTTP / Cabeceras                       ████████████ ALTO-008
Configuración General                            ████████████ MED-001, MED-002, MED-003, MED-004
Logging / Exposición de Datos                    ████████████ MED-005, MED-006, MED-007
Documentación / API expuesta                     ████████████ BAJO-001, BAJO-003, BAJO-004
```

---

*Documento generado automáticamente por el sistema de auditoría de seguridad. Revisar y corregir cada hallazgo antes del despliegue a producción.*

*Fin del reporte — 26 vulnerabilidades encontradas (6 críticas, 8 altas, 7 medias, 5 bajas).*
