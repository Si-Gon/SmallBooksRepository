# SmallBooks — Plataforma de Biblioteca Digital

> Proyecto académico desarrollado en **Duoc UC** · Ingeniería Informática · DSY1103 Desarrollo FullStack 1  
> Arquitectura de microservicios con Spring Boot 3.3.11 y Spring Cloud 2023.0.5

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.11-green)
![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2023.0.5-green)
![Tests](https://img.shields.io/badge/Tests-859-blue)
![Coverage](https://img.shields.io/badge/Coverage-90%25-brightgreen)
![CI/CD](https://img.shields.io/badge/CI%2FCD-GitHub%20Actions-black)

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
                        │  JWT + Rate     │
                        │  Limiting +     │
                        │  X-User-Id      │
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
   └─────────────┘      └───────────────┘     └───────┬───────┘
          │                      │                    ↑
   ┌──────▼──────┐      ┌───────▼───────┐     RabbitMQ (async)
   │  Ingestion  │      │    Search     │
   │   :8092     │      │    :8090      │
   └─────────────┘      └───────────────┘
          │
   ┌──────▼──────┐      ┌───────────────┐      ┌─────────────┐
   │   Content   │      │   Analytics   │      │   Zipkin    │
   │  Delivery   │      │    :8091      │      │   :9411     │
   │   :8093     │      └───────────────┘      └─────────────┘
   └─────────────┘
```

---

## Microservicios

| Microservicio | Puerto | Descripción |
|---|---|---|
| `microservice-config` | 8888 | Servidor de configuración centralizada |
| `microservice-eureka` | 8761 | Registro y descubrimiento de servicios |
| `microservice-gateway` | 8080 | API Gateway — JWT, Rate Limiting, Identity Propagation |
| `identity-services` | 8084 | Autenticación JWT, registro y gestión de usuarios |
| `catalog-service` | 8085 | Catálogo de libros (CRUD completo + HATEOAS) |
| `license-service` | 8086 | Control de copias disponibles con Optimistic Lock |
| `elending-service` | 8087 | Préstamos digitales con reglas BÁSICO/PREMIUM |
| `notification-service` | 8088 | Notificaciones asíncronas via RabbitMQ con idempotencia |
| `subscription-service` | 8089 | Planes de suscripción BÁSICO y PREMIUM |
| `search-service` | 8090 | Búsqueda y descubrimiento de libros |
| `analytics-service` | 8091 | Métricas y estadísticas de uso |
| `ingestion-service` | 8092 | Carga de archivos PDF/EPUB → MySQL BLOB |
| `content-service` | 8093 | Entrega de archivos a usuarios con préstamo activo |

---

## Stack Tecnológico

| Categoría | Tecnología |
|---|---|
| **Framework** | Spring Boot 3.3.11 |
| **Cloud** | Spring Cloud 2023.0.5 |
| **Base de datos** | MySQL 8.4 |
| **Migraciones** | Flyway |
| **ORM** | Spring Data JPA + Hibernate 6.5 |
| **Seguridad** | Spring Security + JWT |
| **Comunicación síncrona** | OpenFeign + Eureka Load Balancer |
| **Comunicación asíncrona** | RabbitMQ + Spring AMQP (DLQ + retry) |
| **Resiliencia** | Resilience4j (Circuit Breaker + fallback) |
| **Control de tráfico** | Bucket4j in-memory (Rate Limiting en Gateway) |
| **Trazabilidad** | Micrometer + TraceID + Zipkin |
| **Bloqueo distribuido** | ShedLock 5.13.0 (scheduler multi-instancia) |
| **Documentación API** | SpringDoc OpenAPI 2.5.0 (Swagger UI) |
| **Hipermedia** | Spring HATEOAS |
| **Build** | Maven multi-módulo |
| **Utilidades** | Lombok, SLF4J, Bean Validation |
| **Testing** | JUnit 5 + Mockito + JaCoCo |
| **Cobertura** | 90.17% — 859 tests, 0 fallos |
| **Contenedores** | Docker + Docker Compose |
| **CI/CD** | GitHub Actions (path filtering por módulo) |

---

## Patrones y Funcionalidades Implementadas

### Resiliencia
- **Circuit Breaker (Resilience4j)** — en los 4 Feign clients de elending-service (Identity, Catalog, Subscription, License) con fallback methods que devuelven respuestas degradadas
- **Optimistic Locking (JPA @Version)** — en License y Préstamo para control de concurrencia
- **Retry pattern** — en LicenseService con hasta 3 reintentos ante conflictos de concurrencia
- **Dead Letter Queue (RabbitMQ)** — mensajes fallidos se envían a cola DLQ con reintento automático

### Mensajería Asíncrona
- **RabbitMQ** — elending-service publica eventos, notification-service los consume de forma asíncrona
- **Idempotencia** — notification-service ignora mensajes duplicados usando clave de idempotencia por hash
- **Trazabilidad RabbitMQ** — TraceID propagado en headers de mensajes async

### Seguridad y Control de Acceso
- **JWT en Gateway** — validación centralizada antes de enrutar a los microservicios
- **Rate Limiting (Bucket4j)** — 1000 req/min global + 50 req/min por IP en el Gateway
- **Identity Propagation** — Gateway extrae claims JWT y propaga `X-User-Id` y `X-User-Roles` como headers a todos los microservicios
- **FeignRequestInterceptor** — propaga automáticamente el JWT en todas las llamadas entre servicios

### Observabilidad
- **Micrometer TraceID** — trazabilidad distribuida en los 13 servicios con @Observed en métodos críticos
- **Zipkin** — visualización de trazas distribuidas en http://localhost:9411
- **ShedLock** — evita ejecución paralela del scheduler en múltiples instancias

### CI/CD
- **GitHub Actions** — workflow con path filtering por módulo (solo corre tests del módulo afectado)
- **Branch protection** — documentado en `docs/07-proteccion-ramas-cicd.md`

---

## Rutas del API Gateway

Todas las peticiones entran por el puerto **:8080**.

| Ruta | Microservicio destino | Requiere JWT |
|---|---|---|
| `/auth/**` | identity-service :8084 | No — Público |
| `/api/users/**` | identity-service :8084 | Si — JwtAuthFilter |
| `/api/catalog/**` | catalog-service :8085 | Si — JwtAuthFilter |
| `/api/licenses/**` | license-service :8086 | Si — JwtAuthFilter |
| `/api/lending/**` | elending-service :8087 | Si — JwtAuthFilter |
| `/api/notifications/**` | notification-service :8088 | Si — JwtAuthFilter |
| `/api/subscriptions/**` | subscription-service :8089 | Si — JwtAuthFilter |
| `/api/search/**` | search-service :8090 | Si — JwtAuthFilter |
| `/api/analytics/**` | analytics-service :8091 | Si — JwtAuthFilter |
| `/api/ingestion/**` | ingestion-service :8092 | Si — JwtAuthFilter |
| `/api/content/**` | content-service :8093 | Si — JwtAuthFilter |

> El Gateway propaga `X-User-Id` y `X-User-Roles` como headers a todos los microservicios tras validar el JWT.

---

## Reglas de Negocio

### Planes de Suscripción

| Plan | Préstamos simultáneos | Duración del préstamo |
|---|---|---|
| **BÁSICO** | 2 | 7 días |
| **PREMIUM** | 5 | 14 días |

### Flujo de creación de préstamo

```
1. Verificar plan del usuario (Subscription Service via Feign + Circuit Breaker)
2. Verificar límite de préstamos activos según plan
3. Verificar que el usuario no tenga ya ese libro en préstamo
4. Verificar copias disponibles (License Service via Feign + Circuit Breaker)
5. Descontar 1 copia con Optimistic Lock (hasta 3 reintentos)
6. Crear registro de préstamo en BD
   → Si falla: compensación automática devuelve la copia a License Service
7. Publicar evento en RabbitMQ → notification-service notifica al usuario de forma asíncrona
```

---

## Pruebas y Cobertura

```bash
# Correr todos los tests desde la raíz
mvn clean test

# Correr tests de un módulo específico
mvn test -pl elending-service
```

| Microservicio | Tests | Cobertura |
|---|---|---|
| `elending-service` | 248 | 97.82% |
| `catalog-service` | 116 | 98.47% |
| `notification-service` | 112 | 95.61% |
| `identity-services` | 58 | 69.57% |
| `microservice-gateway` | 55 | 97.04% |
| `license-service` | 54 | 90.31% |
| `subscription-service` | 48 | 97.71% |
| `search-service` | 45 | 96.39% |
| `ingestion-service` | 39 | 72.99% |
| `analytics-service` | 38 | 94.05% |
| `content-service` | 34 | 93.59% |
| `microservice-config` | 6 | 40.00% |
| `microservice-eureka` | 6 | 40.00% |
| **Total** | **859** | **90.17%** |

---

## Ejecución Local

### Prerrequisitos

- Java 17
- Maven 3.9+
- MySQL 8.4 activo (Laragon recomendado en Windows)
- Docker Desktop (para ejecución con contenedores)

### Bases de datos requeridas

```sql
CREATE DATABASE db_catalog;
CREATE DATABASE db_lending;
CREATE DATABASE db_identity;
CREATE DATABASE db_license;
CREATE DATABASE db_subscriptions;
CREATE DATABASE db_notifications;
CREATE DATABASE db_ingestion;
```

### Orden de arranque manual

```
1. microservice-config   :8888  ← primero siempre
2. microservice-eureka   :8761
3. identity-services     :8084
4. microservice-gateway  :8080
5. Resto de microservicios (cualquier orden)
```

---

## Ejecución con Docker

```bash
# 1. Compilar todos los microservicios
mvn clean package -DskipTests

# 2. Levantar el ecosistema completo
docker-compose up --build

# 3. Verificar contenedores
docker ps

# 4. Verificar registro en Eureka
# http://localhost:8761

# 5. Ver trazabilidad distribuida en Zipkin
# http://localhost:9411
```

### Orden de arranque en Docker

```
Config Server (healthy)
        ↓
RabbitMQ (healthy)
        ↓
Eureka Server (healthy)
        ↓
Gateway (healthy)
        ↓
MS de negocio (arrancan en paralelo)
        ↓
Zipkin (disponible en :9411)
```

---

## Swagger UI por microservicio

| Microservicio | URL |
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

## Estructura del proyecto

```
SmallBooksRepository/
├── pom.xml                          ← pom raíz (dependencias compartidas + JaCoCo)
├── docker-compose.yml               ← ecosistema completo en Docker + RabbitMQ + Zipkin
├── .github/
│   └── workflows/
│       └── ci-sigon.yml             ← CI/CD GitHub Actions con path filtering
├── docs/
│   ├── 01-arquitectura-general.md
│   ├── 02-catalogo-microservicios.md
│   ├── 03-mapa-comunicaciones.md
│   ├── 04-adr-decisiones.md
│   ├── 05-guia-estilo.md
│   ├── 06-modelo-datos-global.md
│   └── 07-proteccion-ramas-cicd.md
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

---

## Autenticación

El sistema usa **JWT (JSON Web Tokens)**:

- **Access Token** — 30 min en desarrollo, requerido en `Authorization: Bearer {token}`
- **Refresh Token** — larga duración para renovar sin re-login
- El Gateway valida el JWT y propaga `X-User-Id` y `X-User-Roles` como headers
- Los microservicios confían en los headers del Gateway sin re-validar el token

---

## Notas de compatibilidad

### Lombok y versiones de Java

El proyecto usa **Lombok 1.18.38** — compatible con Java 17, 21 y 25.

```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.38</version>
    <optional>true</optional>
</dependency>
```

> Versiones anteriores de Lombok (1.18.30 y menores) pueden fallar al compilar con Java 21+ debido a cambios en las APIs internas del compilador.