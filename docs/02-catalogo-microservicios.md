# Catálogo de Microservicios — SmallBooks

> **Documento de referencia rápida** con todos los microservicios, sus puertos, endpoints, entidades y dependencias.

---

## 1. Servicios de Infraestructura

### 1.1 Config Server

| Atributo | Valor |
|----------|-------|
| **Nombre** | `microservice-config` |
| **ID Spring** | `config-server` |
| **Puerto** | `8888` |
| **Descripción** | Servidor de configuración centralizada. Modo nativo, lee YMLs desde `classpath:/configurations/` |
| **Tecnología** | Spring Cloud Config Server |
| **BD** | No |
| **Clase Principal** | `MicroserviceConfigApplication.java` |

### 1.2 Eureka Server

| Atributo | Valor |
|----------|-------|
| **Nombre** | `microservice-eureka` |
| **ID Spring** | `msvc-eureka` |
| **Puerto** | `8761` |
| **Descripción** | Registro y descubrimiento de servicios. No se registra a sí mismo. |
| **Tecnología** | Spring Cloud Netflix Eureka Server |
| **BD** | No |
| **Clase Principal** | `MicroserviceEurekaApplication.java` |

### 1.3 API Gateway

| Atributo | Valor |
|----------|-------|
| **Nombre** | `microservice-gateway` |
| **ID Spring** | `msvc-gateway` |
| **Puerto** | `8080` |
| **Descripción** | Punto de entrada único. Enruta via `lb://`, valida JWT con filtro personalizado. |
| **Tecnología** | Spring Cloud Gateway (reactivo) |
| **BD** | No |
| **Filtros** | `JwtAuthFilter` — valida HMAC + tipo "access" |
| **Swagger** | No expone Swagger (es gateway reactivo) |

---

## 2. Microservicios de Negocio

### 2.1 Identity Service

| Atributo | Valor |
|----------|-------|
| **Nombre carpeta** | `identity-services` |
| **ID Spring** | `identity-service` |
| **Puerto** | `8084` |
| **Base de Datos** | `db_identity` |
| **Paquete base** | `com.silvio.identity` |

#### Entidades JPA

| Entidad | Tabla | Campos clave |
|---------|-------|-------------|
| `User` | `users` | `id` (Long, PK), `username` (único), `password` (BCrypt), `roles` (Set<String>), `resetToken`, `resetTokenExpiry` |

#### Endpoints REST

| Método | Ruta | Descripción | Auth |
|--------|------|-------------|------|
| `POST` | `/auth/login` | Iniciar sesión → access + refresh token | ❌ |
| `POST` | `/auth/register` | Registrar nuevo usuario | ❌ |
| `POST` | `/auth/refresh` | Refrescar access token | ❌ |
| `POST` | `/auth/forgot-password` | Solicitar token de recuperación | ❌ |
| `POST` | `/auth/reset-password` | Restablecer contraseña con token | ❌ |
| `POST` | `/auth/change-password` | Cambiar contraseña (autenticado) | ✅ JWT |

#### DTOs

- `AuthRequest.java` — login (username, password)
- `AuthResponse.java` — extends `RepresentationModel`, contiene accessToken, refreshToken, message, username
- `RegisterRequest.java` — username, password, roles
- `RefreshTokenRequest.java` — refreshToken
- `PasswordResetRequest.java` — username
- `PasswordUpdateRequest.java` — token, newPassword
- `ChangePasswordRequest.java` — currentPassword, newPassword

#### Dependencias Feign

Ninguna (servicio raíz de autenticación). Otros servicios no consultan Identity via Feign — extraen el username del JWT directamente.

---

### 2.2 Catalog Service

| Atributo | Valor |
|----------|-------|
| **Nombre carpeta** | `catalog-service` |
| **ID Spring** | `catalog-service` |
| **Puerto** | `8085` |
| **Base de Datos** | `db_catalog` |
| **Paquete base** | `com.silvio.catalog` |

