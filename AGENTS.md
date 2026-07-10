# AGENTS.md — SmallBooks

## Contexto MCP
- **project**: `"SmallBooks"`
- Siempre pasar `project="SmallBooks"` en todas las herramientas de memoria y tareas MCP.

## Descripción del proyecto
Aplicación de microservicios con Spring Boot 3.2.x. Arquitectura distribuida con 13 servicios independientes.

## Stack técnico
- **Framework**: Spring Boot 3.2.x + Spring Cloud
- **Patrón REST**: HATEOAS (enlaces hipermedia en respuestas)
- **Base de datos**: Flyway para migraciones
- **Comunicación entre servicios**: OpenFeign
- **Service discovery**: Eureka
- **Documentación**: Swagger / OpenAPI
- **Tests**: JUnit + Given/When/Then (66 tests unitarios + REST)
- **Autenticación**: JWT

## Estructura de paquetes (por microservicio)
```
src/main/java/com/smallbooks/{servicio}/
    controller/     ← endpoints REST
    service/        ← lógica de negocio
    repository/     ← acceso a datos
    dto/            ← objetos de transferencia
    entity/         ← entidades JPA
    exception/      ← manejo de errores
```

## Convenciones importantes
- Patrón CSR estricto: Controller → Service → Repository
- DTOs para entrada y salida, nunca exponer entidades directamente
- Validaciones con Bean Validation (`@Valid`, `@NotNull`, etc.)
- Manejo de errores centralizado con `@ControllerAdvice`
- Tests estructurados en Given / When / Then

## Agentes y sus roles

### Explorer
Analiza la estructura del microservicio indicado. Guarda en memoria:
- `save_memory("explorer", "estructura", "...", project="SmallBooks")`
- `save_memory("explorer", "dependencias", "...", project="SmallBooks")`
- `save_memory("explorer", "problemas", "...", project="SmallBooks")`

### Coder
Lee memoria del Explorer y genera o modifica código respetando el patrón CSR y las convenciones del proyecto.

### Debugger
Revisa el código generado. Si hay errores los reporta y devuelve al Coder. Si está correcto, pasa al Tester.

### Tester
Genera tests JUnit con estructura Given/When/Then. Cubre casos felices, errores y casos borde.

## Notas para el agente
- Trabajar sobre UN microservicio a la vez, no sobre el proyecto completo
- Spring Boot 3.x usa `jakarta.*` no `javax.*`
- Respetar la versión `3.2.x` en el pom.xml
