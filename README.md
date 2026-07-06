# SmallBooks — Plataforma de Biblioteca Digital

> Proyecto académico desarrollado en **Duoc UC** · Ingeniería Informática · DSY1103 Desarrollo FullStack 1  
> Arquitectura de microservicios con Spring Boot 3.3.11 y Spring Cloud 2023.0.5

---

## Equipo de Desarrollo

| Nombre | Rol |
|---|---|
| Silvio Gonzalves | Líder técnico / Backend |
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

## Rutas del API Gateway

Todas las peticiones entran por el puerto **:8080**. El Gateway valida el JWT y enruta al microservicio correspondiente.

| Ruta | Microservicio destino | Requiere JWT |
|---|---|---|
| `/auth/**` | identity-service :8084 | ❌ Público |
| `/api/catalog/**` | catalog-service :8085 | ✅ JwtAuthFilter |
| `/api/licenses/**` | license-service :8086 | ✅ JwtAuthFilter |
| `/api/lending/**` | elending-service :8087 | ✅ JwtAuthFilter |
| `/api/notifications/**` | notification-service :8088 | ✅ JwtAuthFilter |
| `/api/subscriptions/**` | subscription-service :8089 | ✅ JwtAuthFilter |
| `/api/search/**` | search-service :8090 | ✅ JwtAuthFilter |
| `/api/analytics/**` | analytics-service :8091 | ✅ JwtAuthFilter |
| `/api/ingestion/**` | ingestion-service :8092 | ✅ JwtAuthFilter |
| `/api/content/**` | content-service :8093 | ✅ JwtAuthFilter |

> El enrutamiento usa `lb://` (load balancer) — el Gateway consulta a Eureka dónde está cada MS en lugar de usar una IP fija.

---

## Database Requerido

```sql
CREATE DATABASE db_catalog;
CREATE DATABASE db_lending;
CREATE DATABASE db_identity;
CREATE DATABASE db_license;
CREATE DATABASE db_subscriptions;
CREATE DATABASE db_notifications;
CREATE DATABASE db_ingestion;
```

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
| **Testing** | JUnit 5 + Mockito + JaCoCo |
| **Cobertura** | JaCoCo — supera el 80% en todos los MS |
| **Contenedores** | Docker + Docker Compose |
| **IDE** | VSCode + Spring Boot Dashboard |

---

## Ejecución Local (VSCode)

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

---

## Ejecución con Docker

### Prerrequisitos

- Docker Desktop instalado y corriendo
- MySQL 8.4 activo en la máquina local (Laragon)
- Java 17 + Maven instalados

### Pasos

```bash
# 1. Compilar todos los microservicios (genera los .jar)
mvn clean package -DskipTests

# 2. Construir imágenes y levantar el ecosistema completo
docker-compose up --build

# 3. Verificar que todos los contenedores están corriendo
docker ps

# 4. Confirmar registro en Eureka
# Abrir en el browser: http://localhost:8761
```

### Comportamiento del arranque

El `docker-compose.yml` está configurado con `healthcheck` y `depends_on: condition: service_healthy` para garantizar el orden correcto:

```
Config Server (healthy)
        ↓
Eureka Server (healthy)
        ↓
Gateway (healthy)
        ↓
MS de negocio (arrancan en paralelo)
```

Los MS de negocio usan la variable de entorno `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka:8761/eureka/` para registrarse correctamente dentro de la red Docker.

---

## Pruebas y Cobertura

El proyecto implementa dos tipos de pruebas automatizadas con **JUnit 5 + Mockito**, cubriendo 8 microservicios con un total de **66 tests y 0 fallos**.

### Ejecutar tests y generar reporte de cobertura

```bash
# Desde la raíz del proyecto
mvn clean test
```

JaCoCo genera automáticamente los reportes de cobertura en `target/site/jacoco/index.html` de cada MS. Los reportes ya generados están disponibles en la carpeta `reports/jacoco/` del repositorio — abrirlos directamente en el browser sin necesidad de compilar.

### Tests Unitarios (Service)

Verifican la lógica de negocio de forma completamente aislada — sin base de datos, sin HTTP, sin Config Server ni Eureka.

| Microservicio | Clase testeada | Tests | Cobertura |
|---|---|---|---|
| `catalog-service` | `CatalogService` | 10 | CRUD completo, ISBN duplicado, libro no encontrado |
| `elending-service` | `PrestamoService` | 10 | Reglas BÁSICO/PREMIUM, copias, duplicados, compensación |
| `identity-service` | `UserService` | 13 | Registro, login, reset y cambio de contraseña |

### Tests REST con MockMvc (Controller)

Verifican el comportamiento HTTP del Controller — códigos de respuesta, estructura JSON, HATEOAS y Bean Validation.

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
   → Si falla: compensación automática devuelve la copia a License Service
7. Notificar al usuario (Notification Service via Feign — silencioso si falla)
```

---

## Nuevas integraciones

### Swagger / OpenAPI

Todos los microservicios con endpoints REST cuentan con documentación interactiva generada automáticamente.

**Acceso a Swagger UI** (con el microservicio corriendo):

```
http://localhost:{puerto}/swagger-ui/index.html
```

### HATEOAS

Las respuestas de la API incluyen enlaces hipermedia (`_links`) que permiten navegar el sistema sin conocer las URLs de antemano.

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

### Almacenamiento MySQL BLOB

`ingestion-service` migró de almacenamiento en disco local a MySQL usando columnas `LONGBLOB`.

```
StorageService (interfaz)
    ├── LocalStorageService    → guarda en disco  (legacy, sin @Primary)
    └── DatabaseStorageService → guarda en MySQL  (@Primary — activo)
```

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

---

## Autenticación

El sistema usa **JWT (JSON Web Tokens)** con dos tipos de token:

- **Access Token** — corta duración (30 min en desarrollo), requerido en `Authorization: Bearer {token}`
- **Refresh Token** — larga duración, usado para renovar el access token sin re-login

Los microservicios que requieren identificación del usuario extraen el `username` del payload del JWT sin necesidad de consultar `identity-service` en cada petición.

---

## Estructura del proyecto

```
SmallBooksRepository/
├── pom.xml                          ← pom raíz (dependencias compartidas + JaCoCo)
├── docker-compose.yml               ← ecosistema completo en Docker
├── reports/
│   └── jacoco/                      ← reportes de cobertura generados
│       ├── catalog-service/index.html
│       ├── elending-service/index.html
│       └── ...
├── microservice-config/
│   └── src/main/resources/
│       └── configurations/          ← YAMLs de todos los MS
├── microservice-eureka/
├── microservice-gateway/
├── identity-services/
├── catalog-service/
├── license-service/
├── elending-service/
├── notification-service/
├── subscription-service/
├── search-service/
├── analytics-service/
├── ingestion-service/
└── content-service/
```
