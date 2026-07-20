## Última Actualización
- Fecha: 2026-07-20
- Pipeline: M-10 — NoSuchElementException → 404 NOT_FOUND en GlobalExceptionHandler

## Estado Actual del Servicio
- Clases principales:
  - `User` (entidad JPA) — modelo con username, password (BCrypt hash), roles (Set<String> como @ElementCollection LAZY), `resetTokenHash` (hash SHA-256 del token de recuperación, nunca texto plano), resetTokenExpiry, refreshTokenHash (hash SHA-256 del refresh token vigente).
  - `UserRepository` — repositorio JPA con findByUsername (lazy), findByUsernameWithRoles (eager vía @EntityGraph), `findByResetTokenHash`, findByRefreshTokenHash.
  - `UserService` — lógica de negocio: registro (roles siempre ROLE_USER), login, refresh token rotation, cambio de contraseña, recuperación con token (hash SHA-256 antes de almacenar/comparar). Implementa UserDetailsService de Spring Security. Método `hashToken()` privado reutilizado para refresh y reset tokens. `createPasswordResetToken()` retorna null si usuario no existe (sin excepción).
  - `RegisterRequest` (DTO) — solo contiene username + password. No acepta roles del cliente.
  - `AuthController` y `UserController` — controllers que delegan en UserService (patrón CSR). `register()` solo pasa username y password. `forgotPassword()` retorna siempre 200 OK con mensaje genérico.
  - `JwtUtil` — utilidad para generación/validación de JWT. Usa API jjwt 0.12.x: `parseSignedClaims().getPayload()`. Deriva la clave HMAC con `secret.getBytes(StandardCharsets.UTF_8)` para consistencia cross-plataforma.
  - `JwtAuthenticationFilter` — filtro que extrae roles del JWT y configura SecurityContext. Maneja roles null/blank con `Collections.emptyList()`.
- Endpoints expuestos:
  - `POST /api/auth/register` — registro de usuario (roles siempre ROLE_USER, ignora cualquier campo roles del JSON)
  - `POST /api/auth/login` — autenticación, devuelve access + refresh tokens
  - `POST /api/auth/refresh` — rotación de refresh token
  - `POST /api/auth/forgot-password` — genera token de recuperación (retorna siempre 200 OK con mensaje genérico, no revela si usuario existe)
  - `POST /api/auth/reset-password` — cambia contraseña con token (hashea el token recibido antes de comparar con hash almacenado)
  - `PUT /api/auth/change-password` — cambia contraseña (autenticado)
  - `GET /api/users/{username}` — obtener datos del usuario
- Dependencias externas: PostgreSQL (BD), JWT/jjwt 0.12.x (auth), BCrypt (password encoding)
- Cobertura de tests: 184 tests, 0 failures, 0 errors. Cubierta la derivación de clave UTF-8 en `JwtUtilTest`.

