# Auditoría de Configuración de Feign Clients

**Proyecto:** SmallBooksRepository  
**Fecha:** 2026-07-15  
**Alcance:** Todas las configuraciones de Feign Clients, Circuit Breaker, Timeouts, Logging y Retry  
**Servicios auditados:** `content-service`, `analytics-service`, `search-service`, `elending-service`, `ingestion-service`

---

## Resumen Ejecutivo

| Concepto | Estado |
|---|---|
| Feign Clients declarados | 7 interfaces |
| Clients con Fallback + Circuit Breaker | 4 (solo en `elending-service`) |
| Clients **sin** Fallback ni Circuit Breaker | 3 (`content-service`, `analytics-service`, `search-service`) |
| Timeouts globales vía Config Server | ✅ Configurados (connect: 3s, read: 5s) |
| Timeouts por cliente | ❌ No configurados |
| Logging Level para Feign | ❌ No configurado |
| Retryer personalizado | ❌ No configurado (usa `NEVER_RETRY` de fábrica) |
| TimeLimiter de Resilience4j | Solo en `elending-service` |
| Propagación JWT vía Interceptor | ✅ Solo en `elending-service` |
| FeignException → 503 en GlobalHandler | ✅ Todos los servicios |

---

## Inventario Completo de Feign Clients

| Servicio | Cliente | Target | Fallback/CB | Timeouts propio | Logging Level |
|---|---|---|---|---|---|
| `content-service` | `LendingClient` | `elending-service` | ❌ | ❌ | ❌ |
| `content-service` | `IngestionClient` | `ingestion-service` | ❌ | ❌ | ❌ |
| `analytics-service` | `LendingClient` | `elending-service` | ❌ | ❌ | ❌ |
| `search-service` | `CatalogClient` | `catalog-service` | ❌ | ❌ | ❌ |
| `elending-service` | `CatalogClient` | `catalog-service` | ✅ FallbackFactory + CB | ❌ | ❌ |
| `elending-service` | `IdentityClient` | `identity-service` | ✅ FallbackFactory + CB | ❌ | ❌ |
| `elending-service` | `LicenseClient` | `license-service` | ✅ FallbackFactory + CB | ❌ | ❌ |
| `elending-service` | `SubscriptionClient` | `subscription-service` | ✅ FallbackFactory + CB | ❌ | ❌ |

---

## Hallazgos

### [JAVA-FEIGN-01] Falta de Circuit Breaker y Fallback en Feign Clients de `content-service`

- **Severidad:** Alta
- **Ubicación:**
  - `content-service/src/main/java/com/silvio/content/client/LendingClient.java` (Líneas 10-18)
  - `content-service/src/main/java/com/silvio/content/client/IngestionClient.java` (Líneas 7-13)
- **Tipo de Incumplimiento:** Arquitectura / Calidad

#### Código Identificado

```java
// LendingClient.java — línea 10
@FeignClient(name = "elending-service")
public interface LendingClient {
    // ...
}

// IngestionClient.java — línea 7
@FeignClient(name = "ingestion-service")
public interface IngestionClient {
    // ...
}
```

#### Justificación del Incumplimiento

`content-service` depende de dos microservicios (`elending-service` e `ingestion-service`) para su funcionalidad principal (verificar préstamos activos y obtener bytes de libros). Si cualquiera de estos servicios falla, `content-service` no tiene un mecanismo de degradación controlada: las excepciones `FeignException` se propagan al `GlobalExceptionHandler`, que responde con 503, pero no hay lógica de fallback que permita una respuesta parcial o informativa. La documentación del proyecto (test comentario en `ContentServiceApplicationTests.java`) reconoce explícitamente que estos son `@FeignClient` sin Eureka, pero no se tomaron medidas de resiliencia. Además, no hay `spring.cloud.openfeign.circuitbreaker.enabled=true` en la configuración de `content-service`.

#### Instrucciones de Rectificación

