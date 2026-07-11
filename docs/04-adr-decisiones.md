# Decisiones de Arquitectura (ADRs) — SmallBooks

> **Registro de Decisiones de Arquitectura (Architecture Decision Records)**  
> Cada ADR documenta una decisión significativa, su contexto, la alternativa elegida y las consecuencias.

---

## ADR-001: Arquitectura de Microservicios vs Monolito

### Estado
**Aceptado**

### Contexto
SmallBooks es una plataforma académica desarrollada por un equipo pequeño (3 personas) en Duoc UC. El sistema debe gestionar: catálogo de libros, autenticación de usuarios, suscripciones, préstamos digitales, notificaciones, carga de archivos y estadísticas.

Las alternativas consideradas fueron:
1. **Monolito Spring Boot** — aplicación única con todos los módulos
2. **Microservicios con Spring Cloud** — servicios independientes por dominio
3. **Módulos Maven con separación lógica** — un solo deploy con capas

### Decisión
Se eligió **Arquitectura de Microservicios** con **13 servicios independientes**.

Justificación:
- **Propósito académico**: El proyecto busca demostrar competencias en arquitectura distribuida, service discovery, API Gateway y comunicación entre servicios — conceptos que un monolito no permite explorar.
- **Separación por dominio**: Cada microservicio corresponde a un Bounded Context claro (Identity, Catalog, Lending, etc.), facilitando la evolución independiente.
- **Stack tecnológico requerido**: La asignatura exige el uso de Eureka, Gateway, Config Server y Feign, que son tecnologías nativas para microservicios.
- **Escalabilidad selectiva**: Servicios con mayor carga (Catalog, Search) podrían escalarse independientemente.

### Consecuencias
**Positivas**:
- ✅ Separación clara de responsabilidades por dominio
- ✅ Cada servicio tiene su propia base de datos (aislamiento)
- ✅ Posibilidad de escalar servicios individualmente
- ✅ Despliegue independiente por servicio
- ✅ Cumplimiento de requisitos académicos de arquitectura distribuida

**Negativas**:
- ❌ Mayor complejidad operativa (13 servicios que arrancar en orden)
- ❌ Latencia de red entre servicios (Feign calls)
- ❌ Consistencia eventual en operaciones跨 servicio (préstamo)
- ❌ Sobrecarga cognitiva para el equipo pequeño
- ❌ Duplicación de configuraciones (cada MS tiene su pom.xml)

---

## ADR-002: Spring Boot 3.3.11 con Spring Cloud 2023.0.5

### Estado
**Aceptado**

### Contexto
Se necesita un framework para construir microservicios en Java que soporte: REST APIs, seguridad JWT, service discovery, gateway reactivo y configuración centralizada.

Las alternativas consideradas:
1. **Spring Boot 3.1.x + Spring Cloud 2022.x** — versión anterior estable
2. **Spring Boot 3.3.x + Spring Cloud 2023.x** — versión más reciente
3. **Quarkus** — framework alternativo con mejor rendimiento de arranque
4. **Micronaut** — framework con compilación AOT

### Decisión
Se eligió **Spring Boot 3.3.11** con **Spring Cloud 2023.0.5** (compatible con Spring Boot 3.3.x).

Justificación:
- **Compatibilidad probada**: Spring Cloud 2023.0.x es la versión recomendada para Spring Boot 3.3.x según la matriz de compatibilidad oficial.
- **Jakarta EE**: Spring Boot 3.x migró de `javax.*` a `jakarta.*`, alineándose con los estándares modernos.
- **Java 17+**: Soporte nativo para records, text blocks, switch expressions.
- **Spring Cloud Gateway reactivo**: Mejor rendimiento que Zuul para el API Gateway.
- **Ecosistema completo**: Spring Cloud Config, Netflix Eureka, OpenFeign están disponibles y maduros.
- **Comunidad y soporte**: Documentación extensa y actualizada.

### Consecuencias
**Positivas**:
- ✅ Framework moderno con soporte LTS
- ✅ Jakarta EE 10 estándar
- ✅ Ecosistema completo para microservicios
- ✅ Buen rendimiento con WebFlux en el Gateway

**Negativas**:
- ❌ Spring Boot 3.3.x no tiene soporte LTS tan largo como 3.1.x
- ❌ Migración de `javax.*` a `jakarta.*` requiere atención en dependencias
- ❌ JaCoCo configurado en el pom raíz con versión 0.8.11 (compatible)

**Nota sobre la versión real detectada**: El `pom.xml` raíz declara Spring Boot 3.3.11 como parent, aunque el README menciona 3.2.x. La versión real en el código es **3.3.11**.

---

## ADR-003: Patrón CSR (Controller → Service → Repository) con HATEOAS

### Estado
**Aceptado**

