## Última Actualización
- Fecha: 2026-07-29
- Pipeline: AC-01+AC-03 — Actuator + Trazabilidad Feign real

## Estado Actual del Servicio
- spring-boot-starter-actuator agregado. Expone /actuator/health.
- spring.cloud.openfeign.micrometer.enabled: true — Feign crea observaciones Micrometer.
- TracePropagationInterceptor: propaga headers B3 (X-B3-TraceId, X-B3-SpanId) en llamadas Feign a elending-service.
- Clases principales:
  - `AnalyticsService` — Capa de negocio de estadísticas. Consulta datos de préstamos vía Feign client hacia elending-service, sin acceso directo a JPA/Hibernate. Usa `page.getContent()` para obtener la lista de préstamos desde la respuesta paginada.
  - `LendingClient` — Feign client hacia elending-service. `obtenerTodos()` retorna `Page<PrestamoAnalyticsDTO>` (respuesta paginada), `obtenerHistorial(usuarioId)` retorna `List<PrestamoAnalyticsDTO>`. Circuit Breaker habilitado con `fallbackFactory`.
  - `LendingClientFallbackFactory` — FallbackFactory para LendingClient. Cuando elending-service no responde, `obtenerTodos()` devuelve `Page.empty()` y `obtenerHistorial()` devuelve `Collections.emptyList()`.
  - `AnalyticsController` — REST controller para endpoints de estadísticas.
  - `EstadisticasDTO` — DTO de respuesta con totalPrestamos, prestamosActivos, prestamosVencidos, librosMasPrestados (top 5), usuariosMasActivos (top 5).
  - `PrestamoAnalyticsDTO` — DTO de préstamo para analytics (id, libroId, usuarioId, estado, fechaInicio, fechaVencimiento). Con `@JsonIgnoreProperties(ignoreUnknown = true)`.
- Endpoints expuestos:
  - `GET /api/analytics/estadisticas` — Estadísticas globales del sistema.
  - `GET /api/analytics/historial/{usuarioId}` — Historial de préstamos de un usuario específico.
- Dependencias externas: elending-service (Feign), Micrometer Tracing (observabilidad), Spring Data Commons (para deserializar `Page<T>`), Resilience4j (Circuit Breaker)
- Cobertura de tests: 74 tests (LendingClientFallbackFactoryTest: 12, Resilience4jConfigIntegrationTest: 7, más tests existentes). 0 fallos.

## Decisiones Técnicas
- **M-07 Hotfix: Revertir @SecurityScheme a configuración programática** — Se eliminó `@SecurityScheme` annotation class-level (causaba `ArrayIndexOutOfBoundsException` en ASM scanner de Spring Boot 3.3.11, rompiendo `@WebMvcTest`, `@SpringBootTest` y JaCoCo). Reemplazado por configuración programática en `customOpenAPI()` usando modelos OpenAPI: `.components(new Components().addSecuritySchemes("BearerAuth", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))).addSecurityItem(new SecurityRequirement().addList("BearerAuth"))`. Alternativa descartada: mantener anotación `@SecurityScheme` — incompatible con ASM 9.x de Spring Boot 3.3.11.
- **M-07 Hotfix: SwaggerConfigTest Spring Context** — Reemplazado el static source scan por `@SpringBootTest(classes = SwaggerConfig.class, webEnvironment = NONE)` con `@ActiveProfiles("test")`. Verifica el bean OpenAPI real en lugar de escanear el fuente.
- `LendingClient.obtenerTodos()` cambió de `List<PrestamoAnalyticsDTO>` a `Page<PrestamoAnalyticsDTO>` — consistente con el endpoint paginado de elending-service. El FeignClient no pasa parámetros de paginación explícitos, por lo que aplican los defaults del servidor (page=0, size=50, sort=fechaInicio,DESC), suficientes para AnalyticsService que procesa la primera página.
- Se agregó dependencia `spring-data-commons` en `pom.xml` — necesaria para que Jackson (vía FeignDecoder) pueda deserializar correctamente `Page<T>` usando `PageJacksonModule`. Sin esta dependencia, Feign no puede mapear la respuesta JSON paginada a un objeto `Page`.
- `AnalyticsService` ahora usa `page.getContent()` en lugar de recibir directamente la lista — permite acceder a metadatos de paginación (número de página, total de páginas, total de elementos) para logging informativo. Los cálculos de estadísticas solo usan `getContent()`.
- Log mejorado con detalles de paginación: `"Total préstamos obtenidos para análisis: {} (página {}/{}, total {})"` — útil para monitorear cuántos datos se están procesando realmente.
- `@JsonIgnoreProperties(ignoreUnknown = true)` en `PrestamoAnalyticsDTO` — protege contra cambios en el DTO de elending-service que agreguen campos nuevos. La deserialización de `Page` no se ve afectada por campos extra en el JSON.
- `@Transactional(readOnly = true)` agregado a `obtenerEstadisticas()` e `historialUsuario()` — aunque el servicio solo usa Feign Clients (sin JPA directo), la anotación marca la intención de solo-lectura y habilita optimizaciones si en el futuro se agrega acceso a base de datos.
- FallbackFactory en lugar de `fallback` simple — permite loguear la causa exacta del error de conexión. Consistente con el patrón de elending-service.
- `spring.cloud.openfeign.circuitbreaker.enabled: true` — habilita el wrapper de Circuit Breaker de Resilience4j sobre cada `@FeignClient`.
- El nombre de instancia `elending-service` en `resilience4j.circuitbreaker.instances` coincide exactamente con el `name` del `@FeignClient`.
- Configuración de Resilience4j idéntica a elending-service: sliding-window-size=10, failure-rate-threshold=50%, wait-duration-in-open-state=30s, timeout global de 5s.