#### Entidades JPA

| Entidad | Tabla | Campos clave |
|---------|-------|-------------|
| `Libro` | `libros` | `id` (Long, PK), `titulo`, `autor`, `isbn` (único), `editorial`, `anioPublicacion`, `idioma`, `genero`, `sinopsis` (TEXT), `portadaUrl`, `disponible` (boolean) |

#### Endpoints REST

| Método | Ruta | Descripción | Auth |
|--------|------|-------------|------|
| `GET` | `/api/catalog` | Listar todos los libros | ✅ JWT |
| `GET` | `/api/catalog/disponibles` | Listar libros disponibles | ✅ JWT |
| `GET` | `/api/catalog/{id}` | Obtener libro por ID | ✅ JWT |
| `GET` | `/api/catalog/buscar` | Buscar (titulo, autor, genero) | ✅ JWT |
| `POST` | `/api/catalog` | Agregar nuevo libro | ✅ JWT |
| `PUT` | `/api/catalog/{id}` | Actualizar libro | ✅ JWT |
| `PATCH` | `/api/catalog/{id}/disponibilidad` | Cambiar disponibilidad (usado por E-Lending) | ✅ JWT |
| `DELETE` | `/api/catalog/{id}` | Eliminar libro | ✅ JWT |

#### DTOs

- `LibroRequestDTO.java` — entrada con validaciones (ISBN-10/13, año 1450-2100, URL formato)
- `LibroResponseDTO.java` — extends `RepresentationModel<LibroResponseDTO>`

#### Dependencias Feign

Ninguna. Es consumido por Search Service y E-Lending Service.

---

### 2.3 License Service

| Atributo | Valor |
|----------|-------|
| **Nombre carpeta** | `license-service` |
| **ID Spring** | `license-service` |
| **Puerto** | `8086` |
| **Base de Datos** | `db_license` |
| **Paquete base** | `com.silvio.license` |

#### Entidades JPA

| Entidad | Tabla | Campos clave |
|---------|-------|-------------|
| `License` | `licencias` | `id` (Long, PK), `libroId` (Long, único), `totalCopias` (Integer), `copiasDisponibles` (Integer) |

#### Endpoints REST

| Método | Ruta | Descripción | Auth |
|--------|------|-------------|------|
| `GET` | `/api/licenses` | Listar todas las licencias | ✅ JWT |
| `GET` | `/api/licenses/{libroId}` | Obtener licencia por libro | ✅ JWT |
| `POST` | `/api/licenses` | Crear nueva licencia | ✅ JWT |
| `PUT` | `/api/licenses/{libroId}` | Actualizar licencia | ✅ JWT |
| `PUT` | `/api/licenses/{libroId}/prestar` | Descontar copia (prestar) | ✅ JWT |
| `PUT` | `/api/licenses/{libroId}/devolver` | Devolver copia | ✅ JWT |

#### DTOs

- `LicenseRequestDTO.java` — libroId, totalCopias
- `LicenseResponseDTO.java` — extends `RepresentationModel`

#### Dependencias Feign

Ninguna. Es consumido por E-Lending Service.

---

### 2.4 E-Lending Service

| Atributo | Valor |
|----------|-------|
| **Nombre carpeta** | `elending-service` |
| **ID Spring** | `elending-service` |
| **Puerto** | `8087` |
| **Base de Datos** | `db_lending` |
| **Paquete base** | `com.silvio.elending` |

#### Entidades JPA

| Entidad | Tabla | Campos clave |
|---------|-------|-------------|
| `Prestamo` | `prestamos` | `id` (Long, PK), `usuarioId` (String), `libroId` (Long), `fechaInicio` (LocalDateTime), `fechaVencimiento` (LocalDateTime), `estado` (Enum: ACTIVO/VENCIDO) |

#### Endpoints REST

