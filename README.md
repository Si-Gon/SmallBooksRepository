# SmallBooks — Plataforma de Biblioteca Digital

> Proyecto académico desarrollado en **Duoc UC** · Ingeniería Informática · DSY1103 Desarrollo FullStack 1  
> Arquitectura de microservicios con Spring Boot 3.3.11 y Spring Cloud 2023.0.5

---

## Equipo de Desarrollo

| Nombre | Rol |
|---|---|
| Silvio Gonzalves | Lider tecnico / Backend |
| Oscar Garrido | Backend |
| Juan Ortega | QA |

---

## Descripción

SmallBooks es una plataforma de biblioteca digital que permite a los usuarios explorar un catálogo de libros, solicitar préstamos digitales según su plan de suscripción (BÁSICO o PREMIUM), y acceder al contenido desde cualquier lugar. El sistema está construido sobre una arquitectura de **13 microservicios independientes** orquestados con Spring Cloud.

---

## Arquitectura

```
                        ┌─────────────────┐
                        │  Config Server  │ :8888
                        │  (Git-backed)   │
                        └────────┬────────┘
                                 │ configuración centralizada
                        ┌────────▼────────┐
                        │  Eureka Server  │ :8761
                        │  (Discovery)    │
                        └────────┬────────┘
                                 │ registro de servicios
                        ┌────────▼────────┐
                        │  API Gateway    │ :8080
                        │  (Spring Cloud) │
                        └────────┬────────┘
                                 │
          ┌──────────────────────┼──────────────────────┐
          │                      │                      │
   ┌──────▼──────┐      ┌───────▼───────┐     ┌───────▼───────┐
   │  Identity   │      │    Catalog    │     │   E-Lending   │
   │   :8084     │      │    :8085      │     │    :8087      │
   └─────────────┘      └───────────────┘     └───────────────┘
          │                      │                      │
   ┌──────▼──────┐      ┌───────▼───────┐     ┌───────▼───────┐
   │  License    │      │ Subscription  │     │ Notification  │
   │   :8086     │      │    :8089      │     │    :8088      │
   └─────────────┘      └───────────────┘     └───────────────┘
          │                      │                      │
   ┌──────▼──────┐      ┌───────▼───────┐     ┌───────▼───────┐
   │  Ingestion  │      │    Search     │     │   Analytics   │
   │   :8092     │      │    :8090      │     │    :8091      │
   └─────────────┘      └───────────────┘     └───────────────┘
          │
   ┌──────▼──────┐
   │   Content   │
   │  Delivery   │
   │   :8093     │
   └─────────────┘
```

---

## Microservicios

| Microservicio | Puerto | Descripción |
|---|---|---|
| `microservice-config` | 8888 | Servidor de configuración centralizada |
| `microservice-eureka` | 8761 | Registro y descubrimiento de servicios |
| `microservice-gateway` | 8080 | API Gateway — punto de entrada único |
| `identity-services` | 8084 | Autenticación JWT, registro y gestión de contraseñas |
| `catalog-service` | 8085 | Catálogo de libros (CRUD completo) |
| `license-service` | 8086 | Control de copias disponibles por libro |
| `elending-service` | 8087 | Préstamos digitales con reglas BÁSICO/PREMIUM |
| `notification-service` | 8088 | Gestión de notificaciones a usuarios |
| `subscription-service` | 8089 | Planes de suscripción BÁSICO y PREMIUM |
| `search-service` | 8090 | Búsqueda y descubrimiento de libros |
| `analytics-service` | 8091 | Métricas y estadísticas de uso |
| `ingestion-service` | 8092 | Carga de archivos PDF/EPUB → MySQL BLOB |
| `content-service` | 8093 | Entrega de archivos a usuarios con préstamo activo |

---

## Database Requerido
CREATE DATABASE db_catalog;
CREATE DATABASE db_lending;
CREATE DATABASE db_identity;
CREATE DATABASE db_license;
CREATE DATABASE db_subscription;
CREATE DATABASE db_notification;
CREATE DATABASE db_ingestion;
CREATE DATABASE db_search;
CREATE DATABASE db_analytics;

---

## Stack Tecnológico

| Categoría | Tecnología |
|---|---|
| **Framework** | Spring Boot 3.3.11 |
| **Cloud** | Spring Cloud 2023.0.5 |
| **Base de datos** | MySQL 8.4 (Laragon) |
| **Migraciones** | Flyway |
| **ORM** | Spring Data JPA + Hibernate 6.5 |
| **Seguridad** | Spring Security + JWT |
| **Comunicación inter-servicio** | OpenFeign + Eureka Load Balancer |
| **Documentación API** | SpringDoc OpenAPI 2.5.0 (Swagger UI) |
| **Hipermedia** | Spring HATEOAS |
| **Build** | Maven multi-módulo |
| **Utilidades** | Lombok, SLF4J, Bean Validation |
| **Testing** | JUnit 5 + Mockito |
| **IDE** | VSCode + Spring Boot Dashboard |

