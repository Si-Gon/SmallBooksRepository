# SmallBooks — Plataforma de Biblioteca Digital Online

Sistema de biblioteca digital construido con arquitectura de microservicios usando Spring Boot 3.x, Spring Cloud y MySQL. Permite gestionar catálogos de libros, préstamos digitales, suscripciones, notificaciones y entrega de archivos PDF/EPUB.

\---

## Equipo de Desarrollo

|Nombre|Rol|
|-|-|
|Silvio Gonzalves|Líder técnico / Backend|
|Oscar Garrido|Backend|
|Yeannette Vera|Frontend|
|Juan Ortega|QA|



**Asignatura:** DSY1103 — Desarrollo FullStack 1  
**Institución:** Duoc UC — Sede Recoleta  
**Año:** 2026

\---

## Arquitectura del Sistema

El sistema está compuesto por **13 microservicios independientes**, cada uno con su propia base de datos y responsabilidad única.

|Servicio|Puerto|Base de datos|Descripción|
|-|-|-|-|
|Config Server|8888|—|Configuración centralizada para todos los MS|
|Eureka Server|8761|—|Registro y descubrimiento de servicios|
|API Gateway|8080|—|Punto de entrada único. Valida JWT|
|Identity Service|8084|db\_identity|Autenticación y gestión de usuarios|
|Catalog Service|8085|db\_catalog|Catálogo de libros disponibles|
|License Service|8086|db\_license|Control de copias por libro|
|E-Lending Service|8087|db\_lending|Préstamos digitales con vencimiento automático|
|Notification Service|8088|db\_notifications|Notificaciones a usuarios|
|Subscription Service|8089|db\_subscriptions|Planes BASICO y PREMIUM|
|Search Service|8090|—|Búsqueda de libros (sin BD propia)|
|Analytics Service|8091|—|Estadísticas del sistema (sin BD propia)|
|Ingestion Service|8092|db\_ingestion|Subida de archivos PDF/EPUB|
|Content Delivery|8093|—|Entrega de archivos verificando préstamo activo|

\---

## Requisitos Previos

* Java 17
* Maven 3.8+
* Laragon (MySQL 8.4)
* VSCode o IntelliJ IDEA
* Postman (para pruebas)

\---

## Configuración Inicial

### 1\. Crear las bases de datos en MySQL

```sql
CREATE DATABASE db\\\_identity;
CREATE DATABASE db\\\_catalog;
CREATE DATABASE db\\\_license;
CREATE DATABASE db\\\_lending;
CREATE DATABASE db\\\_notifications;
CREATE DATABASE db\\\_subscriptions;
CREATE DATABASE db\\\_ingestion;
```

> Las migraciones de tablas e inserción de datos iniciales las ejecuta \\\*\\\*Flyway automáticamente\\\*\\\* al arrancar cada servicio.

### 2\. Crear carpeta para archivos

```
C:\\\\smallbooks\\\\archivos\\\\
```

Esta carpeta es donde Ingestion Service guarda los archivos PDF/EPUB subidos.

### 3\. Verificar configuración JWT

El secret JWT está definido en `microservice-config/src/main/resources/configurations/identity-service.yml`:

```yaml
jwt:
  secret: Duoc.1983Duoc.1983Duoc.1983Duoc.1983
  access-token-expiration: 900000    # 15 minutos
  refresh-token-expiration: 604800000 # 7 días
```

\---

## Orden de Arranque

Los servicios **deben arrancarse en este orden**. Cada uno lee su configuración del Config Server al iniciar.

```
1. microservice-config    → http://localhost:8888
2. microservice-eureka    → http://localhost:8761
3. identity-service       → http://localhost:8084
4. microservice-gateway   → http://localhost:8080
5. catalog-service        → http://localhost:8085
6. license-service        → http://localhost:8086
7. elending-service       → http://localhost:8087
8. notification-service   → http://localhost:8088
9. subscription-service   → http://localhost:8089
10. search-service        → http://localhost:8090
11. analytics-service     → http://localhost:8091
12. ingestion-service     → http://localhost:8092
13. content-service       → http://localhost:8093
```

Para verificar que todos están registrados: `http://localhost:8761`

\---