| Método | Ruta | Descripción | Auth |
|--------|------|-------------|------|
| `POST` | `/api/lending/prestamos` | Crear préstamo | ✅ JWT |
| `GET` | `/api/lending/prestamos/activos` | Préstamos activos del usuario | ✅ JWT |
| `GET` | `/api/lending/prestamos/historial` | Historial del usuario | ✅ JWT |
| `GET` | `/api/lending/prestamos/todos` | Todos los préstamos (para Analytics) | ✅ JWT |
| `GET` | `/api/lending/prestamos/historial/{usuarioId}` | Historial por usuario (Analytics) | ✅ JWT |

#### DTOs

- `PrestamoRequestDTO.java` — solo libroId (usuarioId del JWT)
- `PrestamoResponseDTO.java` — extends `RepresentationModel`
- `SuscripcionDTO.java` — DTO de respuesta de Subscription Service
- `LicenciaDTO.java` — DTO de respuesta de License Service (`@JsonIgnoreProperties`)
- `NotificacionRequestDTO.java` — DTO para crear notificación con métodos estáticos: `prestamoCreado()`, `prestamoVencido()`, `proximoVencer()`
- `NotificacionResponseDTO.java` — DTO de respuesta de Notification Service

#### Feign Clients (3)

| Cliente | Destino | Endpoints |
|---------|---------|-----------|
| `SubscriptionClient` | subscription-service | `GET /api/subscriptions/usuario/{usuarioId}` |
| `LicenseClient` | license-service | `GET /api/licenses/{libroId}`, `PUT .../prestar`, `PUT .../devolver` |
| `NotificationClient` | notification-service | `POST /api/notifications` |

#### Scheduler

- `@Scheduled(fixedRate = 3600000)` — cada 1 hora cierra préstamos vencidos

---

### 2.5 Notification Service

| Atributo | Valor |
|----------|-------|
| **Nombre carpeta** | `notification-service` |
| **ID Spring** | `notification-service` |
| **Puerto** | `8088` |
| **Base de Datos** | `db_notifications` |
| **Paquete base** | `com.silvio.notification` |

#### Entidades JPA

| Entidad | Tabla | Campos clave |
|---------|-------|-------------|
| `Notificacion` | `notificaciones` | `id` (Long, PK), `usuarioId` (String), `tipo` (Enum: PRESTAMO_CREADO/PROXIMO_VENCER/VENCIDO), `mensaje` (String 500), `fechaEnvio` (LocalDateTime), `leida` (Boolean) |

#### Endpoints REST

| Método | Ruta | Descripción | Auth |
|--------|------|-------------|------|
| `POST` | `/api/notifications` | Crear notificación (usado por E-Lending) | ✅ JWT |
| `GET` | `/api/notifications/usuario/{usuarioId}` | Notificaciones por usuario | ✅ JWT |
| `GET` | `/api/notifications/usuario/{usuarioId}/no-leidas` | No leídas | ✅ JWT |
| `PATCH` | `/api/notifications/{id}/leer` | Marcar como leída | ✅ JWT |
| `PATCH` | `/api/notifications/usuario/{usuarioId}/leer-todas` | Marcar todas leídas | ✅ JWT |

#### DTOs

- `NotificacionRequestDTO.java` — usuarioId, tipo, mensaje
- `NotificacionDTO.java` — extends `RepresentationModel`

#### Dependencias Feign

Ninguna. Es consumido por E-Lending Service.

---

### 2.6 Subscription Service

| Atributo | Valor |
|----------|-------|
| **Nombre carpeta** | `subscription-service` |
| **ID Spring** | `subscription-service` |
| **Puerto** | `8089` |
| **Base de Datos** | `db_subscriptions` |
| **Paquete base** | `com.silvio.subscription` |

#### Entidades JPA

| Entidad | Tabla | Campos clave |
|---------|-------|-------------|
| `Suscripcion` | `suscripciones` | `id` (Long, PK), `usuarioId` (String), `plan` (Enum: BASICO/PREMIUM), `fechaInicio`, `fechaFin`, `activa` (Boolean) |

