## Última Actualización
- Fecha: 2026-07-16 09:45
- Pipeline: Corrección de tres issues críticos de seguridad (C-1: hash resetToken, C-2: roles hardcodeados en registro, C-3: migración API jjwt deprecada)

## Estado Actual del Servicio
- Clases principales:
  - `User` (entidad JPA) — modelo con username, password (BCrypt hash), roles (Set<String> como @ElementCollection LAZY), `resetTokenHash` (hash SHA-256 del token de recuperación, nunca texto plano), resetTokenExpiry, refreshTokenHash (hash SHA-256 del refresh token vigente).
  - `UserRepository` — repositorio JPA con findByUsername (lazy), findByUsernameWithRoles (eager vía @EntityGraph), `findByResetTokenHash`, findByRefreshTokenHash.
  - `UserService` — lógica de negocio: registro (roles siempre ROLE_USER), login, refresh token rotation, cambio de contraseña, recuperación con token (hash SHA-256 antes de almacenar/comparar). Implementa UserDetailsService de Spring Security. Método `hashToken()` privado reutilizado para refresh y reset tokens.
  - `RegisterRequest` (DTO) — solo contiene username + password. No acepta roles del cliente.
  - `AuthController` y `UserController` — controllers que delegan en UserService (patrón CSR). `register()` solo pasa username y password.
  - `JwtUtil` — utilidad para generación/validación de JWT. Usa API jjwt 0.12.x: `parseSignedClaims().getPayload()`.
- Endpoints expuestos:
  - `POST /api/auth/register` — registro de usuario (roles siempre ROLE_USER, ignora cualquier campo roles del JSON)
  - `POST /api/auth/login` — autenticación, devuelve access + refresh tokens
  - `POST /api/auth/refresh` — rotación de refresh token
  - `POST /api/auth/forgot-password` — genera token de recuperación (devuelve UUID plano al cliente, almacena SHA-256 hash en BD)
  - `POST /api/auth/reset-password` — cambia contraseña con token (hashea el token recibido antes de comparar con hash almacenado)
  - `PUT /api/auth/change-password` — cambia contraseña (autenticado)
  - `GET /api/users/{username}` — obtener datos del usuario
- Dependencias externas: PostgreSQL (BD), JWT/jjwt 0.12.x (auth), BCrypt (password encoding)
- Cobertura de tests: ~89% (169 tests, 0 failures, 0 errors)

## Decisiones Técnicas
- **@ElementCollection LAZY + @EntityGraph** — Se cambió `FetchType.EAGER` a `FetchType.LAZY` en `roles` para evitar la carga innecesaria de roles en consultas que no los necesitan (ej. `findByResetTokenHash`, `findByRefreshTokenHash`). Se agregó `findByUsernameWithRoles()` con `@EntityGraph(attributePaths = "roles")` para cargarlos eager solo cuando se requiere (autenticación, consulta de usuario). Alternativa descartada: mantener EAGER — forzaba JOIN sin necesidad en toda consulta a User.
- **@Query explícita + @EntityGraph** — La combinación `@EntityGraph` + `@Query` explícita evita que Spring Data JPA intente derivar la consulta del nombre del método `findByUsernameWithRoles`. Sin `@Query`, el framework busca una propiedad `usernameWithRoles` inexistente en la entidad.
- **Patrón CSR (Controller-Service-Repository)** — Toda la lógica de negocio reside en UserService, no en los controllers. Los controllers solo reciben requests, validan y delegan.
- **Refresh Token Rotation** — Se almacena el hash SHA-256 del refresh token. Al rotar, se invalida el anterior para prevenir ataques de robo de token.
- **Idempotencia en registro** — Se verifica existencia de username antes de crear, con manejo de excepción específica.
- **C-1: Hash SHA-256 para reset tokens** — El campo `resetToken` fue renombrado a `resetTokenHash` en `User.java`. `createPasswordResetToken()` genera UUID, lo hashea con SHA-256 vía `hashToken()` y almacena solo el hash. El UUID plano se devuelve al cliente. `resetPassword()` hashea el token recibido antes de comparar con `findByResetTokenHash()`. El método `hashRefreshToken()` fue renombrado a `hashToken()` para reflejar su uso dual (refresh + reset). Alternativa descartada: almacenar token plano — vulnerable si la BD es comprometida.
- **C-2: Roles hardcodeados en registro** — El campo `roles` fue eliminado de `RegisterRequest.java`. `UserService.registerUser()` solo acepta username y password, siempre asigna `Set.of("ROLE_USER")`. El controller no pasa roles. Esto previene escalación de privilegios donde un cliente podría enviarse `ROLE_ADMIN` en el JSON de registro. Alternativa descartada: aceptar roles con validación — innecesariamente complejo para el caso de uso actual.
- **C-3: Migración API jjwt 0.12.x** — `parseClaimsJws()` reemplazado por `parseSignedClaims()`, `.getBody()` reemplazado por `.getPayload()` en JwtUtil, JwtUtilTest. Comentarios en español actualizados consistentemente. Tests de regresión estática (JwtApiMigrationTest) previenen reintroducción de API deprecada.

## Criterios de Aceptación Cumplidos
- 2) `User.java` debe cambiar `@ElementCollection(fetch = FetchType.EAGER)` a `FetchType.LAZY` en roles → Implementado. Se agregó `findByUsernameWithRoles()` en `UserRepository` con `@EntityGraph(attributePaths = "roles")`. `UserService.loadUserByUsername()` y `obtenerUsuarioPorUsername()` ahora usan `findByUsernameWithRoles()`.
- C-1) `resetToken` almacenado como hash SHA-256 → Campo renombrado a `resetTokenHash` en User.java. `UserRepository.findByResetTokenHash()` reemplaza `findByResetToken()`. `createPasswordResetToken()` hashea antes de guardar. `resetPassword()` hashea antes de comparar. Método `hashToken()` reutilizado.
- C-2) Registro no acepta roles del cliente → `RegisterRequest` sin campo `roles`. `registerUser(String username, String rawPassword)` siempre asigna `ROLE_USER`. `AuthController.register()` no pasa roles.
- C-3) API jjwt deprecada reemplazada → `parseSignedClaims().getPayload()` en JwtUtil.java, JwtUtilTest.java. Tests de regresión estática en JwtApiMigrationTest.java.

## Historial de Cambios
- 2026-07-15 — Roles cambiado de EAGER a LAZY. Agregado findByUsernameWithRoles() con @EntityGraph. Actualizados loadUserByUsername() y obtenerUsuarioPorUsername(). Tests actualizados.
- 2026-07-16 — C-1: resetToken renombrado a resetTokenHash, hash SHA-256 aplicado en createPasswordResetToken() y resetPassword(). hashRefreshToken() renombrado a hashToken(). UserRepository actualizado (findByResetTokenHash).
- 2026-07-16 — C-2: Campo roles eliminado de RegisterRequest. registerUser() siempre asigna ROLE_USER. AuthController actualizado. Docstring Swagger corregido.
- 2026-07-16 — C-3: parseClaimsJws() → parseSignedClaims(), getBody() → getPayload() en JwtUtil y tests. JwtApiMigrationTest creado como test de regresión estática.
- 2026-07-16 — Tests agregados: resetPassword_tokenYaUtilizadoAnteriormente_debeFallar (UserServiceTest), register_conRolesEnJson_ignoradosSinEfecto (AuthControllerTest). Total: 169 tests identity-services.