---

## Nuevas integraciones v2.0

### Swagger / OpenAPI

Todos los microservicios con endpoints REST cuentan con documentación interactiva generada automáticamente. La interfaz permite explorar y probar los endpoints directamente desde el navegador.

**Acceso a Swagger UI** (con el microservicio corriendo):

```
http://localhost:{puerto}/swagger-ui/index.html
```

Cada endpoint está documentado con `@Tag`, `@Operation` y `@ApiResponse`, incluyendo descripciones de parámetros, códigos de respuesta esperados y ejemplos de uso.

> **Nota:** `identity-service` requiere que las rutas de Swagger estén permitidas en `SecurityConfig` para acceso sin autenticación.

---

### HATEOAS

Las respuestas de la API incluyen enlaces hipermedia (`_links`) que permiten navegar el sistema sin conocer las URLs de antemano. Cada DTO de respuesta extiende `RepresentationModel<>` de Spring HATEOAS.

**Ejemplo de respuesta con HATEOAS:**

```json
{
  "id": 1,
  "titulo": "Don Quijote de la Mancha",
  "autor": "Miguel de Cervantes",
  "disponible": true,
  "_links": {
    "self":        { "href": "http://localhost:8085/api/catalog/1" },
    "todos":       { "href": "http://localhost:8085/api/catalog" },
    "disponibles": { "href": "http://localhost:8085/api/catalog/disponibles" },
    "eliminar":    { "href": "http://localhost:8085/api/catalog/1" }
  }
}
```

> Los clientes Feign entre microservicios usan `@JsonIgnoreProperties(ignoreUnknown = true)` en sus DTOs para ignorar el campo `_links` sin romper la deserialización.

---

### Almacenamiento MySQL BLOB

`ingestion-service` migró de almacenamiento en disco local a MySQL usando columnas `LONGBLOB`. Los archivos PDF y EPUB se guardan directamente en la base de datos junto con su metadata.

**Patrón Strategy aplicado:**

```
StorageService (interfaz)
    ├── LocalStorageService    → guarda en disco  (legacy, sin @Primary)
    └── DatabaseStorageService → guarda en MySQL  (@Primary — activo)
```

El cambio de implementación requirió únicamente mover `@Primary` de una clase a otra — sin modificar controladores ni otros servicios.

**Migración Flyway:**

```sql
-- V2__add_datos_blob.sql
ALTER TABLE archivos_libros
ADD COLUMN datos LONGBLOB NULL;
```

> Para consultar archivos en HeidiSQL usar `LENGTH(datos)` en vez de `SELECT *` — evita congelar el cliente al cargar BLOBs grandes.

---

## Pruebas

El proyecto implementa dos tipos de pruebas automatizadas con **JUnit 5 + Mockito**, cubriendo 6 microservicios con un total de 66 tests y 0 fallos.

### Tests Unitarios (Service)

Verifican la lógica de negocio de forma completamente aislada — sin base de datos, sin HTTP, sin Config Server ni Eureka. Los repositorios y clientes Feign se reemplazan con mocks controlados por el test.

| Microservicio | Clase testeada | Tests | Cobertura |
|---|---|---|---|
| `catalog-service` | `CatalogService` | 10 | CRUD completo, ISBN duplicado, libro no encontrado |
| `elending-service` | `PrestamoService` | 10 | Reglas BÁSICO/PREMIUM, copias, duplicados, fallback |
| `identity-service` | `UserService` | 13 | Registro, login, reset y cambio de contraseña |

Casos críticos cubiertos en E-Lending:

```java
// Usuario BÁSICO bloqueado al alcanzar límite de 2 préstamos
void crearPrestamo_falla_usuario_BASICO_ya_tiene_2_prestamos_activos()

// Usuario PREMIUM bloqueado al alcanzar límite de 5 préstamos
void crearPrestamo_falla_usuario_PREMIUM_ya_tiene_5_prestamos_activos()

// Fallback a plan BÁSICO cuando Subscription Service no responde
void crearPrestamo_aplica_plan_BASICO_por_defecto_si_falla_subscription()
```

### Tests REST con MockMvc (Controller)

Verifican el comportamiento HTTP del Controller — códigos de respuesta (200, 201, 400, 404...), estructura del JSON, presencia de links HATEOAS y funcionamiento de validaciones Bean Validation. No levantan un servidor Tomcat real: MockMvc simula el ciclo HTTP en memoria, lo que hace estos tests extremadamente rápidos (2-4 segundos por clase) sin depender de ningún servicio externo.

| Microservicio | Clase testeada | Tests | Cobertura |
|---|---|---|---|
| `license-service` | `LicenseController` | 12 | CRUD, prestar/devolver, validaciones, HATEOAS, 404/422 |
| `subscription-service` | `SuscripcionController` | 11 | Planes BÁSICO/PREMIUM, token JWT, 400/404, HATEOAS |
| `notification-service` | `NotificacionController` | 10 | Crear, listar, marcar leída, 400/404, HATEOAS |

