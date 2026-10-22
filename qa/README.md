# Automatización QA

Este módulo contiene pruebas de caja negra independientes del código productivo:

- Karate valida salud, disponibilidad de herramientas, creación, idempotencia, consulta, artefactos, cancelación y errores HTTP.
- Selenium valida el flujo visible principal, navegación, accesibilidad básica y restricciones de ajustes.

## Requisitos

- Java 17.
- API saludable en `http://localhost:8080`.
- Frontend en `http://localhost:4321` para Selenium.
- Chromium o Chrome instalado.

Las URLs pueden modificarse mediante `API_BASE_URL` y `UI_BASE_URL`.

## Ejecución

Desde la raíz del repositorio:

```bash
# Solo API
./backend/gradlew -p qa apiTest --no-daemon

# Solo interfaz web
./backend/gradlew -p qa uiTest --no-daemon

# Regresión black-box completa
./backend/gradlew -p qa test --no-daemon
```

Los reportes HTML quedan en `qa/build/reports/tests/`. La suite crea datos de prueba con identificadores UUID y cancela su propio trabajo; debe ejecutarse en un entorno de QA o desarrollo, no directamente sobre producción.

Appium no se incluye porque el producto no posee una aplicación móvil nativa que pueda funcionar como objetivo real de automatización.
