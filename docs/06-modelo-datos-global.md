# Modelo de Datos Global — SmallBooks

> **Entidades de cada microservicio, relaciones entre servicios y campos/IDs compartidos.**

---

## 1. Diagrama de Entidades por Microservicio

```
┌─────────────────────────────────────────────────────────────────┐
│                     db_identity                                  │
│  ┌──────────────┐    ┌──────────────────┐                      │
│  │    users      │    │   user_roles     │                      │
│  │──────────────│    │──────────────────│                      │
│  │id (PK)       │───>│user_id (FK)      │                      │
│  │username (UQ) │    │role              │                      │
│  │password      │    └──────────────────┘                      │
│  │resetToken    │                                               │
│  │resetTokenExp │                                               │
│  └──────────────┘                                               │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                     db_catalog                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                     libros                                │   │
│  │──────────────────────────────────────────────────────────│   │
│  │id (PK) | titulo | autor | isbn (UQ) | editorial |       │   │
│  │anioPublicacion | idioma | genero | sinopsis |            │   │
│  │portadaUrl | disponible                                    │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                     db_license                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    licencias                               │   │
│  │──────────────────────────────────────────────────────────│   │
│  │id (PK) | libro_id (UQ) | total_copias | copias_disponibles│  │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                     db_lending                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                    prestamos                               │   │
│  │──────────────────────────────────────────────────────────│   │
│  │id (PK) | usuario_id | libro_id | fecha_inicio |          │   │
│  │fecha_vencimiento | estado (ACTIVO/VENCIDO)               │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                     db_notifications                             │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                  notificaciones                            │   │
│  │──────────────────────────────────────────────────────────│   │
│  │id (PK) | usuario_id | tipo (enum) | mensaje |            │   │
│  │fecha_envio | leida                                        │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                     db_subscriptions                             │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                  suscripciones                             │   │
│  │──────────────────────────────────────────────────────────│   │
│  │id (PK) | usuario_id | plan (BASICO/PREMIUM) |           │   │
│  │fecha_inicio | fecha_fin | activa                         │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                     db_ingestion                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │                  archivos_libros                           │   │
│  │──────────────────────────────────────────────────────────│   │
│  │id (PK) | libro_id (UQ) | nombre_archivo | formato |      │   │
│  │tamanio | ruta_o_clave | fecha_subida | datos (LONGBLOB)  │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. Entidades Detalladas

### 2.1 identity-service → `db_identity`

#### `users`

| Columna | Tipo | Restricciones | Descripción |
|---------|------|--------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | ID único del usuario |
| `username` | VARCHAR | UNIQUE, NOT NULL | Nombre de usuario (login) |
| `password` | VARCHAR | NOT NULL | Hash BCrypt de la contraseña |
| `reset_token` | VARCHAR | NULL | Token para recuperación de contraseña |
| `reset_token_expiry` | DATETIME | NULL | Fecha de expiración del token |

#### `user_roles`

| Columna | Tipo | Restricciones | Descripción |
|---------|------|--------------|-------------|
| `user_id` | BIGINT | FK → users.id | ID del usuario |
| `role` | VARCHAR | NOT NULL | Rol (ej: ROLE_USER, ROLE_PREMIUM, ROLE_LIBRARIAN) |

### 2.2 catalog-service → `db_catalog`

#### `libros`

| Columna | Tipo | Restricciones | Descripción |
|---------|------|--------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | ID único del libro |
| `titulo` | VARCHAR(200) | NOT NULL | Título del libro |
| `autor` | VARCHAR(150) | NOT NULL | Autor del libro |
| `isbn` | VARCHAR(20) | UNIQUE, NOT NULL | ISBN (10 o 13 dígitos) |
| `editorial` | VARCHAR(150) | NULL | Editorial |
| `anio_publicacion` | INT | NULL | Año de publicación |
| `idioma` | VARCHAR(50) | NULL | Idioma del libro |
| `genero` | VARCHAR(100) | NULL | Género literario |
| `sinopsis` | TEXT | NULL | Resumen del libro |
| `portada_url` | VARCHAR(500) | NULL | URL de la imagen de portada |
| `disponible` | BOOLEAN | NOT NULL, DEFAULT TRUE | Disponible para préstamo |

### 2.3 license-service → `db_license`

#### `licencias`

| Columna | Tipo | Restricciones | Descripción |
|---------|------|--------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | ID único de la licencia |
| `libro_id` | BIGINT | UNIQUE, NOT NULL | ID del libro (referencia catalog-service) |
| `total_copias` | INT | NOT NULL, MIN 1 | Total de copias adquiridas |
| `copias_disponibles` | INT | NOT NULL | Copias disponibles actualmente |

### 2.4 elending-service → `db_lending`

#### `prestamos`

| Columna | Tipo | Restricciones | Descripción |
|---------|------|--------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | ID único del préstamo |
| `usuario_id` | VARCHAR(100) | NOT NULL | Username (referencia identity-service) |
| `libro_id` | BIGINT | NOT NULL | ID del libro (referencia catalog-service) |
| `fecha_inicio` | DATETIME | NOT NULL | Fecha de inicio del préstamo |
| `fecha_vencimiento` | DATETIME | NOT NULL | Fecha de vencimiento calculada |
| `estado` | VARCHAR(20) | NOT NULL, ENUM('ACTIVO','VENCIDO') | Estado del préstamo |

### 2.5 notification-service → `db_notifications`

#### `notificaciones`

| Columna | Tipo | Restricciones | Descripción |
|---------|------|--------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | ID único de la notificación |
| `usuario_id` | VARCHAR(100) | NOT NULL | Usuario destino (referencia identity-service) |
| `tipo` | VARCHAR(30) | NOT NULL, ENUM('PRESTAMO_CREADO','PROXIMO_VENCER','VENCIDO') | Tipo de notificación |
| `mensaje` | VARCHAR(500) | NOT NULL | Mensaje descriptivo |
| `fecha_envio` | DATETIME | NOT NULL | Fecha de creación |
| `leida` | BOOLEAN | NOT NULL, DEFAULT FALSE | Si fue leída por el usuario |

### 2.6 subscription-service → `db_subscriptions`

#### `suscripciones`

| Columna | Tipo | Restricciones | Descripción |
|---------|------|--------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | ID único de la suscripción |
| `usuario_id` | VARCHAR(100) | NOT NULL | Usuario (referencia identity-service) |
| `plan` | VARCHAR(20) | NOT NULL, ENUM('BASICO','PREMIUM') | Plan de suscripción |
| `fecha_inicio` | DATETIME | NOT NULL | Inicio de la suscripción |
| `fecha_fin` | DATETIME | NOT NULL | Fin de la suscripción |
| `activa` | BOOLEAN | NOT NULL, DEFAULT TRUE | Si la suscripción está activa |

### 2.7 ingestion-service → `db_ingestion`

#### `archivos_libros`

| Columna | Tipo | Restricciones | Descripción |
|---------|------|--------------|-------------|
| `id` | BIGINT | PK, AUTO_INCREMENT | ID único del registro |
| `libro_id` | BIGINT | UNIQUE, NOT NULL | ID del libro (referencia catalog-service) |
| `nombre_archivo` | VARCHAR | NOT NULL | Nombre original del archivo |
| `formato` | VARCHAR(10) | NOT NULL | Formato: PDF o EPUB |
| `tamanio` | BIGINT | NOT NULL | Tamaño en bytes |
| `ruta_o_clave` | VARCHAR | NOT NULL | Clave de almacenamiento (ej: "db:1") |
| `fecha_subida` | DATETIME | NOT NULL | Fecha de subida |
| `datos` | LONGBLOB | NULL | Contenido binario del archivo |

---

## 3. Relaciones entre Entidades de Diferentes Servicios

### 3.1 IDs Compartidos (Sin FK en BD)

Los microservicios se relacionan por **convención de identificadores**, no por claves foráneas en la base de datos:

```
identity-service                catalog-service
┌──────────┐                   ┌──────────┐
│  users   │                   │  libros  │
│───────│                   │────────│
│ username│◄────────────────┐ │ id (Long)│◄───────────────────┐
└──────────┘  (usuarioId)   │ └──────────┘  (libroId)        │
                             │                               │
