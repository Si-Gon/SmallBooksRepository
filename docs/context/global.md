## Última Actualización
- Fecha: 2026-07-20
- Pipeline: M-06 + M-09 — Actualización documentación arquitectura y estandarización groupId Maven

## Estado Actual del Proyecto
- **docs/01-arquitectura-general.md**: Documento de arquitectura general actualizado con estado real del proyecto.
- **groupId Maven**: Todos los 10 microservicios de negocio estandarizados a `com.silvio`.
- **Resilience4j Circuit Breaker**: Implementado en los 10 microservicios de negocio con patrón FallbackFactory.
- **Micrometer Tracing + Zipkin**: Implementado en los 13 servicios (10 negocio + 3 infraestructura).
- **RabbitMQ async**: Implementado entre elending-service (productor) y notification-service (consumidor) con SHA-256 idempotency key.

## Decisiones Técnicas
- **M-06: Documentar estado real de Circuit Breaker, Tracing y RabbitMQ** — La arquitectura general marcaba como "pendiente" características ya implementadas: Circuit Breaker (Resilience4j + FallbackFactory en 10 MS), IDs de correlación (Micrometer Tracing traceId/spanId en MDC), trazabilidad distribuida (Micrometer Brave + Zipkin en 13 servicios). Se actualizaron a "Implementado" con detalles técnicos específicos.
- **M-06: Nueva sección 9.2 Comunicación Asíncrona con RabbitMQ** — Se documentó el flujo async entre elending-service y notification-service, incluyendo exchange direct `smallbooks.notifications`, idempotencia SHA-256 (`idempotencyKey`), y trazabilidad distribuida via headers AMQP.
- **M-06: Tabla de pruebas actualizada** — Se reescribió la sección 11 con tabla desglosada: de 66 tests en 6 MS a 1.228 tests en 10 MS con cobertura JaCoCo individual. Se agregaron tipos de test (integración, seguridad, resiliencia).
- **M-06: Scheduler documentado con ShedLock** — Sección 12 actualizada con mención de ShedLock (`shedlock-spring` 5.13.0 + `shedlock-provider-jdbc-template`) para bloqueo distribuido.
- **M-09: Estandarización groupId a `com.silvio`** — 5 servicios usaban `com.silvio.{service}` (catalog, elending, license, notification, subscription). Cambiados a `com.silvio`. Los otros 5 (analytics, content, identity-services, ingestion, search) ya tenían `com.silvio`. Infraestructura (config, eureka, gateway) mantiene `com.microservice.*`. El groupId es metadata Maven — no afecta paquetes Java, compilación ni imports.

## Criterios de Aceptación Cumplidos
- **M-06**: Actualizar docs/01-arquitectura-general.md con estado real → Implementado. Circuit Breaker, Tracing y RabbitMQ documentados como "Implementado". Tabla de tests actualizada con 1.228 tests. ShedLock documentado.
- **M-09**: Estandarizar groupId a `com.silvio` en todos los microservicios de negocio → Implementado. 5 servicios cambiados, 5 ya correctos. `mvn validate` exitoso en los 10 servicios.

## Historial de Cambios
- 2026-07-20 — M-06: docs/01-arquitectura-general.md actualizado (secciones 9, 9.1, 9.2, Observabilidad y Resiliencia, 11, 12). Items de Circuit Breaker, IDs de correlación y trazabilidad cambiados de "pendiente" a "Implementado".
- 2026-07-20 — M-09: groupId estandarizado a `com.silvio` en 5 servicios: catalog-service, elending-service, license-service, notification-service, subscription-service.
