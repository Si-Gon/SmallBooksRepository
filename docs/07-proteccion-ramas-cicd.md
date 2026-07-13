# Protección de Ramas y CI/CD — SmallBooks

> **Reglas de protección para ramas críticas y configuración del pipeline de integración continua.**

---

## 1. Ramas Protegidas

| Rama | Protección | Uso |
|------|-----------|-----|
| `main` | 🛡️ Total | Releases estables, solo merges via Pull Request |
| `sigon` | 🛡️ Parcial | Desarrollo activo, push directo permitido con CI/CD obligatorio |

---

## 2. Configuración de Branch Protection (`main`)

Aplicar en GitHub → Settings → Branches → Add rule:

### Regla para `main`

| Parámetro | Valor | Motivo |
|-----------|-------|--------|
| **Branch name pattern** | `main` | |
| ✅ **Require a pull request before merging** | Activado | Evita commits directos a producción |
| ✅ Require approvals | `1` | Al menos un reviewer debe aprobar |
| ✅ Dismiss stale pull request approvals when new commits are pushed | Activado | Si cambia el código, se necesita re-aprobación |
| ✅ **Require status checks to pass before merging** | Activado | El CI debe pasar sí o sí |
| ✅ Require branches to be up to date | Activado | El PR debe estar al día con `main` |
| **Status checks** | `Compilar y ejecutar tests` | Coincide con el job del workflow |
| ✅ **Require conversation resolution before merging** | Activado | Todos los comentarios resueltos |
| ✅ **Do not allow bypassing the above settings** | Activado | Ni admins pueden saltarse las reglas |
| ✅ Lock branch | Desactivado | |

### Regla para `sigon`

| Parámetro | Valor | Motivo |
|-----------|-------|--------|
| **Branch name pattern** | `sigon` | |
| ✅ **Require status checks to pass before merging** | Activado | Opcional si se permite push directo |
| **Status checks** | `Compilar y ejecutar tests` | |
| ✅ **Restrict deletions** | Activado | Evita borrados accidentales |

---

## 3. Pipeline CI/CD (`ci-sigon.yml`)

### Disparadores

| Evento | Rama | Acción |
|--------|------|--------|
| `push` | `sigon` | Build + tests completos |
| `pull_request` | `main` ← `sigon` | Validación previa al merge |
| `workflow_dispatch` | Cualquiera | Ejecución manual desde GitHub UI |

### Control de Concurrencia

El workflow cancela automáticamente ejecuciones previas en la misma rama si hay una más reciente (`concurrency.cancel-in-progress: true`). Esto evita builds encolados innecesarios cuando se hacen pushes rápidos.

### Jobs

```
compilar-y-testear
  ├── Checkout del código
  ├── Configurar JDK 17 (Temurin)      ← cachea ~/.m2/repository
  ├── Compilar y testear con Maven     ← mvn clean test
  ├── Subir reportes JaCoCo            ← cobertura HTML
  ├── Subir reportes JUnit             ← resultados XML
  └── Resumen del build                ← tabla en GitHub Summary

notificar-fallo (solo si failure())
  └── Marcar fallo en el check         ← incluye commit, autor, rama
```

### Resumen del Build

El workflow publica automáticamente un resumen en la página de ejecución (GitHub Summary) con:
- Estado de compilación
- Resultado de tests
- Número de módulos
- Enlaces a los artefactos

### Pasos para añadir un nuevo status check

1. Abrir GitHub → Settings → Branches → editar regla de `main`
2. En "Status checks that are required", buscar el nombre del job
3. Marcar la casilla del nuevo check

> ⚠️ El nombre del check debe coincidir exactamente con el `name:` del job en el workflow YAML.

---

## 4. Artefactos Generados

Cada ejecución del workflow produce:

| Artefacto | Contenido | Formato |
|-----------|-----------|---------|
| `jacoco-reports` | Reportes de cobertura por módulo | HTML |
| `test-results` | Resultados XML de JUnit (surefire/failsafe) | XML |

Descargables desde la página de ejecución del workflow en GitHub.

---

## 5. Checklist para Incorporar un Nuevo Microservicio

- [ ] ¿El `pom.xml` del módulo está incluido en el `<modules>` del root `pom.xml`?
- [ ] ¿El test de contexto `*ApplicationTests.java` usa `@ActiveProfiles("test")`?
- [ ] ¿Existe `src/test/resources/application-test.yml`?
- [ ] ¿El `application-test.yml` desactiva Config Server, Eureka y Flyway (si aplica)?
- [ ] ¿Usa H2 en memoria si el módulo tiene base de datos?
- [ ] ¿El workflow lo compila y testea automáticamente? (Sí — Maven multi-módulo lo incluye)

