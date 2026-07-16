## Última Actualización
- Fecha: 2026-07-15 21:30
- Pipeline: Corrección de dos issues de rendimiento JPA (bulk UPDATE + @EntityGraph)

## Estado Actual del Servicio
- Clases principales:
  - `User` (entidad JPA) — modelo con username, password (BCrypt hash), roles (Set<String> como @ElementCollection), resetToken/resetTokenExpiry (recuperación de contraseña), refreshTokenHash (rotación de tokens).
  - `UserRepository` — repositorio JPA con findByUsername (lazy), findByUsernameWithRoles (eager vía @EntityGraph), findByResetToken, findByRefreshTokenHash.
  - `UserService` — lógica de negocio: registro, login, refresh token, cambio de contraseña, recuperación con token. Implementa UserDetailsService de Spring Security.
  - `AuthController` y `UserController` — controllers que delegan en UserService (patrón CSR).
  - `JwtUtil` — utilidad para generación/validación de JWT.
- Endpoints expuestos:
  - `POST /api/auth/register` — registro de usuario
  - `POST /api/auth/login` — autenticación, devuelve access + refresh tokens
  - `POST /api/auth/refresh` — rotación de refresh token
  - `POST /api/auth/forgot-password` — genera token de recuperación
  - `POST /api/auth/reset-password` — cambia contraseña con token
  - `PUT /api/auth/change-password` — cambia contraseña (autenticado)
  - `GET /api/users/{username}` — obtener datos del usuario
- Dependencias externas: PostgreSQL (BD), JWT (auth), BCrypt (password encoding)
- Cobertura de tests: ~88% (167 tests, 0 failures)

## Decisiones Técnicas
- **@ElementCollection LAZY + @EntityGraph** — Se cambió `FetchType.EAGER` a `FetchType.LAZY` en `roles` para evitar la carga innecesaria de roles en consultas que no los necesitan (ej. `findByResetToken`, `findByRefreshTokenHash`). Se agregó `findByUsernameWithRoles()` con `@EntityGraph(attributePaths = "roles")` para cargarlos eager solo cuando se requiere (autenticación, consulta de usuario). Alternativa descartada: mantener EAGER — forzaba JOIN sin necesidad en toda consulta a User.
- **@Query explícita + @EntityGraph** — La combinación `@EntityGraph` + `@Query` explícita evita que Spring Data JPA intente derivar la consulta del nombre del método `findByUsernameWithRoles`. Sin `@Query`, el framework busca una propiedad `usernameWithRoles` inexistente en la entidad.
- **Patrón CSR (Controller-Service-Repository)** — Toda la lógica de negocio reside en UserService, no en los controllers. Los controllers solo reciben requests, validan y delegan.
- **Refresh Token Rotation** — Se almacena el hash SHA-256 del refresh token. Al rotar, se invalida el anterior para prevenir ataques de robo de token.
- **Idempotencia en registro** — Se verifica existencia de username antes de crear, con manejo de excepción específica.

## Criterios de Aceptación Cumplidos
- 2) `User.java` debe cambiar `@ElementCollection(fetch = FetchType.EAGER)` a `FetchType.LAZY` en roles → Implementado. Se agregó `findByUsernameWithRoles()` en `UserRepository` con `@EntityGraph(attributePaths = "roles")`. `UserService.loadUserByUsername()` y `obtenerUsuarioPorUsername()` ahora usan `findByUsernameWithRoles()`.

## Historial de Cambios
- 2026-07-15 — Roles cambiado de EAGER a LAZY. Agregado findByUsernameWithRoles() con @EntityGraph. Actualizados loadUserByUsername() y obtenerUsuarioPorUsername(). Tests actualizados.
