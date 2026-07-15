# Auditoría de Rendimiento — Spring Data JPA

**Proyecto:** SmallBooksRepository  
**Fecha:** 2026-07-15  
**Auditor:** Java Architecture Auditor  
**Alcance:** 7 entidades JPA, 7 repositorios, servicios relacionados  

---

## Resumen Ejecutivo

| Dominio | Hallazgos Críticos | Hallazgos Altos | Hallazgos Medios | Hallazgos Bajos |
|---------|-------------------|-----------------|------------------|-----------------|
| N+1 Queries | 0 | 0 | 1 | 0 |
| @Transactional(readOnly) | 0 | 0 | 9 | 2 |
| Índices faltantes | 0 | 1 | 2 | 0 |
| findAll() / Full Scans | 0 | 1 | 2 | 0 |
| @Lob Eager Loading | 1 | 0 | 0 | 0 |

**Total:** 1 crítico, 2 altos, 14 medios, 2 bajos

---

## [JPA-PERF-001] Carga Eager de LONGBLOB en `ArchivoLibro.datos`

- **Severidad:** Crítica
- **Ubicación:** `ingestion-service/src/main/java/com/silvio/ingestion/model/ArchivoLibro.java` (Líneas 41-43)
- **Tipo de Incumplimiento:** Rendimiento / Calidad

### Código Identificado

```java
@Lob
@Column(columnDefinition = "LONGBLOB")
private byte[] datos;
```

### Justificación del Incumplimiento

La columna `datos` está definida como `LONGBLOB` (hasta 4 GB) y no tiene `@Basic(fetch = FetchType.LAZY)`. JPA por defecto carga **EAGER** los campos `@Lob`. Esto significa que:

1. **`obtenerInfo(Long libroId)`** (IngestionService, línea 78-84) carga todo el blob en memoria solo para devolver metadatos (nombre, formato, tamaño, fecha). El blob `datos` se transfiere desde la BD a la JVM innecesariamente.
2. **`obtenerBytes(Long libroId)`** (IngestionService, línea 88-92) también carga el blob + metadatos, aunque solo necesita `rutaOClave`.
3. **`eliminar(Long libroId)`** (IngestionService, línea 96-104) carga el blob completo antes de eliminar el registro.

Con miles de archivos o archivos grandes (ej. PDFs de 50 MB+), esto causa:
- Uso masivo de memoria Heap en cada request
- Latencia alta en consultas de solo metadata
- Posibles `OutOfMemoryError` en escenarios de concurrencia

### Instrucciones de Rectificación

1. Agregar `@Basic(fetch = FetchType.LAZY)` sobre el campo `datos`:

```java
@Lob
@Basic(fetch = FetchType.LAZY)
@Column(columnDefinition = "LONGBLOB")
private byte[] datos;
```

2. Crear una proyección (interface o DTO) para consultas de solo metadata, por ejemplo:

```java
public interface ArchivoLibroInfo {
    Long getId();
    Long getLibroId();
    String getNombreArchivo();
    String getFormato();
    Long getTamanio();
    String getRutaOClave();
    LocalDateTime getFechaSubida();
}
```

3. Agregar un método en `ArchivoLibroRepository` que use la proyección:

```java
Optional<ArchivoLibroInfo> findByLibroId(Long libroId);
```

> **Nota:** Si se usa `@Basic(fetch = FetchType.LAZY)` sin `bytecode instrumentation` de Hibernate, igual se cargará el blob si se accede a cualquier propiedad del entity dentro de la transacción activa por el proxy de inicialización. La solución definitiva es usar proyecciones o DTOs con JPQL.

---

## [JPA-PERF-002] `findAll()` en CatalogService — Riesgo de Full Table Scan

- **Severidad:** Alta
- **Ubicación:** `catalog-service/src/main/java/com/silvio/catalog/service/CatalogService.java` (Líneas 29-35)
- **Tipo de Incumplimiento:** Rendimiento / Arquitectura

### Código Identificado

```java
@Observed(name = "catalog.obtenerTodos")
public List<LibroResponseDTO> obtenerTodos() {
    log.info("Consultando todos los libros del catálogo");
    return libroRepository.findAll()
            .stream()
            .map(this::mapearADto)
            .collect(Collectors.toList());
}
```

### Justificación del Incumplimiento

`findAll()` sin paginación ejecuta `SELECT * FROM libros`, cargando **todas las filas en memoria**. En un catálogo con crecimiento (cientos de miles de libros), esto provoca:

