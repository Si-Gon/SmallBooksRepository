# Documento de Arquitectura General — SmallBooks

> **Proyecto:** SmallBooks — Plataforma de Biblioteca Digital  
> **Versión:** 1.0  
> **Última actualización:** Julio 2026  
> **Stack:** Spring Boot 3.3.11 + Spring Cloud 2023.0.5 + Java 17

---

## 1. Visión General del Sistema

SmallBooks es una plataforma de biblioteca digital construida sobre una arquitectura de **13 microservicios independientes**. Permite a los usuarios explorar un catálogo de libros, gestionar suscripciones (BÁSICO/PREMIUM), solicitar préstamos digitales y acceder al contenido desde cualquier lugar.

### Diagrama de Arquitectura

```
                            ┌─────────────────────┐
                            │   Config Server     │ :8888
                            │  (Git-backed/Native)│
                            └──────────┬──────────┘
                                       │ configuración centralizada
                            ┌──────────▼──────────┐
                            │   Eureka Server     │ :8761
                            │   (Service Discovery)│
                            └──────────┬──────────┘
                                       │ registro de servicios
                            ┌──────────▼──────────┐
                            │   API Gateway       │ :8080
                            │(Spring Cloud Gateway)│
                            │  + JwtAuthFilter    │
                            └──────────┬──────────┘
                                       │
          ┌────────────────────────────┼────────────────────────────┐
          │              ┌─────────────▼─────────────┐              │
          │              │                             │              │
   ┌──────▼──────┐  ┌───▼────────┐  ┌────────▼─────┐  ┌───▼────────┐
   │  Identity   │  │  Catalog   │  │  E-Lending   │  │  License   │
   │   :8084     │  │  :8085     │  │  :8087       │  │  :8086     │
   │  JWT Auth   │  │  CRUD      │  │  Préstamos   │  │  Licencias │
   │  Usuarios   │  │  Libros    │  │  + Scheduler │  │  Copias    │
   └──────┬──────┘  └─────┬──────┘  └──────┬───────┘  └─────┬──────┘
          │               │                │                 │
   ┌──────▼──────┐  ┌───▼────────┐  ┌──────▼───────┐  ┌─────▼──────┐
   │ Subscription│  │  Search    │  │ Notification │  │  Analytics │
   │   :8089     │  │  :8090     │  │  :8088       │  │  :8091     │
   │  Planes     │  │  Búsqueda  │  │  Alertas     │  │  Métricas  │
   │  BAS/PREMIUM│  │  via Feign │  │  Usuario     │  │  via Feign │
   └─────────────┘  └────────────┘  └──────────────┘  └─────┬──────┘
                                                             │
                    ┌──────────────────────┐                 │
                    │   Ingestion Service  │◄────────────────┘
                    │   :8092              │
                    │   Upload PDF/EPUB    │
                    │   BLOB MySQL         │
                    └──────────┬───────────┘
                               │
                    ┌──────────▼───────────┐
                    │   Content Delivery   │
                    │   :8093              │
                    │   Download con       │
                    │   validación préstamo│
                    └──────────────────────┘
```

### Flujo de Petición Típico

```
Cliente → API Gateway (:8080) → JwtAuthFilter (valida JWT) → 
→ lb://servicio-destino → Microservicio → MySQL (BD propia)
```

---

## 2. Stack Tecnológico

| Categoría | Tecnología | Versión |
|-----------|-----------|---------|
| **Lenguaje** | Java | 17 |
| **Framework Base** | Spring Boot | 3.3.11 |
| **Cloud** | Spring Cloud | 2023.0.5 |
| **Service Discovery** | Netflix Eureka | (Spring Cloud) |
| **API Gateway** | Spring Cloud Gateway | Reactivo |
| **Config Centralizada** | Spring Cloud Config Server | Nativo |
| **Base de Datos** | MySQL | 8.4 |
| **Migraciones** | Flyway | (Spring Boot) |
| **ORM** | Spring Data JPA + Hibernate | 6.5 |
| **Seguridad** | Spring Security + JWT (jjwt 0.11.5) | — |
| **Documentación API** | SpringDoc OpenAPI | 2.5.0 |
| **Hipermedia** | Spring HATEOAS | (Spring Boot) |
| **Comunicación MS** | OpenFeign | (Spring Cloud) |
| **Build** | Maven (multi-módulo) | — |
| **Testing** | JUnit 5 + Mockito + JaCoCo | 0.8.11 |
| **Contenedores** | Docker + Docker Compose | — |