### Resumen total

| Tipo | Microservicios | Tests |
|---|---|---|
| Unitarios (Service) | 3 | 33 |
| REST MockMvc (Controller) | 3 | 33 |
| **Total** | **6** | **66 — 0 fallos** |

### Ejecutar tests

```bash
# Un microservicio a la vez
cd catalog-service
.\mvnw test

# El resultado indica: Tests run: N, Failures: 0, Errors: 0
# BUILD SUCCESS = todos los tests pasaron
```

Desde VS Code, el panel de Testing (icono de matraz en la barra lateral) muestra todos los tests organizados por clase y permite ejecutarlos individualmente con un click, sin necesitar la terminal.

---


## Reglas de Negocio

### Planes de Suscripción

| Plan | Préstamos simultáneos | Duración del préstamo |
|---|---|---|
| **BÁSICO** | 2 | 7 días |
| **PREMIUM** | 5 | 14 días |

### Flujo de creación de préstamo

```
1. Verificar plan del usuario (Subscription Service via Feign)
2. Verificar límite de préstamos activos según plan
3. Verificar que el usuario no tenga ya ese libro en préstamo
4. Verificar copias disponibles (License Service via Feign)
5. Descontar 1 copia (License Service via Feign)
6. Crear registro de préstamo en BD
7. Notificar al usuario (Notification Service via Feign — silencioso si falla)
```

---

## Configuración y arranque

### Prerrequisitos

- Java 17
- Maven (o usar `mvnw` incluido)
- MySQL 8.4 (Laragon recomendado en Windows)
- Laragon o MySQL Server activo

### Orden de arranque

```
1. microservice-config   :8888  ← primero siempre
2. microservice-eureka   :8761
3. identity-services     :8084
4. microservice-gateway  :8080
5. Resto de microservicios (cualquier orden)
```

### Variables de entorno requeridas

Cada microservicio se conecta al Config Server en `http://localhost:8888`. Las configuraciones de base de datos, JWT secret y puertos se gestionan centralmente desde el repositorio de configuración.

### Problema común: conflicto de puerto JMX en VS Code

Si al arrancar múltiples microservicios aparece `Port already in use: 49734`, agregar en `launch.json` de cada microservicio:

```json
"vmArgs": "-Dcom.sun.management.jmxremote.port=0 ..."
```

El `0` asigna un puerto dinámico libre, evitando conflictos entre instancias JVM.

---

## Estructura del proyecto

```
SmallBooksRepository/
├── pom.xml                          ← pom raíz (dependencias compartidas)
├── microservice-config/
├── microservice-eureka/
├── microservice-gateway/
├── identity-services/
│   └── src/
│       ├── main/java/com/silvio/identity/
│       │   ├── controller/          ← AuthController
│       │   ├── service/             ← UserService
│       │   ├── security/            ← JwtUtil, JwtAuthenticationFilter
│       │   ├── config/              ← SecurityConfig, SwaggerConfig
│       │   └── repository/
│       └── test/java/com/silvio/identity/
│           └── service/             ← UserServiceTest (13 tests)
├── catalog-service/
│   └── src/
│       ├── main/java/com/silvio/catalog/
│       └── test/java/com/silvio/catalog/
│           └── service/             ← CatalogServiceTest (10 tests)
├── elending-service/
│   └── src/
│       ├── main/java/com/silvio/elending/
│       └── test/java/com/silvio/elending/
│           └── service/             ← PrestamoServiceTest (10 tests)
├── license-service/
├── ingestion-service/               ← contiene DatabaseStorageService
├── content-service/
├── notification-service/
├── subscription-service/
├── search-service/
└── analytics-service/
```

---

## Autenticación

El sistema usa **JWT (JSON Web Tokens)** con dos tipos de token:

- **Access Token** — corta duración (30 min en desarrollo), requerido en `Authorization: Bearer {token}`
- **Refresh Token** — larga duración, usado para renovar el access token sin re-login

Los microservicios que requieren identificación del usuario extraen el `username` del payload del JWT sin necesidad de consultar `identity-service` en cada petición.

---

## Swagger UI por microservicio

| Microservicio | URL Swagger |
|---|---|
| Identity | http://localhost:8084/swagger-ui/index.html |
| Catalog | http://localhost:8085/swagger-ui/index.html |
| License | http://localhost:8086/swagger-ui/index.html |
| E-Lending | http://localhost:8087/swagger-ui/index.html |
| Notification | http://localhost:8088/swagger-ui/index.html |
| Subscription | http://localhost:8089/swagger-ui/index.html |
| Search | http://localhost:8090/swagger-ui/index.html |
| Analytics | http://localhost:8091/swagger-ui/index.html |
| Ingestion | http://localhost:8092/swagger-ui/index.html |
| Content Delivery | http://localhost:8093/swagger-ui/index.html |