## Autenticación

Todos los endpoints excepto `/auth/\\\*\\\*` requieren token JWT.

```http
POST http://localhost:8080/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
```

Copia el `accessToken` de la respuesta y úsalo en Postman:  
`Authorization: Bearer <accessToken>`

**Usuarios por defecto:**

|Usuario|Contraseña|Roles|
|-|-|-|
|admin|admin123|ROLE\_ADMIN, ROLE\_USER|
|user1|user123|ROLE\_USER|

\---

## Endpoints Principales

Todos los endpoints se acceden a través del Gateway en `http://localhost:8080`.

### Catálogo

```
GET    /api/catalog                        → Listar todos los libros
GET    /api/catalog/buscar?titulo=quijote  → Buscar por título
POST   /api/catalog                        → Agregar libro
```

### Préstamos

```
POST   /api/lending/prestamos              → Crear préstamo
GET    /api/lending/prestamos/activos      → Mis préstamos activos
GET    /api/lending/prestamos/historial    → Mi historial
```

### Suscripciones

```
POST   /api/subscriptions                  → Crear suscripción
GET    /api/subscriptions/mi-plan          → Ver mi plan activo
```

### Búsqueda

```
GET    /api/search/disponibles             → Libros disponibles para prestar
GET    /api/search/buscar?autor=tolkien    → Buscar por autor
```

### Archivos

```
POST   /api/ingestion/upload/{libroId}    → Subir PDF/EPUB (form-data)
GET    /api/content/{libroId}             → Descargar libro (requiere préstamo activo)
```

### Estadísticas

```
GET    /api/analytics/estadisticas        → Métricas globales del sistema
```

\---

## Flujo Principal del Sistema

```
1. Login → obtener JWT
2. Crear suscripción PREMIUM o BASICO
3. Buscar libro disponible en /api/search/disponibles
4. Crear préstamo → sistema verifica copias, descuenta licencia y notifica
5. Subir archivo PDF del libro (admin) → /api/ingestion/upload/{id}
6. Descargar libro → /api/content/{id} (verifica préstamo activo)
7. Ver estadísticas → /api/analytics/estadisticas
```

\---

## Stack Tecnológico

|Tecnología|Uso|
|-|-|
|Spring Boot 3.3.11|Framework base de cada microservicio|
|Spring Cloud 2023.0.5|Config Server, Eureka, Gateway, Feign|
|Spring Security + JWT|Autenticación stateless|
|Spring Data JPA + Hibernate|Persistencia de datos|
|Flyway|Migraciones de base de datos|
|MySQL 8.4|Base de datos relacional|
|Lombok|Reducción de código boilerplate|
|Bean Validation (JSR 380)|Validación de entrada|
|SLF4J|Logs estructurados con trazabilidad|
|Postman|Pruebas de integración REST|

\---

## Reglas de Negocio Destacadas

* **Planes de suscripción:** BASICO (2 préstamos, 7 días) / PREMIUM (5 préstamos, 14 días)
* **Control de copias:** cada libro tiene un número limitado de copias prestables simultáneamente gestionado por License Service
* **Vencimiento automático:** scheduler ejecuta cada hora cerrando préstamos vencidos y devolviendo copias
* **Notificaciones automáticas:** al crear préstamo, 2 días antes de vencer y al vencer
* **Historial permanente:** los préstamos vencidos no se eliminan — quedan como historial clínico para Analytics
* **Entrega segura:** Content Delivery verifica préstamo activo antes de entregar el archivo

\---

## 📁 Estructura del Proyecto

```
SmallBooks/
├── microservice-config/       # Config Server — configuración centralizada
├── microservice-eureka/       # Eureka Server — registro de servicios
├── microservice-gateway/      # API Gateway — entrada única + JWT
├── identity-service/          # Autenticación y usuarios
├── catalog-service/           # Catálogo de libros
├── license-service/           # Control de copias
├── elending-service/          # Préstamos digitales
├── notification-service/      # Notificaciones
├── subscription-service/      # Planes de suscripción
├── search-service/            # Búsqueda
├── analytics-service/         # Estadísticas
├── ingestion-service/         # Subida de archivos
└── content-service/           # Entrega de archivos
```