---

## 3. Patrones Arquitectónicos

### 3.1 Microservicios

Cada microservicio es **independiente** con:
- Base de datos propia (schema por servicio)
- Ciclo de vida independiente (build, deploy, scale)
- Puerto y nombre únicos registrados en Eureka

### 3.2 REST con HATEOAS

Todas las respuestas incluyen enlaces `_links` para navegabilidad siguiendo el principio HATEOAS. Ejemplo:

```json
{
  "id": 1,
  "titulo": "Don Quijote",
  "_links": {
    "self":  { "href": "http://localhost:8085/api/catalog/1" },
    "todos": { "href": "http://localhost:8085/api/catalog" },
    "disponibles": { "href": "http://localhost:8085/api/catalog/disponibles" },
    "eliminar": { "href": "http://localhost:8085/api/catalog/1" }
  }
}
```

### 3.3 CSR Estricto (Controller → Service → Repository)

Cada microservicio sigue el patrón de 3 capas:
- **Controller**: Endpoints REST, validación, HATEOAS
- **Service**: Lógica de negocio, orquestación Feign
- **Repository**: Acceso a datos JPA

### 3.4 Comunicación Síncrona con OpenFeign

Los microservicios se comunican mediante **Feign Clients** que resuelven la URL destino via Eureka (load balancer). Tiempo de espera configurado globalmente:
- `connectTimeout`: 3 segundos
- `readTimeout`: 5 segundos

### 3.5 Patrón de Compensación (Saga Coreográfica)

El flujo de creación de préstamo implementa compensación automática: si falla el paso de guardado en BD tras descontar la copia, se restaura la copia automáticamente.

---

## 4. Service Discovery con Eureka

- **Eureka Server** (`msvc-eureka`) corre en puerto `:8761`
- Cada microservicio se registra con su `spring.application.name`
- El Gateway usa `lb://` (load balancer) para enrutar a los servicios
- Eureka no se registra a sí mismo (`register-with-eureka: false`)

---

## 5. API Gateway

- **Spring Cloud Gateway** reactivo en puerto `:8080`
- **JwtAuthFilter**: Filtro personalizado que valida el JWT en cada request (excepto `/auth/**`)
- Verifica: existencia del header `Authorization: Bearer`, firma HMAC-SHA, tipo `access` (no refresh)
- Enrutamiento via `lb://` con `Discovery Locator` habilitado

### Rutas del Gateway

| Ruta | Destino | Autenticación |
|------|---------|--------------|
| `/auth/**` | identity-service:8084 | ❌ Público |
| `/api/catalog/**` | catalog-service:8085 | ✅ JWT |
| `/api/licenses/**` | license-service:8086 | ✅ JWT |
| `/api/lending/**` | elending-service:8087 | ✅ JWT |
| `/api/notifications/**` | notification-service:8088 | ✅ JWT |
| `/api/subscriptions/**` | subscription-service:8089 | ✅ JWT |
| `/api/search/**` | search-service:8090 | ✅ JWT |
| `/api/analytics/**` | analytics-service:8091 | ✅ JWT |
| `/api/ingestion/**` | ingestion-service:8092 | ✅ JWT |
| `/api/content/**` | content-service:8093 | ✅ JWT |

---

## 6. Seguridad con JWT

### Esquema de Tokens

| Token | Duración (desarrollo) | Propósito |
|-------|----------------------|-----------|
| **Access Token** | 30 minutos | Autenticar requests (header `Authorization`) |
| **Refresh Token** | 7 días | Renovar access token sin re-login |

