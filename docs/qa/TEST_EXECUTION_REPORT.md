# Reporte de ejecución de pruebas

## Ejecución base — 2026-10-22

### Entorno

- Sistema: Linux local.
- Java: 17.
- Node.js: 22.23.3.
- npm: 10.9.9.
- Navegadores disponibles: Chromium 151 y Firefox 155.
- Orquestación: Docker Compose validado sintácticamente.

### Resultados registrados

| Suite | Comando | Resultado | Evidencia |
|---|---|---|---|
| Backend | `./gradlew test --no-daemon` | Passed | Build exitoso; 144 pruebas descubiertas, 136 ejecutadas y 8 JDBC omitidas sin `MUSICWAVE_TEST_DB_URL` |
| Frontend | `npm test -- --reporter=dot` | Passed | 19/19 archivos y 139/139 pruebas |
| Calidad frontend | `npm run check && npm run lint && npm run format && npm run build` | Passed | Tipos, lint, formato y build estático sin errores |
| Dependencias frontend | `npm audit --audit-level=moderate` | Passed | 0 vulnerabilidades reportadas |
| Docker Compose | `docker compose -f infrastructure/compose.yaml config --quiet` | Passed | Exit code 0 |
| Compilación QA | `./gradlew -p ../qa testClasses --no-daemon` | Passed | Karate, Selenium y JUnit compilan con Java 17 |
| Karate black-box | `./backend/gradlew -p qa apiTest --no-daemon` | Passed | 5 escenarios ejecutados contra el stack saludable |
| Selenium smoke | `./backend/gradlew -p qa uiTest --no-daemon` | Passed | 2 pruebas headless ejecutadas con Chromium |

## Interpretación

- Los gates unitarios, de componentes, API black-box y humo web están verdes.
- Las ocho pruebas omitidas no son fallos: están condicionadas a una base PostgreSQL de prueba explícita.
- Karate y Selenium se ejecutaron contra servicios reales locales; no se contabilizaron como aprobadas solamente por compilar.
- El resultado local es reproducible, pero no reemplaza la primera ejecución del workflow de GitHub Actions después de publicar.

## Defectos

- QA-001: cerrado y documentado en [`DEFECT_REPORT_YOUTUBE_PLAYER_CLIENT.md`](DEFECT_REPORT_YOUTUBE_PLAYER_CLIENT.md).
- Defectos críticos o altos abiertos en esta ejecución: ninguno registrado.

## Siguiente verificación

Tras publicar el repositorio, se revisará la primera ejecución de GitHub Actions y se registrará cualquier diferencia entre el entorno local y CI como defecto o deuda técnica, según corresponda.