- Full table scan sin límite
- Consumo de Heap proporcional al tamaño de la tabla
- Bloqueo de conexiones JDBC por lecturas largas
- Sin capacidad de escalar horizontalmente

### Instrucciones de Rectificación

Refactorizar para usar paginación con `Pageable`:

```java
public Page<LibroResponseDTO> obtenerTodos(Pageable pageable) {
    log.info("Consultando libros del catálogo — página: {}, tamaño: {}",
            pageable.getPageNumber(), pageable.getPageSize());
    return libroRepository.findAll(pageable)
            .map(this::mapearADto);
}
```

Actualizar el Controller para aceptar `Pageable` de Spring y exponer parámetros `?page=0&size=20&sort=titulo,asc`.

---

## [JPA-PERF-003] `findAll()` en LicenseService — Riesgo de Full Table Scan

- **Severidad:** Alta
- **Ubicación:** `license-service/src/main/java/com/silvio/license/service/LicenseService.java` (Líneas 33-38)
- **Tipo de Incumplimiento:** Rendimiento / Arquitectura

### Código Identificado

```java
@Observed(name = "license.obtenerTodas")
public List<LicenseResponseDTO> obtenerTodas() {
    log.info("Consultando todas las licencias");
    return licenseRepository.findAll()
            .stream()
            .map(this::mapearADto)
            .collect(Collectors.toList());
}
```

### Justificación del Incumplimiento

Ídem [JPA-PERF-002]. `findAll()` sin paginación sobre `licencias` escala con el catálogo de libros. Aunque `licencias` es 1:1 con libros, el crecimiento es ilimitado.

### Instrucciones de Rectificación

Ídem [JPA-PERF-002]: usar `findAll(Pageable)` con paginación.

---

## [JPA-PERF-004] `findAll()` en PrestamoService — Riesgo de Full Table Scan

- **Severidad:** Media
- **Ubicación:** `elending-service/src/main/java/com/silvio/elending/service/PrestamoService.java` (Líneas 233-239)
- **Tipo de Incumplimiento:** Rendimiento

### Código Identificado

```java
@Observed(name = "elending.obtenerTodos")
public List<PrestamoResponseDTO> obtenerTodos() {
    log.info("Consultando todos los préstamos para Analytics");
    return prestamoRepository.findAll()
            .stream()
            .map(this::mapearADto)
            .collect(Collectors.toList());
}
```

### Justificación del Incumplimiento

Este endpoint expone el historial completo de préstamos sin paginación. Proyectado a largo plazo, una base de usuarios activa generará millones de registros de préstamos, haciendo este endpoint progresivamente más lento hasta volverse impracticable.

### Instrucciones de Rectificación

1. Agregar paginación vía `Pageable`:
```java
public Page<PrestamoResponseDTO> obtenerTodos(Pageable pageable) {
    return prestamoRepository.findAll(pageable).map(this::mapearADto);
}
```

2. Si el único consumidor es Analytics, considerar un endpoint específico con filtros por fecha:
```java
Page<PrestamoResponseDTO> obtenerPorRangoFechas(
        LocalDateTime desde, LocalDateTime hasta, Pageable pageable);
```

---

## [JPA-PERF-005] Índices faltantes en tabla `libros`

- **Severidad:** Alta
- **Ubicación:** `catalog-service/src/main/resources/db/migration/V1__crear_tabla_libros.sql`
- **Tipo de Incumplimiento:** Rendimiento / Calidad

### Código Identificado

```sql
CREATE TABLE libros (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    titulo            VARCHAR(200)  NOT NULL,
    autor             VARCHAR(150)  NOT NULL,
    isbn              VARCHAR(20)   NOT NULL UNIQUE,
    editorial         VARCHAR(150),
    anio_publicacion  INT,
    idioma            VARCHAR(50),
    genero            VARCHAR(100),
    sinopsis          TEXT,
    portada_url       VARCHAR(500),
    disponible        TINYINT(1)    NOT NULL DEFAULT 1
);
```

### Justificación del Incumplimiento

La tabla `libros` carece de índices en columnas utilizadas en cláusulas WHERE de las siguientes consultas del repositorio:

| Método | Columna(s) de Filtro | ¿Tiene Índice? |
|--------|----------------------|----------------|
| `findByTituloContainingIgnoreCase` | `titulo` (LIKE %%) | ❌ |
| `findByAutorContainingIgnoreCase` | `autor` (LIKE %%) | ❌ |
| `findByGeneroIgnoreCase` | `genero` | ❌ |
| `findByDisponibleTrue` | `disponible` | ❌ |
| `buscarCombinado` | `titulo`, `autor`, `genero` | ❌ |

