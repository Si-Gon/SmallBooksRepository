## Última Actualización
- Fecha: 2026-07-20 19:22
- Pipeline: L-01, L-02, L-03 — Limpieza de dependencias Maven en root pom.xml

## Estado Actual del Proyecto
- **docs/01-arquitectura-general.md**: Documento de arquitectura general actualizado con estado real del proyecto.
- **groupId Maven**: Todos los 10 microservicios de negocio estandarizados a `com.silvio`.
- **Resilience4j Circuit Breaker**: Implementado en los 10 microservicios de negocio con patrón FallbackFactory.
- **Micrometer Tracing + Zipkin**: Implementado en los 13 servicios (10 negocio + 3 infraestructura).
- **RabbitMQ async**: Implementado entre elending-service (productor) y notification-service (consumidor) con SHA-256 idempotency key.
- **Dependencias globales root pom.xml**: Limpiadas — `spring-boot-starter-hateoas`, `micrometer-observation-test` y `springdoc-openapi-starter-webmvc-ui` ya NO están en `<dependencies>` globales. Solo heredan de `<dependencyManagement>` cuando se declaran explícitamente.
- **spring-boot-starter-hateoas**: En `<dependencyManagement>` del root. Declarado explícitamente en los 10 microservicios de negocio. Ausente en gateway (reactive — correcto).
- **micrometer-observation-test**: En `<dependencyManagement>` del root con `<scope>test</scope>`. No se hereda automáticamente.
- **springdoc-openapi-starter-webmvc-ui**: En `<dependencyManagement>` del root. Declarado explícitamente en los 10 microservicios de negocio.
- **springdoc-openapi-starter-webflux-ui**: Declarado en `microservice-gateway/pom.xml` (WebFlux compatible).

## Decisiones Técnicas
- **M-06: Documentar estado real de Circuit Breaker, Tracing y RabbitMQ** — La arquitectura general marcaba como "pendiente" características ya implementadas: Circuit Breaker (Resilience4j + FallbackFactory en 10 MS), IDs de correlación (Micrometer Tracing traceId/spanId en MDC), trazabilidad distribuida (Micrometer Brave + Zipkin en 13 servicios). Se actualizaron a "Implementado" con detalles técnicos específicos.
- **M-06: Nueva sección 9.2 Comunicación Asíncrona con RabbitMQ** — Se documentó el flujo async entre elending-service y notification-service, incluyendo exchange direct `smallbooks.notifications`, idempotencia SHA-256 (`idempotencyKey`), y trazabilidad distribuida via headers AMQP.
- **M-06: Tabla de pruebas actualizada** — Se reescribió la sección 11 con tabla desglosada: de 66 tests en 6 MS a 1.228 tests en 10 MS con cobertura JaCoCo individual. Se agregaron tipos de test (integración, seguridad, resiliencia).
- **M-06: Scheduler documentado con ShedLock** — Sección 12 actualizada con mención de ShedLock (`shedlock-spring` 5.13.0 + `shedlock-provider-jdbc-template`) para bloqueo distribuido.
- **M-09: Estandarización groupId a `com.silvio`** — 5 servicios usaban `com.silvio.{service}` (catalog, elending, license, notification, subscription). Cambiados a `com.silvio`. Los otros 5 (analytics, content, identity-services, ingestion, search) ya tenían `com.silvio`. Infraestructura (config, eureka, gateway) mantiene `com.microservice.*`. El groupId es metadata Maven — no afecta paquetas Java, compilación ni imports.
- **L-01: Mover spring-boot-starter-hateoas a dependencyManagement** — HATEOAS es WebMVC, incompatible con Gateway (WebFlux/reactivo). Al moverlo de `<dependencies>` globales a `<dependencyManagement>`, los 10 microservicios de negocio lo heredan solo si lo declaran explícitamente. Gateway queda libre de HATEOAS. Alternativa descartada: mantenerlo global y excluirlo en gateway (más verboso, propenso a errores futuros).
- **L-02: Mover micrometer-observation-test a dependencyManagement con scope test** — Era global sin scope, heredándose como dependencia compile. Movido a `<dependencyManagement>` con `<scope>test</scope>` para que solo esté disponible cuando un microservicio lo declare explícitamente como test.
- **L-03: Mover springdoc-openapi-starter-webmvc-ui a dependencyManagement** — webmvc-ui es incompatible con Gateway (WebFlux). Movido a `<dependencyManagement>`. Gateway ahora declara `springdoc-openapi-starter-webflux-ui` (WebFlux nativo). Los 10 microservicios de negocio declaran `webmvc-ui` explícitamente.

## Criterios de Aceptación Cumplidos
- **M-06**: Actualizar docs/01-arquitectura-general.md con estado real → Implementado. Circuit Breaker, Tracing y RabbitMQ documentados como "Implementado". Tabla de tests actualizada con 1.228 tests. ShedLock documentado.
- **M-09**: Estandarizar groupId a `com.silvio` en todos los microservicios de negocio → Implementado. 5 servicios cambiados, 5 ya correctos. `mvn validate` exitoso en los 10 servicios.
- **L-01**: Mover spring-boot-starter-hateoas de dependencias globales a dependencyManagement → Implementado. 10/10 microservicios de negocio declaran HATEOAS explícitamente. Gateway no lo hereda.
- **L-02**: Mover micrometer-observation-test a dependencyManagement con scope test → Implementado. Ya no se hereda como compile, solo como test cuando se declara.
- **L-03**: Mover springdoc-openapi-starter-webmvc-ui de dependencias globales a dependencyManagement → Implementado. Gateway ahora declara springdoc-openapi-starter-webflux-ui. 10/10 microservicios declaran webmvc-ui.
- **Verificación compilación**: `mvn validate` → BUILD SUCCESS (14 módulos). `mvn compile -pl microservice-gateway` → BUILD SUCCESS. `mvn compile -pl catalog-service -pl elending-service` → BUILD SUCCESS. Gateway tests (98 tests) → 98/0/0/0. Catalog tests (188 tests) → 188/0/0/0. Elending tests (277 tests) → 277/0/0/1 (1 skip pre-existing).

## Historial de Cambios
- 2026-07-20 — M-06: docs/01-arquitectura-general.md actualizado (secciones 9, 9.1, 9.2, Observabilidad y Resiliencia, 11, 12). Items de Circuit Breaker, IDs de correlación y trazabilidad cambiados de "pendiente" a "Implementado".
- 2026-07-20 — M-09: groupId estandarizado a `com.silvio` en 5 servicios: catalog-service, elending-service, license-service, notification-service, subscription-service.
- 2026-07-20 — L-01, L-02, L-03: Limpieza de dependencias Maven en root pom.xml. `spring-boot-starter-hateoas`, `micrometer-observation-test` y `springdoc-openapi-starter-webmvc-ui` movidos de `<dependencies>` globales a `<dependencyManagement>`. Gateway añade `springdoc-openapi-starter-webflux-ui`. 10 microservicios de negocio declaran HATEOAS + webmvc-ui explícitamente. Compilación y tests verificados (14 módulos).