1. Agregar `spring.cloud.openfeign.circuitbreaker.enabled: true` en `content-service/src/main/resources/application.yml`.
2. Implementar `FallbackFactory` para `LendingClient` y `IngestionClient` que devuelvan respuestas degradadas (ej. lista vacía de préstamos, arreglo de bytes vacío).
3. Agregar `contextId` en `@FeignClient` para evitar conflictos de beans si múltiples clientes apuntan al mismo servicio.
4. Agregar configuración de Resilience4j Circuit Breaker (al menos `default` config) y `timelimiter` en `application.yml`.

---

### [JAVA-FEIGN-02] Falta de Circuit Breaker y Fallback en Feign Client de `analytics-service`

- **Severidad:** Alta
- **Ubicación:** `analytics-service/src/main/java/com/silvio/analytics/client/LendingClient.java` (Líneas 10-20)
- **Tipo de Incumplimiento:** Arquitectura / Calidad

#### Código Identificado

```java
// Línea 10
@FeignClient(name = "elending-service")
public interface LendingClient {
    // ...
}
```

#### Justificación del Incumplimiento

`analytics-service` consulta el historial de préstamos y todos los préstamos desde `elending-service` para calcular métricas. Si `elending-service` falla, `analytics-service` no dispone de un fallback que pueda retornar datos cacheados o una respuesta degradada. El servicio de analytics quedaría completamente inoperativo durante una caída de `elending-service`, cuando podría al menos devolver datos previamente calculados o indicar que las métricas no están disponibles.

#### Instrucciones de Rectificación

1. Agregar `spring.cloud.openfeign.circuitbreaker.enabled: true` en `analytics-service/src/main/resources/application.yml`.
2. Implementar `LendingClientFallbackFactory` con respuestas degradadas (lista vacía de préstamos).
3. Agregar configuración de Circuit Breaker y `timelimiter` en su `application.yml`.
4. Considerar caching (Spring Cache) de las respuestas de `elending-service` para servir datos aunque el servicio esté caído.

---

### [JAVA-FEIGN-03] Falta de Circuit Breaker y Fallback en Feign Client de `search-service`

- **Severidad:** Alta
- **Ubicación:** `search-service/src/main/java/com/silvio/search/client/CatalogClient.java` (Líneas 10-27)
- **Tipo de Incumplimiento:** Arquitectura / Calidad

#### Código Identificado

```java
// Línea 10
@FeignClient(name = "catalog-service")
public interface CatalogClient {
    // ...
}
```

#### Justificación del Incumplimiento

`search-service` delega todas las búsquedas a `catalog-service`. Sin un fallback ni circuit breaker, una caída de `catalog-service` deja completamente inoperativo el buscador. El `GlobalExceptionHandler` captura la `FeignException` y devuelve 503, pero no hay posibilidad de devolver resultados de búsqueda parciales, vacíos o pre-cacheados. Este es un punto crítico porque `search-service` es la puerta de entrada a la funcionalidad de búsqueda del sistema.

#### Instrucciones de Rectificación

1. Agregar `spring.cloud.openfeign.circuitbreaker.enabled: true` en `search-service/src/main/resources/application.yml`.
2. Implementar `CatalogClientFallbackFactory` que devuelva lista vacía de libros.
3. Agregar configuración de Circuit Breaker con `sliding-window-size` apropiado para un servicio de búsqueda.
4. Agregar `timelimiter` configuration.

---

### [JAVA-FEIGN-04] Ausencia de Feign Logging Level en Todos los Servicios

- **Severidad:** Baja
- **Ubicación:** Todos los microservicios
  - `content-service/src/main/resources/application.yml`
  - `analytics-service/src/main/resources/application.yml`
  - `search-service/src/main/resources/application.yml`
  - `elending-service/src/main/resources/application.yml`
  - `ingestion-service/src/main/resources/application.yml`
  - `microservice-config/src/main/resources/configurations/application.yml`
- **Tipo de Incumplimiento:** Estándar de Codificación / Calidad

#### Código Identificado

```yaml
# En ningún archivo de configuración aparece
logging.level.com.netflix.loadbalancer: DEBUG  # ni similar
logging.level.feign: DEBUG                     # ni similar
feign.client.config.default.loggerLevel: FULL  # ni similar
```