Sin índices, cada consulta ejecuta **Full Table Scan** sobre `libros`. Con `LIKE '%texto%'` los índices B-tree tradicionales no ayudan mucho (salvo índices FULLTEXT), pero para `genero` y `disponible` un índice estándar mejoraría drásticamente el rendimiento. Para `titulo` y `autor`, conviene añadir un índice FULLTEXT para búsquedas textuales eficientes.

### Instrucciones de Rectificación

Crear una migración Flyway `V3__agregar_indices_libros.sql`:

```sql
-- Índice para búsquedas por disponibilidad (usado por findByDisponibleTrue)
CREATE INDEX idx_libros_disponible ON libros(disponible);

-- Índice para búsquedas exactas/igualdad por género (usado por findByGeneroIgnoreCase y buscarCombinado)
CREATE INDEX idx_libros_genero ON libros(genero);

-- Índice FULLTEXT para búsqueda textual en título y autor
-- (usado por findByTituloContainingIgnoreCase, findByAutorContainingIgnoreCase y buscarCombinado)
CREATE FULLTEXT INDEX idx_libros_busqueda ON libros(titulo, autor);
```

> **Nota:** Si se migra a FULLTEXT, los métodos de repositorio deben reescribirse usando `@Query` con `MATCH ... AGAINST` en lugar de `LIKE %...%` para obtener verdadero rendimiento. Alternativamente, mantener LIKE y aceptar que los índices regulares solo aceleran búsquedas con prefijo (`titulo%`), no con comodín inicial (`%titulo%`).

---

## [JPA-PERF-006] Falta de `@Transactional(readOnly = true)` en métodos de solo lectura

- **Severidad:** Media
- **Ubicación:** Múltiples servicios (ver tabla abajo)
- **Tipo de Incumplimiento:** Rendimiento / Estándar de Codificación

### Justificación del Incumplimiento

Spring Data JPA deriva de `SimpleJpaRepository` que ya tiene `@Transactional(readOnly = true)` en métodos como `findById()`, `findAll()`, etc. Sin embargo, los métodos de servicio que **combinan varias operaciones de repositorio** no tienen `@Transactional`, lo que provoca:

1. Cada llamada a repositorio abre una transacción separada (overhead de conexión JDBC)
2. Sin `readOnly = true`, Hibernate no optimiza el flush (hace dirty checking innecesario)
3. Sin `readOnly = true`, no se habilita el hint de solo lectura en el driver JDBC (MySQL puede optimizar consultas)
4. Las colecciones lazy cargadas fuera de una transacción unificada pueden lanzar `LazyInitializationException`

### Métodos Afectados

| Servicio | Método | Línea |
|----------|--------|-------|
| `CatalogService` | `obtenerTodos()` | 29 |
| `CatalogService` | `obtenerDisponibles()` | 38 |
| `CatalogService` | `obtenerPorId(Long id)` | 47 |
| `CatalogService` | `buscar(...)` | 58 |
| `LicenseService` | `obtenerTodas()` | 33 |
| `LicenseService` | `obtenerPorLibroId(Long libroId)` | 42 |
| `PrestamoService` | `obtenerPrestamosActivos(...)` | 213 |
| `PrestamoService` | `obtenerHistorial(...)` | 223 |
| `PrestamoService` | `obtenerTodos()` | 233 |
| `SuscripcionService` | `obtenerPorUsuario(...)` | 31 |
| `NotificacionService` | `obtenerPorUsuario(...)` | 85 |
| `NotificacionService` | `obtenerNoLeidas(...)` | 95 |

### Instrucciones de Rectificación

Agregar `@Transactional(readOnly = true)` en cada método de solo lectura. Ejemplo:

```java
@Observed(name = "catalog.obtenerTodos")
@Transactional(readOnly = true)
public List<LibroResponseDTO> obtenerTodos() {
    // ...
}
```

Alternativamente, marcar toda la clase con `@Transactional(readOnly = true)` y sobrescribir con `@Transactional(readOnly = false)` solo en métodos de escritura.

---

## [JPA-PERF-007] `@ElementCollection(fetch = FetchType.EAGER)` en `User.roles`

