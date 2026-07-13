# ============================================================
# Script de validacion del pipeline CI/CD
# Verifica que todos los microservicios tengan la configuracion
# necesaria para ejecutarse correctamente en GitHub Actions.
# ============================================================

param(
    [switch]$Verbose
)

$ErrorActionPreference = "Stop"
$rootDir = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$passed = 0
$failed = 0
$warnings = 0

function Write-Result {
    param([string]$Test, [bool]$Ok, [string]$Detail = "")
    if ($Ok) {
        Write-Host "  [PASS]" -ForegroundColor Green -NoNewline
        $script:passed++
    } else {
        Write-Host "  [FAIL]" -ForegroundColor Red -NoNewline
        $script:failed++
    }
    Write-Host " $Test" -NoNewline
    if ($Detail) { Write-Host " - $Detail" -ForegroundColor DarkGray }
    else { Write-Host "" }
}

function Write-Warn {
    param([string]$Test, [string]$Detail)
    Write-Host "  [WARN]" -ForegroundColor Yellow -NoNewline
    Write-Host " $Test - $Detail" -ForegroundColor DarkGray
    $script:warnings++
}

function Get-StringMatch {
    param([string]$Content, [string]$Pattern)
    return $Content -match $Pattern
}

# ============================================================
# 1. Validar que el workflow existe y su sintaxis YAML
# ============================================================
Write-Host "`n=== Validando archivo de workflow ===" -ForegroundColor Cyan
$workflowPath = Join-Path (Join-Path $rootDir ".github") "workflows\ci-sigon.yml"
$wfExists = Test-Path $workflowPath
Write-Result "ci-sigon.yml existe" $wfExists

if ($wfExists) {
    $yamlContent = Get-Content $workflowPath -Raw

    Write-Result "Triggers: push a sigon" ($yamlContent -match "push:")
    Write-Result "Triggers: PR a main" ($yamlContent -match "pull_request:")
    Write-Result "Triggers: workflow_dispatch" ($yamlContent -match "workflow_dispatch:")
    Write-Result "Control de concurrencia" ($yamlContent -match "concurrency:")
    Write-Result "JDK 17 (Temurin)" (($yamlContent -match "java-version: '17'") -and ($yamlContent -match "distribution: 'temurin'"))
    Write-Result "JaCoCo habilitado" ($yamlContent -match "jacoco.skip=false")
    Write-Result "Resumen del build (GITHUB_STEP_SUMMARY)" ($yamlContent -match "GITHUB_STEP_SUMMARY")

    $jobName = "Compilar y ejecutar tests"
    Write-Result "Job name: '$jobName' (match con docs)" ($yamlContent -match [regex]::Escape($jobName))

    $wfName = "CI/CD . SmallBooks"
    Write-Result "Workflow name: 'CI/CD - SmallBooks'" ($yamlContent -match [regex]::Escape($wfName) -or ($yamlContent -match "CI/CD.*SmallBooks"))

    Write-Result "Checkout action v4" ($yamlContent -match "actions/checkout@v4")
    Write-Result "Setup Java action v4" ($yamlContent -match "actions/setup-java@v4")
    Write-Result "Upload artifact action v4" ($yamlContent -match "actions/upload-artifact@v4")
    Write-Result "Resumen con estado dinamico (job.status)" ($yamlContent -match "job.status")
    Write-Result "Job: notificar-fallo" ($yamlContent -match "notificar-fallo:")
    Write-Result "  -> depends on compilar-y-testear" ($yamlContent -match "needs: compilar-y-testear")
    Write-Result "  -> solo si failure()" ($yamlContent -match "if: failure\(\)")
    Write-Result "Timeout: 30 minutos" ($yamlContent -match "timeout-minutes: 30")
    Write-Result "Retencion de artefactos: 7 dias" ($yamlContent -match "retention-days: 7")
}

# ============================================================
# 2. Verificar que todos los modulos tienen test config
# ============================================================
Write-Host "`n=== Verificando configuracion de tests por modulo ===" -ForegroundColor Cyan

$modules = @(
    "microservice-config",
    "microservice-eureka",
    "microservice-gateway",
    "identity-services",
    "catalog-service",
    "license-service",
    "elending-service",
    "ingestion-service",
    "content-service",
    "notification-service",
    "subscription-service",
    "search-service",
    "analytics-service"
)

foreach ($module in $modules) {
    $modulePath = Join-Path $rootDir $module
    $testResources = Join-Path (Join-Path (Join-Path (Join-Path $modulePath "src") "test") "resources") "application-test.yml"
    $testYmlExists = Test-Path $testResources

    Write-Result "$module - application-test.yml" $testYmlExists
}