```java
// No existe @Bean para Logger.Level en ningún @Configuration
// ej. lo que debería existir:
// @Bean
// public Logger.Level feignLoggerLevel() {
//     return Logger.Level.BASIC;
// }
```

#### Justificación del Incumplimiento

Sin un `Logger.Level` configurado para los Feign Clients, Spring Cloud OpenFeign usa `NONE` como nivel por defecto, lo que significa que **no se loguea ninguna petición o respuesta HTTP** de los Feign Clients. Esto dificulta enormemente el debugging de problemas de conectividad entre microservicios, especialmente durante el desarrollo y troubleshooting de incidencias en producción. El proyecto tiene un estándar de logging con `com.silvio: DEBUG`, pero las llamadas Feign son invisibles en los logs.

#### Instrucciones de Rectificación

Agregar en el archivo de configuración global (`microservice-config/src/main/resources/configurations/application.yml`) o en cada servicio:

```yaml
# Opción 1: Nivel de logging global para Feign
logging:
  level:
    feign: DEBUG
    com.netflix.loadbalancer: DEBUG   # si usa Ribbon

# Opción 2: O por cliente Feign via openfeign config
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            loggerLevel: BASIC
```

O crear una clase `@Configuration` con:

```java
@Bean
public Logger.Level feignLoggerLevel() {
    return Logger.Level.BASIC;  // BASIC para producción, FULL para desarrollo
}
```

---

### [JAVA-FEIGN-05] Ausencia de Timeouts Personalizados por Cliente Feign

- **Severidad:** Media
- **Ubicación:** Todos los clientes Feign declarados (7 interfaces)
- **Tipo de Incumplimiento:** Arquitectura / Calidad

#### Justificación del Incumplimiento

Si bien existe una configuración **global** de timeouts via Config Server (`connectTimeout: 3000`, `readTimeout: 5000`), no hay timeouts personalizados por cliente Feign. Diferentes servicios tienen diferentes requisitos de latencia:

| Cliente | Operación | Latencia esperada |
|---|---|---|
| `LicenseClient.prestar()` | PUT - modifica stock | Debería ser rápida (< 2s) |
| `IngestionClient.obtenerBytes()` | GET - transfiere archivos | Puede requerir > 5s |
| `CatalogClient.buscar()` | GET - consulta con filtros | Variable según criterios |
| `IdentityClient.obtenerUsuario()` | GET - consulta simple | Debería ser rápida (< 2s) |

Usar el mismo timeout de 5s para operaciones de lectura de archivos grandes (IngestionClient) y para consultas simples (IdentityClient) puede causar timeouts innecesarios en un caso y esperas excesivas en el otro.

#### Instrucciones de Rectificación

Configurar timeouts específicos por cliente Feign en cada servicio:

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          ingestion-service:
            connectTimeout: 5000
            readTimeout: 30000    # Archivos grandes requieren más tiempo
          identity-service:
            connectTimeout: 2000
            readTimeout: 3000
          elending-service:
            connectTimeout: 3000
            readTimeout: 5000
          catalog-service:
            connectTimeout: 3000
            readTimeout: 7000     # Búsquedas pueden ser lentas
```

---

### [JAVA-FEIGN-06] Ausencia de TimeLimiter en Servicios sin Resilience4j

- **Severidad:** Media
- **Ubicación:**
  - `content-service/src/main/resources/application.yml`
  - `analytics-service/src/main/resources/application.yml`
  - `search-service/src/main/resources/application.yml`
- **Tipo de Incumplimiento:** Arquitectura

#### Código Identificado

```yaml
# Estos servicios no tienen configuración de resilience4j timelimiter
# Ejemplo de lo que falta:
# resilience4j:
#   timelimiter:
#     configs:
#       default:
#         timeout-duration: 5s
#         cancel-running-future: true
```

#### Justificación del Incumplimiento

`elending-service` tiene configurado un `resilience4j.timelimiter` con `timeout-duration: 5s` que cancela futures colgados cuando se excede el timeout. Los servicios `content-service`, `analytics-service` y `search-service` no tienen esta configuración. Si una llamada Feign se cuelga (por ejemplo, el otro servicio no responde pero la conexión TCP no se cierra), el hilo del servidor quedaría bloqueado hasta que el timeout de Feign (5s global) se cumpla, pero sin el `cancel-running-future: true` de TimeLimiter el hilo podría quedar retenido adicionalmente.

#### Instrucciones de Rectificación

Agregar en cada servicio que use Feign Clients (content, analytics, search):

```yaml
resilience4j:
  timelimiter:
    configs:
      default:
        timeout-duration: 5s
        cancel-running-future: true