- **Severidad:** Media
- **Ubicación:** `identity-services/src/main/java/com/silvio/identity/model/User.java` (Línea 28)
- **Tipo de Incumplimiento:** Rendimiento

### Código Identificado

```java
@ElementCollection(fetch = FetchType.EAGER)
@CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
@Column(name = "role")
private Set<String> roles;
```

### Justificación del Incumplimiento

`FetchType.EAGER` en `@ElementCollection` provoca que:

1. Cada vez que se carga un `User` (incluso solo para login, findByUsername, etc.), JPA ejecuta **una consulta adicional** a `user_roles` para traer los roles.
2. Métodos como `loadUserByUsername()` (UserService, línea 135) o `obtenerUsuarioPorUsername()` (línea 270) cargan los roles aunque solo necesiten username/password.
3. No hay manera de hacer "eager si quiero, lazy si no" — EAGER es obligatorio siempre.

El impacto es bajo ahora porque `user_roles` es una tabla pequeña, pero la consulta adicional es innecesaria para flujos como login, cambio de contraseña, o reseteo de token.

### Instrucciones de Rectificación

Cambiar a `FetchType.LAZY` y forzar la carga solo donde sea necesario:

```java
@ElementCollection(fetch = FetchType.LAZY)
@CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
@Column(name = "role")
private Set<String> roles;
```

Ajustar `loadUserByUsername()` en `UserService` para usar `@Transactional(readOnly = true)` con `@EntityGraph`:

```java
@Override
@Transactional(readOnly = true)
@Observed(name = "identity.loadUserByUsername")
public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    User user = userRepository.findByUsernameWithRoles(username)
            .orElseThrow(() -> new UsernameNotFoundException(...));
    // ... build UserDetails
}
```

Y en `UserRepository`:

```java
@EntityGraph(attributePaths = "roles")
Optional<User> findByUsernameWithRoles(String username);
```

---

## [JPA-PERF-008] `cerrarPrestamosVencidos()` — Consulta duplicada en scheduler

- **Severidad:** Media
- **Ubicación:** `elending-service/src/main/java/com/silvio/elending/service/PrestamoService.java` (Líneas 260 y 296)
- **Tipo de Incumplimiento:** Rendimiento

### Código Identificado

```java
// Primera consulta: préstamos vencidos
List<Prestamo> vencidos = prestamoRepository
        .findByEstadoAndFechaVencimientoBefore(EstadoPrestamo.ACTIVO, ahora);
// ... procesa vencidos ...

// Segunda consulta: préstamos próximos a vencer
List<Prestamo> proximosAVencer = prestamoRepository
        .findByEstadoAndFechaVencimientoBefore(EstadoPrestamo.ACTIVO, en2Dias)
        .stream()
        .filter(p -> p.getFechaVencimiento().isAfter(ahora))
        .collect(Collectors.toList());
```

### Justificación del Incumplimiento

El scheduler ejecuta dos consultas casi idénticas sobre `prestamos` en la misma ejecución:

1. `WHERE estado = 'ACTIVO' AND fecha_vencimiento < ahora` → préstamos vencidos
2. `WHERE estado = 'ACTIVO' AND fecha_vencimiento < ahora + 2dias` → próximos a vencer

La segunda consulta **incluye todos los resultados de la primera** más los próximos a vencer, y luego filtra en memoria con `.filter()`. Esto es ineficiente: se podrían obtener los próximos a vencer con una sola consulta adicional que excluya los ya vencidos.

### Instrucciones de Rectificación

Optimizar para una sola consulta adicional que solo obtenga los próximos a vencer (excluyendo vencidos):

```java
// Próximos a vencer (entre ahora y ahora+2días, excluye vencidos ya procesados)
List<Prestamo> proximosAVencer = prestamoRepository
        .findByEstadoAndFechaVencimientoBetween(
                EstadoPrestamo.ACTIVO, ahora, en2Dias);
```

Agregar método en `PrestamoRepository`:

```java
// Préstamos activos con vencimiento en un rango de fechas
List<Prestamo> findByEstadoAndFechaVencimientoBetween(
        EstadoPrestamo estado, LocalDateTime desde, LocalDateTime hasta);
```

---

## [JPA-PERF-009] Falta de `@Transactional(readOnly = true)` en `UserService.loadUserByUsername`

- **Severidad:** Baja
- **Ubicación:** `identity-services/src/main/java/com/silvio/identity/service/UserService.java` (Líneas 134-150)
- **Tipo de Incumplimiento:** Rendimiento