subscription-service         │ license-service                │
┌──────────────┐             │ ┌──────────┐                  │
│ suscripciones│             │ │ licencias│                  │
│──────────────│             │ │─────────│                  │
│ usuario_id  ─┘             │ │ libro_id ───────────────────┘
└──────────────┘              │ └──────────┘
                             │
elending-service              │ ingestion-service
┌──────────┐                  │ ┌──────────────┐
│ prestamos│                  │ │archivos_libros│
│─────────│                  │ │──────────────│
│ usuario_id ─────────────────┘ │ libro_id ─────────────────┘
│ libro_id   ───────────────────┘└──────────────┘
└──────────┘                  
                             
notification-service          content-service (sin BD)
┌──────────────┐              ┌──────────────────┐
│ notificaciones│              │ Usa libroId del  │
│──────────────│              │ catálogo y        │
│ usuario_id   ───────────────── usuarioId del JWT│
│ (referencia) │              └──────────────────┘
└──────────────┘
```

### 3.2 Relaciones Lógicas

| Relación | Servicio A | Servicio B | Campo de Enlace |
|----------|-----------|-----------|----------------|
| Usuario → Suscripción | identity-service | subscription-service | `users.username` ↔ `suscripciones.usuario_id` |
| Usuario → Préstamo | identity-service | elending-service | `users.username` ↔ `prestamos.usuario_id` |
| Usuario → Notificación | identity-service | notification-service | `users.username` ↔ `notificaciones.usuario_id` |
| Libro → Licencia | catalog-service | license-service | `libros.id` ↔ `licencias.libro_id` |
| Libro → Préstamo | catalog-service | elending-service | `libros.id` ↔ `prestamos.libro_id` |
| Libro → Archivo | catalog-service | ingestion-service | `libros.id` ↔ `archivos_libros.libro_id` |

### 3.3 Cardinalidades

| Relación | Cardinalidad | Explicación |
|----------|-------------|-------------|
| Usuario : Suscripción | 1 : N | Un usuario puede tener múltiples suscripciones (solo una activa) |
| Usuario : Préstamo | 1 : N | Un usuario puede tener múltiples préstamos (activos según plan) |
| Usuario : Notificación | 1 : N | Un usuario puede tener múltiples notificaciones |
| Libro : Licencia | 1 : 1 | Un libro tiene una única licencia (regla de negocio: `libro_id` es UNIQUE) |
| Libro : Préstamo | 1 : N | Un libro puede ser prestado múltiples veces (a diferentes usuarios o momentos) |
| Libro : Archivo | 1 : 1 | Un libro tiene un único archivo (`libro_id` es UNIQUE en `archivos_libros`) |

---

## 4. Reglas de Negocio en Datos

### Planes de Suscripción

| Plan | maxPrestamos | diasPrestamo | Duración Suscripción |
|------|-------------|-------------|---------------------|
| **BÁSICO** | 2 | 7 | N/A (gratuito, por defecto) |
| **PREMIUM** | 5 | 14 | Configurable en meses |

### Flujo de Datos en Préstamo

```
1. [subscription-service] → GET /usuario/{id}
   Retorna: plan, maxPrestamos, diasPrestamo