```

---

### [JAVA-FEIGN-07] Ausencia de `contextId` en Múltiples Feign Clients que Apuntan al Mismo Servicio

- **Severidad:** Baja
- **Ubicación:**
  - `content-service/src/main/java/com/silvio/content/client/LendingClient.java` — target `elending-service`
  - `analytics-service/src/main/java/com/silvio/analytics/client/LendingClient.java` — target `elending-service`
- **Tipo de Incumplimiento:** Arquitectura / Calidad

#### Código Identificado

```java
// content-service
@FeignClient(name = "elending-service")
public interface LendingClient { ... }

// analytics-service
@FeignClient(name = "elending-service")
public interface LendingClient { ... }
```

#### Justificación del Incumplimiento

Aunque estos clientes están en diferentes servicios (por lo tanto no hay colisión de beans en el mismo ApplicationContext), el atributo `contextId` es una buena práctica recomendada por Spring Cloud para identificar inequívocamente cada cliente Feign. Sin `contextId`, Spring Cloud usa el `name` para crear el nombre del bean, lo que funciona cuando los clientes están en diferentes servicios, pero si en el futuro se agrega otro cliente al mismo servicio apuntando al mismo target, se producirá un conflicto de beans.

#### Instrucciones de Rectificación

Agregar `contextId` descriptivo a cada `@FeignClient`:

```java
// content-service
@FeignClient(name = "elending-service", contextId = "elendingContentClient")
public interface LendingClient { ... }

// analytics-service
@FeignClient(name = "elending-service", contextId = "elendingAnalyticsClient")
public interface LendingClient { ... }
```

---

### [JAVA-FEIGN-08] Falta de Configuración de Compresión en Peticiones Feign

- **Severidad:** Baja
- **Ubicación:** Todos los servicios — `microservice-config/src/main/resources/configurations/application.yml`
- **Tipo de Incumplimiento:** Estándar de Codificación / Calidad

#### Justificación del Incumplimiento

Ningún servicio tiene configurada la compresión de peticiones/respuestas Feign. Para un cliente como `IngestionClient` que transfiere bytes de archivos, o `CatalogClient` que puede devolver listas grandes de libros, la compresión mejoraría el rendimiento de la comunicación entre microservicios y reduciría el uso de ancho de banda, especialmente si los servicios no están en la misma máquina.

#### Instrucciones de Rectificación

Agregar en `microservice-config/src/main/resources/configurations/application.yml` (configuración global):

```yaml
spring:
  cloud:
    openfeign:
      compression:
        request:
          enabled: true
          mime-types: application/json,application/xml
          min-request-size: 2048
        response:
          enabled: true
```

---

### [JAVA-FEIGN-09] Ausencia de Retry Documentado — Dependencia del Default `NEVER_RETRY`

- **Severidad:** Baja
- **Ubicación:** Todos los servicios
- **Tipo de Incumplimiento:** Estándar de Codificación / Calidad

#### Justificación del Incumplimiento

Feign 11+ usa por defecto `feign.Retryer.NEVER_RETRY`, lo que significa que **no se reintenta ninguna petición fallida**. Esto es correcto desde la perspectiva de evitar fallos en cascada (no reintentar a un servicio que ya está fallando), pero esta decisión no está documentada ni configurada explícitamente en el proyecto. No existe un `@Bean` de tipo `Retryer` ni propiedades de retry en la configuración Feign. La ausencia de configuración explícita hace que el comportamiento de reintentos sea opaco para futuros mantenedores. Si en el futuro se quisieran reintentar ciertos tipos de fallos (ej. 503 o timeouts de conexión), no hay un punto claro donde hacerlo.

#### Instrucciones de Rectificación

Agregar configuración explícita de retry en la configuración global:

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connectTimeout: 3000
            readTimeout: 5000
```