### Código Identificado

```java
@Override
@Observed(name = "identity.loadUserByUsername")
public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    log.info("Cargando usuario para autenticación: {}", username);
    User user = userRepository.findByUsername(username)
            .orElseThrow(() -> {
                log.warn("Usuario no encontrado: {}", username);
                return new UsernameNotFoundException(" Usuario no encontrado: " + username);
            });
    return org.springframework.security.core.userdetails.User.builder()
            .username(user.getUsername())
            .password(user.getPassword())
            .authorities(user.getRoles().stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList()))
            .build();
}
```

### Justificación del Incumplimiento

Este método es invocado por Spring Security en **cada autenticación** (login, cada request con JWT que requiera `UserDetailsService`). Al no tener `@Transactional(readOnly = true)`, cada autenticación abre una transacción de escritura innecesaria con dirty checking activo. Con `FetchType.EAGER` en roles, además se ejecutan queries separadas sin agrupar en una sola transacción.

### Instrucciones de Rectificación

Agregar `@Transactional(readOnly = true)`:

```java
@Override
@Transactional(readOnly = true)
@Observed(name = "identity.loadUserByUsername")
public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    // ...
}
```

---

## [JPA-PERF-010] `marcarTodasLeidas()` — Actualización ineficiente

- **Severidad:** Baja
- **Ubicación:** `notification-service/src/main/java/com/silvio/notification/service/NotificacionService.java` (Líneas 118-126)
- **Tipo de Incumplimiento:** Rendimiento

### Código Identificado

```java
public void marcarTodasLeidas(String usuarioId) {
    log.info("Marcando todas las notificaciones como leídas — usuario: {}", usuarioId);
    List<Notificacion> noLeidas = notificacionRepository
            .findByUsuarioIdAndLeidaFalse(usuarioId);
    noLeidas.forEach(n -> n.setLeida(true));
    notificacionRepository.saveAll(noLeidas);
    log.info("{} notificaciones marcadas como leídas para usuario: {}",
            noLeidas.size(), usuarioId);
}
```

### Justificación del Incumplimiento

Este método:
1. **Carga todas** las notificaciones no leídas en memoria (entidad completa)
2. Las modifica una por una en el bucle
3. Ejecuta `saveAll()` que genera **N consultas UPDATE individuales** (una por notificación)

Para un usuario con cientos de notificaciones no leídas, esto genera cientos de UPDATEs + un SELECT masivo.

### Instrucciones de Rectificación

Usar una consulta de actualización masiva con `@Modifying @Query`:

```java
@Modifying
@Query("UPDATE Notificacion n SET n.leida = true WHERE n.usuarioId = :usuarioId AND n.leida = false")
int marcarTodasLeidasPorUsuario(@Param("usuarioId") String usuarioId);
```

Llamar desde el servicio:

```java
@Transactional
public void marcarTodasLeidas(String usuarioId) {
    int actualizadas = notificacionRepository.marcarTodasLeidasPorUsuario(usuarioId);
    log.info("{} notificaciones marcadas como leídas para usuario: {}", actualizadas, usuarioId);
}
```

---

## Resumen de Hallazgos por Servicio

| Servicio | Crítico | Alto | Medio | Bajo |
|----------|---------|------|-------|------|
| `catalog-service` | 0 | 1 | 2 | 0 |
| `elending-service` | 0 | 0 | 3 | 0 |
| `license-service` | 0 | 1 | 1 | 0 |
| `identity-services` | 0 | 0 | 1 | 1 |
| `subscription-service` | 0 | 0 | 1 | 0 |
| `notification-service` | 0 | 0 | 1 | 1 |
| `ingestion-service` | 1 | 0 | 0 | 0 |
| **Total** | **1** | **2** | **9** | **2** |

---

## Prioridad de Corrección Recomendada

1. **Inmediata (Crítica):** `@Lob` EAGER en ArchivoLibro.datos → riesgo de OOM y latencia extrema
2. **Alta:** Índices faltantes en `libros` → full table scans en búsquedas frecuentes
3. **Alta:** Paginación en `findAll()` de catálogo y licencias → degradación progresiva
4. **Media:** `@Transactional(readOnly = true)` en 12 métodos de solo lectura
5. **Media:** Carga EAGER de roles en User
6. **Media:** Consulta duplicada en scheduler de préstamos
7. **Baja:** Actualización masiva en notificaciones
8. **Baja:** Transaccional faltante en `loadUserByUsername`