### Contexto
Se necesita un patrón de capas consistente para todos los microservicios, que permita separar responsabilidades, facilitar el testing y exponer APIs REST descrubribles.

Las alternativas consideradas:
1. **CSR (Controller-Service-Repository)** con HATEOAS — capas estrictas
2. **Arquitectura Hexagonal (Puertos y Adaptadores)** — mayor desacoplamiento
3. **Clean Architecture** — múltiples capas con inversión de dependencias
4. **Service Layer + Anemic Domain Model** — sin capa de dominio explícita

### Decisión
Se eligió **CSR estricto (Controller → Service → Repository)** con **HATEOAS en los DTOs de respuesta**.

Justificación:
- **Simplicidad**: CSR es el patrón más simple y entendible para un equipo académico.
- **Separación por capas**: Controller (HTTP), Service (negocio), Repository (datos).
- **DTOs de entrada/salida**: `*RequestDTO` para entrada, `*ResponseDTO extends RepresentationModel` para salida. Nunca se exponen entidades JPA directamente.
- **HATEOAS**: Cada ResponseDTO extiende `RepresentationModel` y agrega enlaces `_links` para navegabilidad.
- **Consistencia**: Todos los microservicios siguen exactamente la misma estructura de paquetes.

### Consecuencias
**Positivas**:
- ✅ Estructura predecible y consistente en todos los MS
- ✅ Fácil onboarding para nuevos desarrolladores
- ✅ DTOs de entrada con validaciones Bean Validation
- ✅ HATEOAS permite descubrimiento de APIs
- ✅ Testing por capas (service test + controller test)

**Negativas**:
- ❌ Las entidades JPA contienen anotaciones de validación (`@NotBlank`, `@Size`), mezclando responsabilidades
- ❌ No hay una capa de dominio pura (el modelo es anémico)
- ❌ Los ResponseDTO extienden `RepresentationModel`, lo que acopla la capa de presentación con HATEOAS
- ❌ No se usa Builder pattern — los DTOs se construyen con setters

### Estructura de paquetes estándar

```
com.silvio.{servicio}/
├── config/
│   └── SwaggerConfig.java
├── controller/
│   └── {Nombre}Controller.java
├── dto/
│   ├── {Nombre}RequestDTO.java
│   └── {Nombre}ResponseDTO.java (extends RepresentationModel)
├── exception/
│   └── GlobalExceptionHandler.java
├── model/
│   └── {Nombre}Entity.java
├── repository/
│   └── {Nombre}Repository.java
└── service/
    └── {Nombre}Service.java
```

---

## ADR-004: JWT (JSON Web Tokens) para Autenticación Distribuida

### Estado
**Aceptado**

### Contexto
En una arquitectura de microservicios, la autenticación debe ser **stateless** y **distribuida**. Cada request que llega al Gateway debe ser autenticado sin necesidad de consultar un servicio central en cada petición.

Las alternativas consideradas:
1. **JWT (JSON Web Tokens)** — token autónomo con claims firmados
2. **Session HTTP con Redis centralizado** — estado en servidor
3. **OAuth2 + Keycloak** — servidor de autorización externo
4. **API Keys** — simple pero sin identidad de usuario

### Decisión
Se eligió **JWT con HMAC-SHA256** y dos tipos de token (access + refresh).

Justificación:
- **Stateless**: El Gateway valida el token por sí mismo usando la clave HMAC compartida — no necesita llamar a identity-service en cada request.
- **Autonomía**: El token contiene el `username` como claim, permitiendo a cualquier microservicio identificar al usuario sin consultar a identity-service.
- **Dos tipos de token**: Access token (corta duración, 30 min) + Refresh token (larga duración, 7 días) — balance entre seguridad y usabilidad.
- **Simplicidad**: No requiere infraestructura adicional (Redis, Keycloak).
- **Clave compartida**: Misma clave `Duoc.1983Duoc.1983Duoc.1983Duoc.1983` en identity-service (generación) y gateway (validación).

### Consecuencias
**Positivas**:
- ✅ Autenticación stateless y distribuida
- ✅ Sin punto único de fallo para validación de tokens
- ✅ Fácil de implementar y entender
- ✅ Refresh token reduce la frecuencia de login

**Negativas**:
- ❌ No se puede revocar un token individual antes de su expiración
- ❌ La clave HMAC es compartida y está hardcodeada en los YMLs (riesgo de seguridad)
- ❌ El JWT secret `Duoc.1983Duoc.1983Duoc.1983Duoc.1983` es débil (caracteres repetidos) y debería ser más robusto en producción
- ❌ No hay soporte para OAuth2 ni roles avanzados (scopes)
- ❌ Los microservicios que necesitan el usuario (elending, subscription) implementan `JwtExtractor` duplicado