Y documentar en un comentario en la configuración o crear un `@Bean` de `Retryer` que explicite la política (incluso si es `NEVER_RETRY`):

```java
/**
 * Política de reintentos para Feign Clients.
 * Se usa NEVER_RETRY para evitar fallos en cascada entre microservicios.
 * Si un servicio no responde, el Circuit Breaker se encarga de la
 * degradación en lugar de reintentar operaciones que probablemente
 * volverán a fallar.
 */
@Bean
public Retryer feignRetryer() {
    return Retryer.NEVER_RETRY;
}
```

---

## Mapa de Dependencias entre Microservicios vía Feign

```
┌─────────────────┐     Feign (sin CB)     ┌──────────────────┐
│  content-service │ ──────────────────────▶│ elending-service │
│  (LendingClient) │                        └──────────────────┘
└─────────────────┘
┌─────────────────┐     Feign (sin CB)     ┌──────────────────┐
│  content-service │ ──────────────────────▶│ ingestion-service│
│ (IngestionClient)│                        └──────────────────┘
└─────────────────┘
┌──────────────────┐    Feign (sin CB)     ┌──────────────────┐
│ analytics-service │ ─────────────────────▶│ elending-service │
│ (LendingClient)   │                       └──────────────────┘
└──────────────────┘
┌────────────────┐      Feign (sin CB)     ┌──────────────────┐
│ search-service  │ ──────────────────────▶│ catalog-service  │
│ (CatalogClient) │                        └──────────────────┘
└────────────────┘
┌─────────────────┐    Feign (+ CB + FF)   ┌──────────────────┐
│ elending-service │ ─────────────────────▶│ catalog-service  │
│ (CatalogClient)  │                        └──────────────────┘
├─────────────────┤    Feign (+ CB + FF)   ┌──────────────────┐
│ (IdentityClient) │ ─────────────────────▶│ identity-service │
├─────────────────┤    Feign (+ CB + FF)   ┌──────────────────┐
│ (LicenseClient)  │ ─────────────────────▶│ license-service  │
├─────────────────┤    Feign (+ CB + FF)   ┌────────────────────┐
│(SubscriptionCli.)│ ─────────────────────▶│ subscription-serv.│
└─────────────────┘                        └────────────────────┘

CB  = Circuit Breaker habilitado
FF  = FallbackFactory implementado
sin CB = Sin Circuit Breaker ni Fallback
```

---

## Conclusiones y Recomendaciones Prioritarias

### Crítico (Alta Severidad — Acción Inmediata)

1. **Implementar Circuit Breaker + Fallback en `content-service`** (2 clientes: LendingClient, IngestionClient).
2. **Implementar Circuit Breaker + Fallback en `analytics-service`** (1 cliente: LendingClient).
3. **Implementar Circuit Breaker + Fallback en `search-service`** (1 cliente: CatalogClient).

### Importante (Media Severidad — Próximo Sprint)

4. **Configurar timeouts por cliente Feign** según la naturaleza de cada operación.
5. **Agregar `timelimiter` de Resilience4j** en content, analytics y search services.
6. **Agregar Feign logging level** (al menos `BASIC`) para facilitar debugging.

### Conveniencia (Baja Severidad — Backlog)

7. **Agregar `contextId`** a todos los `@FeignClient` para evitar conflictos futuros.
8. **Configurar compresión Feign** para mejorar rendimiento en comunicaciones entre servicios.
9. **Documentar explícitamente la política de reintentos** (Retryer.NEVER_RETRY).
10. **Evaluar si content-service y analytics-service pueden compartir una librería común** de Feign Clients y Fallbacks para evitar duplicación.

---

*Reporte generado por el Auditor de Arquitectura Java —严格遵守 convenciones CSR y mejores prácticas Spring Cloud.*