### Flujo de Autenticación

```
1. POST /auth/login → username+password → JWT (access + refresh)
2. Cliente almacena tokens (localStorage/sessionStorage)
3. Cada request incluye: Authorization: Bearer {accessToken}
4. Gateway valida la firma y expiración del JWT
5. Si expiró → POST /auth/refresh con refresh token → nuevos tokens
```

### JWT Secret

- Clave HMAC compartida: `Duoc.1983Duoc.1983Duoc.1983Duoc.1983`
- Configurada en `identity-service.yml` y `msvc-gateway.yml`
- El JWT incluye claims: `username`, `type` ("access"|"refresh"), `roles`

---

## 7. Base de Datos y Flyway

### Bases de Datos

| Microservicio | Base de Datos | Con BD Propia |
|--------------|--------------|--------------|
| identity-service | `db_identity` | ✅ |
| catalog-service | `db_catalog` | ✅ |
| license-service | `db_license` | ✅ |
| elending-service | `db_lending` | ✅ |
| notification-service | `db_notifications` | ✅ |
| subscription-service | `db_subscriptions` | ✅ |
| ingestion-service | `db_ingestion` | ✅ |
| search-service | Sin BD | ❌ (consulta catalog via Feign) |
| analytics-service | Sin BD | ❌ (consulta elending via Feign) |
| content-service | Sin BD | ❌ (consulta ingestion via Feign) |

### Flyway

- Migraciones automáticas al iniciar cada servicio
- Configuración: `baseline-on-migrate: true`, `ddl-auto: validate`
- Los servicios sin BD excluyen autoconfiguración de DataSource

---

## 8. Configuración Centralizada

- **Config Server** (`microservice-config`) en puerto `:8888`
- Modo nativo: busca archivos YML en `classpath:/configurations/`
- Cada microservicio tiene su propio archivo de configuración
- Parámetros comunes (timouts Feign) en `application.yml` global

### Orden de Arranque

```
1. microservice-config  :8888
2. microservice-eureka  :8761
3. identity-services    :8084
4. microservice-gateway :8080
5. Resto de MS          (cualquier orden)
```

---

## 9. Comunicación entre Microservicios (OpenFeign)

### Mapa de Dependencias

| Microservicio Origen | Cliente Feign | Microservicio Destino | Endpoints Consumidos |
|---------------------|--------------|----------------------|---------------------|
| **elending-service** | `SubscriptionClient` | subscription-service | `GET /api/subscriptions/usuario/{usuarioId}` |
| **elending-service** | `LicenseClient` | license-service | `GET /api/licenses/{libroId}`, `PUT /api/licenses/{libroId}/prestar`, `PUT /api/licenses/{libroId}/devolver` |
| **elending-service** | `NotificationClient` | notification-service | `POST /api/notifications` |
| **search-service** | `CatalogClient` | catalog-service | `GET /api/catalog`, `GET /api/catalog/buscar`, `GET /api/catalog/disponibles` |
| **analytics-service** | `LendingClient` | elending-service | `GET /api/lending/prestamos/todos`, `GET /api/lending/prestamos/historial/{usuarioId}` |
| **content-service** | `LendingClient` | elending-service | `GET /api/lending/prestamos/activos` (con auth header) |
| **content-service** | `IngestionClient` | ingestion-service | `GET /api/ingestion/{libroId}/bytes` |

### Observabilidad y Resiliencia

- **Logging estructurado** con SLF4J en todos los servicios
- **IDs de correlación**: No implementado aún (pendiente de verificar)
- **Circuit Breaker**: No implementado (pendiente — los errores Feign se manejan con try-catch)
- **Trazabilidad distribuida**: Pendiente de implementar

---

## 10. Swagger / OpenAPI

Cada microservicio expone su documentación interactiva en:

```
http://localhost:{puerto}/swagger-ui/index.html
```