#### Endpoints REST

| Método | Ruta | Descripción | Auth |
|--------|------|-------------|------|
| `GET` | `/api/subscriptions/mi-plan` | Plan del usuario autenticado | ✅ JWT |
| `GET` | `/api/subscriptions/usuario/{usuarioId}` | Plan por usuario (para E-Lending) | ✅ JWT |
| `POST` | `/api/subscriptions` | Crear/cambiar suscripción | ✅ JWT |
| `PATCH` | `/api/subscriptions/cancelar` | Cancelar suscripción | ✅ JWT |

#### DTOs

- `SuscripcionRequestDTO.java` — plan, meses
- `SuscripcionResponseDTO.java` — extends `RepresentationModel`, incluye maxPrestamos, diasPrestamo

#### Dependencias Feign

Ninguna. Es consumido por E-Lending Service.

---

### 2.7 Search Service

| Atributo | Valor |
|----------|-------|
| **Nombre carpeta** | `search-service` |
| **ID Spring** | `search-service` |
| **Puerto** | `8090` |
| **Base de Datos** | **Sin BD** (consulta catalog-service via Feign) |
| **Paquete base** | `com.silvio.search` |

#### Endpoints REST

| Método | Ruta | Descripción | Auth |
|--------|------|-------------|------|
| `GET` | `/api/search` | Listar todos los libros | ✅ JWT |
| `GET` | `/api/search/disponibles` | Libros disponibles | ✅ JWT |
| `GET` | `/api/search/buscar` | Buscar (titulo, autor, genero) | ✅ JWT |

#### DTOs

- `LibroCatalogDTO.java` — DTO que recibe de Catalog Service
- `SearchResultDTO.java` — respuesta al cliente

#### Feign Clients (1)

| Cliente | Destino | Endpoints |
|---------|---------|-----------|
| `CatalogClient` | catalog-service | `GET /api/catalog`, `GET /api/catalog/buscar`, `GET /api/catalog/disponibles` |

---

### 2.8 Analytics Service

| Atributo | Valor |
|----------|-------|
| **Nombre carpeta** | `analytics-service` |
| **ID Spring** | `analytics-service` |
| **Puerto** | `8091` |
| **Base de Datos** | **Sin BD** (consulta elending-service via Feign) |
| **Paquete base** | `com.silvio.analytics` |

#### Endpoints REST

| Método | Ruta | Descripción | Auth |
|--------|------|-------------|------|
| `GET` | `/api/analytics/estadisticas` | Estadísticas globales | ✅ JWT |
| `GET` | `/api/analytics/historial/{usuarioId}` | Historial por usuario | ✅ JWT |

#### DTOs

- `EstadisticasDTO.java` — extends `RepresentationModel`, incluye totalPrestamos, prestamosActivos/Vencidos, top5 libros y usuarios
- `PrestamoAnalyticsDTO.java` — extends `RepresentationModel`, incluye `@JsonIgnoreProperties`

#### Feign Clients (1)

| Cliente | Destino | Endpoints |
|---------|---------|-----------|
| `LendingClient` | elending-service | `GET /api/lending/prestamos/todos`, `GET .../historial/{usuarioId}` |

---

### 2.9 Ingestion Service

| Atributo | Valor |
|----------|-------|
| **Nombre carpeta** | `ingestion-service` |
| **ID Spring** | `ingestion-service` |
| **Puerto** | `8092` |
| **Base de Datos** | `db_ingestion` |
| **Paquete base** | `com.silvio.ingestion` |

#### Entidades JPA

| Entidad | Tabla | Campos clave |
|---------|-------|-------------|
| `ArchivoLibro` | `archivos_libros` | `id` (Long, PK), `libroId` (Long, único), `nombreArchivo`, `formato` (PDF/EPUB), `tamanio` (Long), `rutaOClave` (String), `fechaSubida`, `datos` (LONGBLOB) |