2. [elending-service] → Validación local
   Contar prestamos ACTIVOS del usuario < maxPrestamos

3. [elending-service] → Validación local
   Verificar que no exista prestamo ACTIVO del mismo libro para el mismo usuario

4. [license-service] → GET /{libroId}
   Retorna: copiasDisponibles

5. [license-service] → PUT /{libroId}/prestar
   copiasDisponibles -= 1

6. [elending-service] → INSERT en prestamos
   usuarioId, libroId, fechaInicio=ahora, fechaVencimiento=ahora+diasPrestamo, estado=ACTIVO

7. (Si falla paso 6) → [license-service] → PUT /{libroId}/devolver
   copiasDisponibles += 1 (compensación)

8. [notification-service] → POST /notifications
   Crear notificación PRESTAMO_CREADO
```

### Flujo de Datos en Vencimiento (Scheduler)

```
Cada 1 hora:

1. SELECT * FROM prestamos WHERE estado='ACTIVO' AND fecha_vencimiento < NOW()

2. Por cada préstamo vencido:
   a. [license-service] → PUT /{libroId}/devolver → copiasDisponibles += 1
   b. UPDATE prestamos SET estado='VENCIDO'
   c. [notification-service] → POST → tipo=VENCIDO
```

---

## 5. Campos Compartidos entre Servicios

### `usuarioId` (String — username)

Es el identificador de **identidad compartida** más usado en el sistema. Se propaga de las siguientes formas:

| Método de Propagación | Descripción |
|----------------------|-------------|
| **JWT Claim `sub`** | El username viaja dentro del token JWT firmado |
| **Header HTTP** | El username se pasa en el path `/api/subscriptions/usuario/{usuarioId}` |
| **Request Body** | En notificaciones y otros DTOs |

### `libroId` (Long)

Es el identificador compartido para referenciar libros del catálogo:

| Servicio | Campo |
|----------|-------|
| catalog-service | `libros.id` (origen) |
| license-service | `licencias.libro_id` |
| elending-service | `prestamos.libro_id` |
| notification-service | Dentro del mensaje de texto |
| ingestion-service | `archivos_libros.libro_id` |
| content-service | Path variable `{libroId}` |
| analytics-service | `PrestamoAnalyticsDTO.libroId` |

---

## 6. Índices y Constraints

### Índices Únicos

| Servicio | Tabla | Columna(s) | Tipo |
|----------|-------|-----------|------|
| identity-service | `users` | `username` | UNIQUE |
| catalog-service | `libros` | `isbn` | UNIQUE |
| license-service | `licencias` | `libro_id` | UNIQUE |
| ingestion-service | `archivos_libros` | `libro_id` | UNIQUE |

### Índices de Búsqueda

| Servicio | Tabla | Columna(s) | Propósito |
|----------|-------|-----------|-----------|
| elending-service | `prestamos` | `usuario_id + estado` | Buscar préstamos activos por usuario |
| elending-service | `prestamos` | `estado + fecha_vencimiento` | Scheduler de vencimientos |
| notification-service | `notificaciones` | `usuario_id` | Listar notificaciones por usuario |
| notification-service | `notificaciones` | `usuario_id + leida` | Buscar no leídas |
| subscription-service | `suscripciones` | `usuario_id + activa` | Buscar suscripción activa |

---

## 7. Resumen de Volumen de Datos Estimado

| Tabla | Registros Estimados | Crecimiento |
|-------|-------------------|-------------|
| `users` | Cientos | Bajo |
| `libros` | Miles | Medio (según catálogo) |
| `licencias` | Miles | Medio |
| `prestamos` | Decenas de miles | Alto |
| `notificaciones` | Decenas de miles | Alto |
| `suscripciones` | Cientos | Bajo |
| `archivos_libros` | Miles | Medio (archivos BLOB grandes) |

---

## 8. Pendientes de Verificación

- [ ] **Migraciones Flyway**: No se encontraron archivos SQL de migración en el repositorio. Las migraciones pueden estar generadas por Hibernate o faltan los scripts de Flyway.
- [ ] **DDL exacto**: Las tablas se crean con `ddl-auto: validate`, lo que sugiere que Flyway gestiona el schema. Sin embargo, no se ubicaron los archivos de migración en `src/main/resources/db/migration/`.
- [ ] **Índices**: La creación de índices depende de las migraciones Flyway. No se pudo verificar si existen índices adicionales no documentados aquí.
- [ ] **Relaciones en ingestion-service**: La entidad `ArchivoLibro` tiene tanto `rutaOClave` (para almacenamiento legacy) como `datos` (LONGBLOB para BD activa), lo que sugiere una migración en curso.