# ============================================================
# 3. Verificar que todos los modulos tienen ApplicationTests
# ============================================================
Write-Host "`n=== Verificando ApplicationTests classes ===" -ForegroundColor Cyan

$testFiles = Get-ChildItem -Path $rootDir -Recurse -Filter "*ApplicationTests.java" -File | Where-Object {
    $_.FullName -match "src\\test\\java"
}
Write-Result "ApplicationTests encontrados: $($testFiles.Count) (esperados: 13)" ($testFiles.Count -eq 13)

# Contar tests totales
Write-Host "`n=== Contando tests JUnit 5 ===" -ForegroundColor Cyan
$totalTests = 0
$totalFiles = 0
$moduleResults = @{}
foreach ($module in $modules) {
    $modulePath = Join-Path $rootDir $module
    $testDir = Join-Path (Join-Path (Join-Path $modulePath "src") "test") "java"
    $moduleCount = 0
    $moduleFiles = 0
    if (Test-Path $testDir) {
        $files = Get-ChildItem -Path $testDir -Recurse -File -Include "*Test.java", "*Tests.java" | Where-Object { $_.Name -ne "package-info.java" }
        $moduleFiles = $files.Count
        foreach ($file in $files) {
            $content = Get-Content -Path $file.FullName -Raw
            $matches = [regex]::Matches($content, '@(Test|ParameterizedTest)')
            $moduleCount += $matches.Count
        }
    }
    $moduleResults[$module] = @{ Files = $moduleFiles; Tests = $moduleCount }
    $totalFiles += $moduleFiles
    $totalTests += $moduleCount
}

foreach ($module in $modules) {
    $r = $moduleResults[$module]
    Write-Host "  $module : $($r.Tests) tests en $($r.Files) archivos" -ForegroundColor DarkGray
}
Write-Host "  TOTAL: $totalTests JUnit 5 en $totalFiles archivos" -ForegroundColor White

# ============================================================
# 4. Verificar documentacion de branch protection
# ============================================================
Write-Host "`n=== Verificando documentacion ===" -ForegroundColor Cyan
$docPath = Join-Path (Join-Path $rootDir "docs") "07-proteccion-ramas-cicd.md"
$docExists = Test-Path $docPath
Write-Result "docs/07-proteccion-ramas-cicd.md existe" $docExists

if ($docExists) {
    $docContent = Get-Content $docPath -Raw -Encoding UTF8
    Write-Result "Seccion: Branch Protection" ($docContent -match "Protecci.n de Ramas")
    Write-Result "Seccion: Pipeline CI/CD" ($docContent -match "Pipeline CI/CD")
    Write-Result "Seccion: Troubleshooting" ($docContent -match "Resoluci.n de Problemas")
    Write-Result "Seccion: Estado de modulos" ($docContent -match "Estado de los Microservicios")
    Write-Result "Seccion: Validacion local" ($docContent -match "Validaci.n Local del Workflow")
}

# ============================================================
# 5. Verificar el pom.xml raiz
# ============================================================
Write-Host "`n=== Verificando pom.xml raiz ===" -ForegroundColor Cyan
$pomPath = Join-Path $rootDir "pom.xml"
$pomExists = Test-Path $pomPath
Write-Result "pom.xml raiz existe" $pomExists

if ($pomExists) {
    $pomContent = Get-Content $pomPath -Raw
    Write-Result "JaCoCo plugin configurado" ($pomContent -match "jacoco-maven-plugin")

    $allModulesOk = $true
    foreach ($module in $modules) {
        if ($pomContent -notmatch [regex]::Escape("<module>$module</module>")) {
            Write-Result "  Modulo $module en <modules>" $false
            $allModulesOk = $false
        }
    }
    Write-Result "Todos los 13 modulos en <modules>" $allModulesOk
}

# ============================================================
# Resumen final
# ============================================================
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  RESUMEN DE VALIDACION CI/CD" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
if ($failed -eq 0) {
    Write-Host "  Tests: $passed passed, $failed failed, $warnings warnings" -ForegroundColor Green
} else {
    Write-Host "  Tests: $passed passed, $failed failed, $warnings warnings" -ForegroundColor Red
}
Write-Host "  Modulos: 13 microservicios"
Write-Host "  Tests:   $totalTests JUnit 5 en $totalFiles archivos"
Write-Host "========================================" -ForegroundColor Cyan

if ($failed -gt 0) {
    exit 1
}
exit 0