### Mejoras recomendadas
1. Usar RSA (asymmetric) en lugar de HMAC (symmetric) para no compartir el secret
2. Mover el JWT secret a variables de entorno, no a YMLs
3. Implementar un endpoint de revocación (blacklist de tokens)
4. Centralizar `JwtExtractor` en una librería compartida

---

## ADR-005: OpenFeign para Comunicación entre Microservicios

### Estado
**Aceptado**

### Contexto
Los microservicios necesitan comunicarse entre sí para orquestar operaciones de negocio (préstamo, búsqueda, estadísticas, entrega de contenido).

Las alternativas consideradas:
1. **OpenFeign** — cliente HTTP declarativo con integración Eureka
2. **RestTemplate** — cliente HTTP síncrono tradicional
3. **WebClient** — cliente reactivo no bloqueante
4. **Mensajería asíncrona (RabbitMQ/Kafka)** — desacoplamiento total

### Decisión
Se eligió **OpenFeign** con **Eureka load balancer** para comunicación síncrona.

Justificación:
- **Integración nativa**: Feign + Eureka resuelve automáticamente la URL del servicio destino usando `lb://nombre-servicio`.
- **Declarativo**: Interfaz Java con anotaciones Spring — no hay boilerplate de HTTP.
- **Timeouts configurables**: Globales en `application.yml` (connect: 3s, read: 5s).
- **Simplicidad**: Adecuado para un proyecto académico con comunicación síncrona.
- **Balanceo de carga**: Eureka integra balanceo round-robin.

### Consecuencias
**Positivas**:
- ✅ Código mínimo para comunicación entre servicios
- ✅ Resolución automática de URLs via Eureka
- ✅ Timeouts globales configurados
- ✅ Familiar para el equipo (mismas anotaciones Spring)

**Negativas**:
- ❌ Comunicación síncrona — la latencia se acumula en cadenas de llamadas
- ❌ Sin Circuit Breaker implementado (acoplamiento fuerte)
- ❌ Sin retry policy configurada
- ❌ Sin trazabilidad distribuida (IDs de correlación)
- ❌ El contenido-service pasa el auth header completo al elending-service, lo que puede ser riesgoso

---

## ADR-006: Base de Datos por Microservicio con Flyway

### Estado
**Aceptado**

### Contexto
En una arquitectura de microservicios, cada servicio debe tener su propio almacenamiento de datos para garantizar el desacoplamiento.

Las alternativas consideradas:
1. **Base de datos compartida** — todos los servicios en la misma BD
2. **Base de datos por servicio** — cada MS con su propio schema MySQL
3. **Base de datos por servicio + eventos** — cada MS publica eventos de cambio

### Decisión
Se eligió **Base de datos por microservicio** con **Flyway** para migraciones.

Justificación:
- **Aislamiento de datos**: Cada servicio es dueño de sus datos y solo accede a su propia BD.
- **Flyway**: Migraciones automáticas al iniciar, con `baseline-on-migrate: true`.
- **Esquemas separados**: `db_catalog`, `db_lending`, `db_identity`, `db_license`, `db_subscriptions`, `db_notifications`, `db_ingestion`.
- **Servicios sin BD**: search, analytics, y content no tienen BD propia — consultan otros servicios via Feign.

### Consecuencias
**Positivas**:
- ✅ Aislamiento completo de datos entre servicios
- ✅ Flyway garantiza consistencia de esquemas
- ✅ Servicios sin BD son más livianos y rápidos de arrancar

**Negativas**:
- ❌ No hay integridad referencial entre servicios (no hay FK cruzadas)
- ❌ La consistencia entre servicios es eventual (no hay transacciones distribuidas)
- ❌ Siete bases de datos MySQL que crear y mantener
- ❌ El ingestion-service almacena archivos como LONGBLOB en MySQL, lo que puede ser ineficiente para archivos grandes

---

## Resumen de Decisiones

| ADR | Decisión | Estado |
|-----|----------|--------|
| 001 | Microservicios (13 servicios) | ✅ Aceptado |
| 002 | Spring Boot 3.3.11 + Spring Cloud 2023.0.5 | ✅ Aceptado |
| 003 | CSR + HATEOAS | ✅ Aceptado |
| 004 | JWT con HMAC (access + refresh) | ✅ Aceptado |
| 005 | OpenFeign para comunicación síncrona | ✅ Aceptado |
| 006 | BD por servicio + Flyway | ✅ Aceptado |

---

## ADRs Futuros (Pendientes)

- **ADR-007**: Migración de comunicación síncrona a asíncrona (eventos)
- **ADR-008**: Implementación de Circuit Breaker (Resilience4J)
- **ADR-009**: Centralización de JwtExtractor en librería compartida
- **ADR-010**: Migración de HMAC a RSA para JWT
- **ADR-011**: Implementación de trazabilidad distribuida (IDs de correlación)