## Decisiones Técnicas
- **M-07 Hotfix: Revertir @SecurityScheme a configuración programática** — Se eliminó `@SecurityScheme` annotation class-level (causaba `ArrayIndexOutOfBoundsException` en ASM scanner de Spring Boot 3.3.11). Reemplazado por configuración programática en `customOpenAPI()` usando modelos OpenAPI. SwaggerConfigTest migrado de static source scan a `@SpringBootTest(classes = SwaggerConfig.class, webEnvironment = NONE)`. Alternativa descartada: mantener anotación — incompatible con ASM 9.x de Spring Boot 3.3.11.
- **M-05: Endpoints públicos excluidos** — AuthController: /auth/login, /auth/register, /auth/refresh, /auth/forgot-password, /auth/reset-password no tienen `@SecurityRequirement`. Solo /auth/change-password tiene security.
- **@ElementCollection LAZY + @EntityGraph** — Se cambió `FetchType.EAGER` a `FetchType.LAZY` en `roles` para evitar la carga innecesaria de roles en consultas que no los necesitan (ej. `findByResetTokenHash`, `findByRefreshTokenHash`). Se agregó `findByUsernameWithRoles()` con `@EntityGraph(attributePaths = "roles")` para cargarlos eager solo cuando se requiere (autenticación, consulta de usuario). Alternativa descartada: mantener EAGER — forzaba JOIN sin necesidad en toda consulta a User.
- **@Query explícita + @EntityGraph** — La combinación `@EntityGraph` + `@Query` explícita evita que Spring Data JPA intente derivar la consulta del nombre del método `findByUsernameWithRoles`. Sin `@Query`, el framework buscaría una propiedad `usernameWithRoles` inexistente en la entidad.
- **Patrón CSR (Controller-Service-Repository)** — Toda la lógica de negocio reside en UserService, no en los controllers. Los controllers solo reciben requests, validan y delegan.
- **Refresh Token Rotation** — Se almacena el hash SHA-256 del refresh token. Al rotar, se invalida el anterior para prevenir ataques de robo de token.
- **Idempotencia en registro** — Se verifica existencia de username antes de crear, con manejo de excepción específica.
- **C-1: Hash SHA-256 para reset tokens** — El campo `resetToken` fue renombrado a `resetTokenHash` en `User.java`. `createPasswordResetToken()` genera UUID, lo hashea con SHA-256 vía `hashToken()` y almacena solo el hash. El UUID plano se devuelve al cliente. `resetPassword()` hashea el token recibido antes de comparar con `findByResetTokenHash()`. El método `hashRefreshToken()` fue renombrado a `hashToken()` para reflejar su uso dual (refresh + reset). Alternativa descartada: almacenar token plano — vulnerable si la BD es comprometida.
- **C-2 (registro): Roles hardcodeados en registro** — El campo `roles` fue eliminado de `RegisterRequest.java`. `UserService.registerUser()` solo acepta username y password, siempre asigna `Set.of("ROLE_USER")`. El controller no pasa roles. Esto previene escalación de privilegios donde un cliente podría enviarse `ROLE_ADMIN` en el JSON de registro. Alternativa descartada: aceptar roles con validación — innecesariamente complejo para el caso de uso actual.
- **C-3: Migración API jjwt 0.12.x** — `parseClaimsJws()` reemplazado por `parseSignedClaims()`, `.getBody()` reemplazado por `.getPayload()` en JwtUtil, JwtUtilTest. Comentarios en español actualizados consistentemente. Tests de regresión estática (JwtApiMigrationTest) previenen reintroducción de API deprecada.
- **C-02 (key derivation): UTF-8 en derivación de clave HMAC** — `JwtUtil.getSigningKey()` usa `jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8)` en lugar de `getBytes()` con charset por defecto. Esto garantiza que la misma cadena de secreto produzca la misma clave criptográfica en Windows (Cp1252 por defecto) y Linux (UTF-8). Alternativa descartada: mantener charset por defecto — rompe la validación cross-plataforma del JWT.
- **H-02: Optional en createPasswordResetToken** — Se cambió `findByUsername().orElseThrow()` a uso directo de Optional con `isEmpty()` check. Si usuario no existe, retorna null sin excepción. AuthController retorna siempre 200 OK con mensaje `"Si el usuario existe, recibirá instrucciones de recuperación."` sin revelar existencia del usuario. Alternativa descartada: lanzar excepción y capturar en controller — revelaría información al cliente.
- **Z-01: UsuarioNotFoundException reemplazado por java.util.NoSuchElementException** — Se eliminó la clase zombie `UsuarioNotFoundException` (sin throw new en producción). Los throws en `UserService` se reemplazaron por `NoSuchElementException` con mensaje descriptivo. Alternativa descartada: crear nueva excepción personalizada — usar stdlib reduce código muerto futuro.
- **M-10: @ExceptionHandler(NoSuchElementException.class) → 404 NOT_FOUND** — Se agregó handler en GlobalExceptionHandler para capturar `NoSuchElementException` (lanzado por `Optional.orElseThrow()`) y retornar 404 en lugar de 500. Sigue exactamente el patrón de subscription-service. Test unitario en GlobalExceptionHandlerTest verifica NoSuchElementException → 404. Alternativa descartada: usar ResponseEntityExceptionHandler — más complejo sin beneficio para un caso simple.
- **H-01: Null-check/blank-check en JwtAuthenticationFilter** — Antes de `rolesStr.split(",")`, se valida null y blank. Si null/blank → `Collections.emptyList()`. Si tiene contenido → split con `filter(!blank)` y map a `SimpleGrantedAuthority`. Import `java.util.Collections` agregado. Alternativa descartada: usar Optional.ofNullable — más verboso sin beneficio real.