## Criterios de Aceptación Cumplidos
- Cambiar return type de `LendingClient.obtenerTodos()` de `List<PrestamoAnalyticsDTO>` a `Page<PrestamoAnalyticsDTO>` → Implementado con import de `Page`
- Actualizar `AnalyticsService` para usar `page.getContent()` → Implementado. Log incluye metadatos de paginación.
- Agregar `spring-data-commons` en pom.xml para soporte de deserialización `Page<T>` → Implementado.
- Tests actualizados: AnalyticsServiceTest usa `new PageImpl<>(...)` en mocks. Nuevo test `obtenerEstadisticas_conPageConMetadatos_usaGetContentCorrectamente` verifica que solo se usa `getContent()`, no `totalElements`. Nuevo `LendingClientPageDeserializationTest` con 5 tests cubre deserialización JSON de Page (2 elementos, 1 elemento, vacío, multipágina, campos ignorados).
- Agregar `@Transactional(readOnly = true)` a métodos read-only de analytics-service → Implementado previamente en `obtenerEstadisticas()` e `historialUsuario()`.
- Agregar FallbackFactory para LendingClient (fallback retorna `Page.empty()` para `obtenerTodos` y `Collections.emptyList()` para `obtenerHistorial`) → Implementado en `LendingClientFallbackFactory`.
- Agregar `spring.cloud.openfeign.circuitbreaker.enabled: true` en application.yml → Implementado.
- Agregar Resilience4j circuitbreaker + timelimiter config en application.yml → Implementado con valores idénticos a elending-service.
- Agregar dependencia `spring-cloud-starter-circuitbreaker-resilience4j` en pom.xml → Implementado.

## Historial de Cambios
- 2026-07-20 — Z-01: Eliminadas ErrorDatosPrestamosException y ErrorHistorialUsuarioException (zombies puras, 0 referencias). Sin cambios en código — nunca fueron instanciadas.
- 2026-07-18 — M-07 Hotfix: ASM bug fix. @SecurityScheme eliminado de SwaggerConfig, reemplazado por configuración programática de modelos OpenAPI. SwaggerConfigTest migrado de static source scan a @SpringBootTest con contexto real. Verificado: 76 tests PASS, JaCoCo OK.
- 2026-07-17 — M-05: @SecurityScheme agregado en SwaggerConfig. Import SecuritySchemeType corregido (de .security a .enums). SwaggerConfigTest static source scan verifica anotación. Todos los @Operation tienen @SecurityRequirement(name="BearerAuth").
- 2026-07-15 — Agregado `@Transactional(readOnly = true)` a `obtenerEstadisticas()` e `historialUsuario()` para optimización de rendimiento JPA y consistencia con el código base.
- 2026-07-15 — `LendingClient.obtenerTodos()` cambió a `Page<PrestamoAnalyticsDTO>`. `AnalyticsService` actualizado para usar `page.getContent()`. Dependencia `spring-data-commons` agregada. Tests de deserialización Page agregados.
- 2026-07-16 — Agregado Circuit Breaker + FallbackFactory a LendingClient siguiendo el patrón de elending-service. Resilience4j config en application.yml. Dependencia en pom.xml.
- 2026-07-29 — AC-01: Agregado spring-boot-starter-actuator + management.endpoints.web.exposure.include: health.
- 2026-07-29 — AC-03: Agregado spring.cloud.openfeign.micrometer.enabled: true + TracePropagationInterceptor. Trazabilidad fin-a-fin via Feign.