---

## 6. Estado de los Microservicios (verificado Julio 2026)

| Módulo | `application-test.yml` | `@ActiveProfiles` | Tests | Estado CI |
|--------|:---------------------:|:------------------:|:----:|:---------:|
| microservice-config | ✅ Configurado | `{"test", "native"}` | 1 | ✅ OK |
| microservice-eureka | ✅ Configurado | `"test"` | 1 | ✅ OK |
| microservice-gateway | ✅ Existente | `"test"` | 30 | ✅ OK |
| identity-services | ✅ Existente | `"test"` | 37 | ✅ OK |
| catalog-service | ✅ Existente | `"test"` | 89 | ✅ OK |
| license-service | ✅ Existente | `"test"` | 31 | ✅ OK |
| elending-service | ✅ Existente | `"test"` | 200 | ✅ OK |
| ingestion-service | ✅ Existente | `"test"` | 19 | ✅ OK (1 skip) |
| content-service | ✅ Existente | `"test"` | 11 | ✅ OK |
| notification-service | ✅ Existente | `"test"` | 78 | ✅ OK |
| subscription-service | ✅ Existente | `"test"` | 27 | ✅ OK |
| search-service | ✅ Existente | `"test"` | 19 | ✅ OK |
| analytics-service | ✅ Existente | `"test"` | 13 | ✅ OK |

> **Total:** 556 tests, 0 fallos, 0 errores, 1 skip — verificado Julio 2026

> **Nota:** Los módulos `microservice-config` y `microservice-eureka` requirieron correcciones durante la implementación del CI/CD:
> - `microservice-config`: Su test necesita el perfil `native` para que Config Server use el backend nativo de archivos en lugar del git. Se corrigió `@ActiveProfiles({"test", "native"})` y se ajustó `application-test.yml`.
> - `microservice-eureka`: Su test no puede deshabilitar `eureka.client.enabled=false` porque `EurekaServerAutoConfiguration` necesita `ApplicationInfoManager` (provisto por el cliente). Se corrigió `application-test.yml` para usar `register-with-eureka: false` en lugar de deshabilitar el cliente por completo.

---

## 7. Resolución de Problemas

| Síntoma | Causa probable | Solución |
|---------|---------------|----------|
| El workflow no se ejecuta | El push fue a otra rama que no es `sigon` | Verificar rama destino |
| Falla `mvn clean test` | Dependencia no encontrada en Maven Central | Revisar `pom.xml` y `settings.xml` |
| Tests con errores de conexión | Falta `application-test.yml` o `@ActiveProfiles("test")` | El test intenta conectar a Config Server real |
| JaCoCo no genera reportes | El plugin no está en el `pom.xml` raíz | Verificar `jacoco-maven-plugin` en `<pluginManagement>` |
| El check requerido no aparece en GitHub | El job nunca se ha ejecutado en esa rama | Hacer un push a `sigon` para que se ejecute al menos una vez |
| Config Server test: `need to configure a uri for the git repository` | `@ActiveProfiles("test")` sobreescribe `spring.profiles.active: native`, el Config Server intenta usar git | Usar `@ActiveProfiles({"test", "native"})` en la clase de test |
| Eureka Server test: `No qualifying bean of type ApplicationInfoManager` | `eureka.client.enabled: false` desactiva el cliente que provee `ApplicationInfoManager`, pero el servidor lo necesita | No deshabilitar el cliente; usar `register-with-eureka: false` y `fetch-registry: false` en su lugar |

---

---

## 8. Validación Local del Workflow

Antes de hacer push, puedes validar la sintaxis YAML del workflow localmente:

```bash
# Usando Python (requiere PyYAML)
python -c "import yaml; yaml.safe_load(open('.github/workflows/ci-sigon.yml')); print('✅ YAML válido')"

# Usando action-validator (recomendado, vía npm)
npx action-validator .github/workflows/ci-sigon.yml
```

> **Nota:** La validación sintáctica no garantiza que los pasos del workflow sean correctos en GitHub Actions. Para una validación completa, haz push a `sigon` y revisa la ejecución en la pestaña Actions.

---

## 9. Archivos del Pipeline

| Archivo | Propósito |
|---------|-----------|
| `.github/workflows/ci-sigon.yml` | Definición del workflow CI/CD |
| `docs/07-proteccion-ramas-cicd.md` | Documentación de branch protection y CI/CD |
| `pom.xml` (raíz) | Configuración multi-módulo con JaCoCo |

---

> **Última actualización:** Julio 2026