## Criterios de Aceptación Cumplidos
- 2) `User.java` debe cambiar `@ElementCollection(fetch = FetchType.EAGER)` a `FetchType.LAZY` en roles → Implementado. Se agregó `findByUsernameWithRoles()` en `UserRepository` con `@EntityGraph(attributePaths = "roles")`. `UserService.loadUserByUsername()` y `obtenerUsuarioPorUsername()` ahora usan `findByUsernameWithRoles()`.
- C-1) `resetToken` almacenado como hash SHA-256 → Campo renombrado a `resetTokenHash` en User.java. `UserRepository.findByResetTokenHash()` reemplaza `findByResetToken()`. `createPasswordResetToken()` hashea antes de guardar. `resetPassword()` hashea antes de comparar. Método `hashToken()` reutilizado.
- C-2) Registro no acepta roles del cliente → `RegisterRequest` sin campo `roles`. `registerUser(String username, rawPassword)` siempre asigna `ROLE_USER`. `AuthController.register()` no pasa roles.
- C-3) API jjwt deprecada reemplazada → `parseSignedClaims().getPayload()` en JwtUtil.java, JwtUtilTest.java. Tests de regresión estática en JwtApiMigrationTest.java.
- **C-02) Derivar clave HMAC con `StandardCharsets.UTF_8`** → `JwtUtil.getSigningKey()` usa `secret.getBytes(StandardCharsets.UTF_8)`. `JwtUtilTest` actualizado para usar UTF-8 en la clave de prueba y verificar consistencia cross-plataforma.
- **H-02)** `createPasswordResetToken()` retorna null si usuario no existe → Implementado con Optional. `forgotPassword()` retorna siempre 200 OK con mensaje genérico idéntico. Tests actualizados y nuevos agregados.
- **H-01)** `JwtAuthenticationFilter` maneja roles null/blank sin NPE → Implementado con null-check + blank-check antes del split. `Collections.emptyList()` para casos vacíos. 3 tests unitarios creados en `JwtAuthenticationFilterTest`.
- **Z-01) Reemplazar UsuarioNotFoundException por NoSuchElementException** → `UserService` ya no lanza `UsuarioNotFoundException`. Usa `NoSuchElementException` con mensaje descriptivo. Clase zombie eliminada del filesystem. Tests actualizados. Compilación verificada.
- **M-10) NoSuchElementException → 404 NOT_FOUND en GlobalExceptionHandler** → Handler `@ExceptionHandler(NoSuchElementException.class)` retorna 404 con mensaje descriptivo. Test `noSuchElement_debeRetornar404()` en GlobalExceptionHandlerTest verifica 404. Build: 183 tests PASS.

## Historial de Cambios
- 2026-07-20 — M-10: @ExceptionHandler(NoSuchElementException.class) → 404 NOT_FOUND verificado. Handler y test ya implementados. 183 tests PASS.
- 2026-07-20 — Z-01: UsuarioNotFoundException eliminada, reemplazada por NoSuchElementException en UserService. Tests actualizados. Compilación verificada.
- 2026-07-18 — M-07 Hotfix: ASM bug fix. @SecurityScheme eliminado de SwaggerConfig, reemplazado por configuración programática. SwaggerConfigTest migrado a @SpringBootTest. Verificado: 186 tests PASS, JaCoCo OK.
- 2026-07-17 — M-05: @SecurityScheme en SwaggerConfig. Import SecuritySchemeType corregido. SwaggerConfigTest static scan. Endpoints públicos excluidos de @SecurityRequirement.
- 2026-07-16 — C-02: `JwtUtil.getSigningKey()` usa `StandardCharsets.UTF_8`. `JwtUtilTest` actualizado con tests de consistencia cross-plataforma.
- 2026-07-15 — Roles cambiado de EAGER a LAZY. Agregado findByUsernameWithRoles() con @EntityGraph. Actualizados loadUserByUsername() y obtenerUsuarioPorUsername(). Tests actualizados.
- 2026-07-16 — C-1: resetToken renombrado a resetTokenHash, hash SHA-256 aplicado en createPasswordResetToken() y resetPassword(). hashRefreshToken() renombrado a hashToken(). UserRepository actualizado (findByResetTokenHash).
- 2026-07-16 — C-2 (registro): Campo roles eliminado de RegisterRequest. registerUser() siempre asigna ROLE_USER. AuthController actualizado. Docstring Swagger corregido.
- 2026-07-16 — C-3: parseClaimsJws() → parseSignedClaims(), getBody() → getPayload() en JwtUtil y tests. JwtApiMigrationTest creado como test de regresión estática.
- 2026-07-16 — Tests agregados: resetPassword_tokenYaUtilizadoAnteriormente_debeFallar (UserServiceTest), register_conRolesEnJson_ignoradosSinEfecto (AuthControllerTest). Total: 169 tests identity-services.
- 2026-07-17 — H-02: createPasswordResetToken() usa Optional, retorna null si usuario no existe. forgotPassword() retorna siempre 200 OK con mensaje genérico. Tests actualizados y nuevos.
- 2026-07-17 — H-01: JwtAuthenticationFilter maneja roles null/blank con Collections.emptyList(). Import Collections agregado. JwtAuthenticationFilterTest creado con 3 tests. Total: 184 tests, 0 fallos.