#### Endpoints REST

| Método | Ruta | Descripción | Auth |
|--------|------|-------------|------|
| `POST` | `/api/ingestion/upload/{libroId}` | Subir archivo (multipart) | ✅ JWT |
| `GET` | `/api/ingestion/{libroId}` | Obtener info del archivo | ✅ JWT |
| `GET` | `/api/ingestion/{libroId}/bytes` | Obtener bytes (para Content) | ✅ JWT |
| `DELETE` | `/api/ingestion/{libroId}` | Eliminar archivo | ✅ JWT |

#### DTOs

- `ArchivoLibroDTO.java` — metadata del archivo (sin los bytes)

#### Storage Strategy

```
StorageService (interfaz)
    ├── @Primary DatabaseStorageService → guarda en MySQL (activo)
    └── LocalStorageService → guarda en disco (legacy)
```

#### Dependencias Feign

Ninguna. Es consumido por Content Service.

---

### 2.10 Content Delivery Service

| Atributo | Valor |
|----------|-------|
| **Nombre carpeta** | `content-service` |
| **ID Spring** | `content-service` |
| **Puerto** | `8093` |
| **Base de Datos** | **Sin BD** (consulta elending + ingestion via Feign) |
| **Paquete base** | `com.silvio.content` |

#### Endpoints REST

| Método | Ruta | Descripción | Auth |
|--------|------|-------------|------|
| `GET` | `/api/content/{libroId}` | Descargar archivo (requiere préstamo activo) | ✅ JWT |

#### DTOs

- `PrestamoDTO.java` — id, usuarioId, libroId, estado

#### Feign Clients (2)

| Cliente | Destino | Endpoints |
|---------|---------|-----------|
| `LendingClient` | elending-service | `GET /api/lending/prestamos/activos` (con auth header) |
| `IngestionClient` | ingestion-service | `GET /api/ingestion/{libroId}/bytes` |

---

## 3. Resumen de Bases de Datos

| BD | Microservicio | Tablas |
|----|--------------|--------|
| `db_identity` | identity-service | `users`, `user_roles` |
| `db_catalog` | catalog-service | `libros` |
| `db_license` | license-service | `licencias` |
| `db_lending` | elending-service | `prestamos` |
| `db_notifications` | notification-service | `notificaciones` |
| `db_subscriptions` | subscription-service | `suscripciones` |
| `db_ingestion` | ingestion-service | `archivos_libros` |
| Sin BD | search-service | — |
| Sin BD | analytics-service | — |
| Sin BD | content-service | — |

## 4. Resumen de Comunicación Feign

```
                    ┌─────────────────┐
                    │  subscription   │
                    │  -service       │
                    └────────┬────────┘
                             │ GET /usuario/{id}
                    ┌────────▼────────┐
                    │   elending      │
                    │   -service      │
                    └──┬────┬────┬───┘
                       │    │    │
              ┌────────┘    │    └──────────┐
              │             │               │
     ┌────────▼──┐  ┌──────▼──────┐  ┌─────▼────────┐
     │  license  │  │ notification│  │  (scheduler) │
     │  -service │  │  -service   │  │  cierra      │
     └───────────┘  └─────────────┘  │  vencidos    │
                                     └──────────────┘

┌──────────────┐     ┌─────────────────┐
│   search     │────▶│    catalog      │
│   -service   │     │    -service     │
└──────────────┘     └─────────────────┘

┌──────────────┐     ┌─────────────────┐
│  analytics   │────▶│   elending      │
│  -service    │     │   -service      │
└──────────────┘     └─────────────────┘

┌──────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   content    │────▶│   elending      │────▶│   ingestion     │
│   -service   │     │   -service      │     │   -service      │
└──────────────┘     └─────────────────┘     └─────────────────┘
```