| Microservicio | URL |
|--------------|-----|
| Identity | http://localhost:8084/swagger-ui/index.html |
| Catalog | http://localhost:8085/swagger-ui/index.html |
| License | http://localhost:8086/swagger-ui/index.html |
| E-Lending | http://localhost:8087/swagger-ui/index.html |
| Notification | http://localhost:8088/swagger-ui/index.html |
| Subscription | http://localhost:8089/swagger-ui/index.html |
| Search | http://localhost:8090/swagger-ui/index.html |
| Analytics | http://localhost:8091/swagger-ui/index.html |
| Ingestion | http://localhost:8092/swagger-ui/index.html |
| Content | http://localhost:8093/swagger-ui/index.html |

---

## 11. Pruebas y Cobertura

| Tipo | Microservicios | Tests |
|------|---------------|-------|
| Unitarios (Service) | catalog-service, elending-service, identity-service | 33 |
| REST MockMvc (Controller) | license-service, subscription-service, notification-service | 33 |
| **Total** | **6 microservicios** | **66 tests — 0 fallos** |

- Herramienta: JUnit 5 + Mockito
- Cobertura: JaCoCo (>80% en todos los MS)
- Estructura de tests: Given / When / Then

---

## 12. Reglas de Negocio

### Planes de Suscripción

| Plan | Préstamos Simultáneos | Duración |
|------|----------------------|----------|
| **BÁSICO** | 2 | 7 días |
| **PREMIUM** | 5 | 14 días |

### Flujo de Creación de Préstamo

```
1. Verificar plan del usuario → Subscription Service (Feign)
2. Verificar límite de préstamos activos según plan
3. Verificar que el usuario no tenga ya ese libro prestado
4. Verificar copias disponibles → License Service (Feign)
5. Descontar 1 copia → License Service (Feign)
6. Crear registro de préstamo en BD
   └→ Si falla: compensación automática (restaurar copia)
7. Notificar al usuario → Notification Service (Feign)
   └→ Si falla: silencioso, no bloquea el flujo
```

### Scheduler de Vencimientos

- `PrestamoService.cerrarPrestamosVencidos()` se ejecuta **cada 1 hora**
- Detecta préstamos vencidos, devuelve copias a License Service, genera notificaciones
- También notifica préstamos próximos a vencer (2 días antes)

---

## 13. Almacenamiento de Archivos

### Patrón Strategy para Storage

```
StorageService (Interfaz)
    ├── LocalStorageService    → guarda en disco (legacy, sin @Primary)
    └── DatabaseStorageService → guarda en MySQL  (@Primary — activo)
```

- **Activo**: `DatabaseStorageService` almacena archivos como `LONGBLOB` en MySQL
- La entidad `ArchivoLibro` tiene campo `datos` de tipo `LONGBLOB`
- Tamaño máximo de subida: 50MB
- Formatos permitidos: PDF, EPUB

---

## 14. Estructura del Proyecto

```
SmallBooksRepository/
├── pom.xml                              ← POM raíz multi-módulo
├── docker-compose.yml                   ← 13 servicios + healthchecks
├── README.md                            ← Documentación principal
├── microservice-config/                 ← Config Server (:8888)
├── microservice-eureka/                 ← Eureka Server (:8761)
├── microservice-gateway/                ← API Gateway (:8080)
├── identity-services/                   ← Auth JWT (:8084)
├── catalog-service/                     ← Catálogo libros (:8085)
├── license-service/                     ← Control copias (:8086)
├── elending-service/                    ← Préstamos digitales (:8087)
├── notification-service/                ← Notificaciones (:8088)
├── subscription-service/                ← Planes suscripción (:8089)
├── search-service/                      ← Búsqueda libros (:8090)
├── analytics-service/                   ← Estadísticas (:8091)
├── ingestion-service/                   ← Carga archivos (:8092)
├── content-service/                     ← Entrega contenido (:8093)
├── reports/
│   └── jacoco/                          ← Reportes cobertura
└── docs/                                ← Documentación técnica
```
