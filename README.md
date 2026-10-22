# MusicWave

[![Quality Gate](https://github.com/SalasJean/MusicWave/actions/workflows/quality.yml/badge.svg)](https://github.com/SalasJean/MusicWave/actions/workflows/quality.yml)

Proyecto independiente full-stack para gestionar solicitudes multimedia mediante una cola asíncrona, convertir audio a MP3 y entregarlo al navegador como un artefacto temporal de un solo uso.

El repositorio está presentado principalmente como evidencia de formación en **QA y testing**: análisis de requerimientos, criterios de aceptación, casos funcionales y de regresión, pruebas de caja negra, validación de APIs REST, automatización y reporte de defectos.

> Estado: aplicación funcional en entorno local. No representa un servicio público ni una promesa de capacidad productiva.

## Qué demuestra este proyecto

| Competencia | Evidencia dentro del repositorio |
|---|---|
| QA funcional y caja negra | Plan, casos, criterios de aceptación y matriz de trazabilidad en [`docs/qa`](docs/qa/) |
| Regresión | 144 pruebas backend descubiertas por Gradle y 139 pruebas frontend con Vitest |
| APIs REST | Colección Postman, contrato OpenAPI 3.1 y automatización Karate |
| Códigos HTTP y JSON | Escenarios positivos/negativos, Problem Details y aserciones de esquema/campos |
| Automatización web | Pruebas de humo con Selenium WebDriver y Java 17 |
| Bug tracking | Flujo de defectos, plantilla de GitHub Issues y caso real documentado |
| Integración continua | GitHub Actions ejecuta backend, frontend y regresión black-box |
| Appium | Conocimiento en formación; no se declara automatización móvil sin una aplicación móvil objetivo |

La guía de evidencias para una revisión rápida está en [`docs/qa/QA_PORTFOLIO.md`](docs/qa/QA_PORTFOLIO.md).

## Funcionalidades principales

- Registro de una URL, varias URLs o un archivo TXT.
- Bandeja persistente con estados `PENDING`, `QUEUED`, `DOWNLOADING`, `CONVERTING`, `COMPLETED`, `FAILED` y `CANCELLED`.
- Cola distribuida con PostgreSQL, RabbitMQ y workers independientes.
- Concurrencia configurable de 1 a 10 trabajos.
- Progreso en tiempo real mediante Server-Sent Events y Redis Streams.
- Cancelación, reintento y limpieza de trabajos.
- Conversión MP3 mediante yt-dlp, Deno y FFmpeg.
- Descarga automática y eliminación del artefacto después de entregarlo.
- Métricas, logs estructurados y trazas con Prometheus, Grafana, Loki, Tempo y OpenTelemetry.

## Arquitectura resumida

```text
Astro + TypeScript
        |
        | REST / SSE
        v
Spring Boot API -----> PostgreSQL
        |                  ^
        | Outbox           | estado durable
        v                  |
     RabbitMQ -------> Spring Boot Worker
                           |       |
                           |       +--> yt-dlp + Deno + FFmpeg
                           +----------> almacenamiento S3 compatible

Worker ---- progreso ----> Redis Streams ----> API SSE
```

El diseño completo, sus límites y decisiones están en [`ARCHITECTURE.md`](ARCHITECTURE.md) y [`docs/`](docs/).

## Tecnologías

### Producto

- Java 17, Spring Boot 3.5 y Gradle 8.
- Astro, TypeScript estricto y Tailwind CSS 4.
- PostgreSQL, RabbitMQ, Redis y almacenamiento S3 compatible.
- Docker Engine y Docker Compose.
- yt-dlp, Deno y FFmpeg.

### Calidad

- JUnit 5, Mockito, Spring MockMvc y pruebas de integración JDBC.
- Vitest para componentes, stores, mappers y clientes HTTP/SSE.
- Karate Framework para regresión black-box de APIs.
- Selenium WebDriver para pruebas de humo web.
- Postman y OpenAPI 3.1 para verificación manual y contractual.
- k6 y scripts controlados para evidencia local de rendimiento.

## Estructura pública

```text
musicwave/
├── backend/            Backend multiproyecto y sus pruebas
├── frontend/           Interfaz Astro y pruebas Vitest
├── infrastructure/     Docker Compose y observabilidad
├── qa/                 Automatización Karate + Selenium
├── docs/
│   ├── openapi/        Contrato REST congelado
│   ├── operations/     Runbooks de operación e incidentes
│   ├── qa/             Evidencia del proceso QA
│   └── testing/        Postman y pruebas manuales
├── scripts/            Verificación del contrato OpenAPI
└── .github/            Integración continua y plantilla de defectos
```

## Requisitos locales

- Docker Engine con Docker Compose v2.
- Java 17.
- Node.js 22 o superior y npm 10 o superior.
- Chromium/Chrome y su WebDriver para la suite Selenium local.

## Ejecución local

Desde la raíz del repositorio:

```bash
cp .env.example .env
MUSICWAVE_POSTGRES_PORT=55432 docker compose -f infrastructure/compose.yaml up -d --build
docker compose -f infrastructure/compose.yaml ps
```

Después, en otra terminal:

```bash
cd frontend
cp .env.example .env
npm ci
npm run dev
```

- Frontend: `http://localhost:4321`
- API: `http://localhost:8080`
- OpenAPI generado: `http://localhost:8080/v3/api-docs`
- Grafana: `http://localhost:3000`

Para detener el entorno conservando los contenedores y volúmenes:

```bash
docker compose -f infrastructure/compose.yaml stop
```

## Ejecución de pruebas

### Backend

```bash
cd backend
./gradlew test --no-daemon
```

Las pruebas JDBC que necesitan PostgreSQL se habilitan cuando `MUSICWAVE_TEST_DB_URL` está configurado. Una ejecución sin esa variable las reporta como omitidas de forma explícita.

### Frontend

```bash
cd frontend
npm ci
npm run check
npm run lint
npm run format
npm test
npm run build
```

### APIs con Karate

Con el stack Docker saludable:

```bash
./backend/gradlew -p qa apiTest --no-daemon
```

### Web con Selenium

Con el frontend ejecutándose en `http://localhost:4321`:

```bash
./backend/gradlew -p qa uiTest --no-daemon
```

La suite completa acepta URLs alternativas mediante `API_BASE_URL` y `UI_BASE_URL`:

```bash
API_BASE_URL=http://localhost:8080 UI_BASE_URL=http://localhost:4321 \
  ./backend/gradlew -p qa test --no-daemon
```

## Pruebas manuales de API

1. Importar en Postman:
   - [`MusicWave.postman_collection.json`](docs/testing/postman/MusicWave.postman_collection.json)
   - [`MusicWave.local.postman_environment.json`](docs/testing/postman/MusicWave.local.postman_environment.json)
2. Seguir el orden de [`MANUAL_API_TESTING.md`](docs/testing/MANUAL_API_TESTING.md).
3. Comparar rutas y DTO con [`musicwave-api-v1.json`](docs/openapi/musicwave-api-v1.json).

La colección cubre las 17 operaciones estables, estados HTTP, respuestas JSON, idempotencia, errores, SSE, artefactos y limpieza final.

## Documentación QA

- [Portafolio y mapa de evidencias](docs/qa/QA_PORTFOLIO.md)
- [Plan de pruebas](docs/qa/TEST_PLAN.md)
- [Matriz de trazabilidad](docs/qa/REQUIREMENTS_TRACEABILITY.md)
- [Casos de prueba](docs/qa/TEST_CASES.md)
- [Ciclo de vida de defectos](docs/qa/DEFECT_LIFECYCLE.md)
- [Defecto real: restricciones del cliente web de YouTube](docs/qa/DEFECT_REPORT_YOUTUBE_PLAYER_CLIENT.md)
- [Reporte de ejecución](docs/qa/TEST_EXECUTION_REPORT.md)

## Limitaciones y uso responsable

- Docker Compose representa un entorno local o de un solo host, no alta disponibilidad multi-host.
- Los resultados de rendimiento corresponden únicamente a la máquina donde fueron medidos.
- YouTube y otros proveedores externos pueden cambiar sus restricciones sin aviso.
- El modo de seguridad `local` es solo para desarrollo; un despliegue público requiere OAuth 2.0/JWT y secretos externos.
- El proyecto debe utilizarse únicamente con contenido propio, autorizado o permitido por la legislación y términos aplicables.
- Los MP3 son temporales: se eliminan después de la entrega o al vencer su TTL.

## Tipo de proyecto

Proyecto independiente desarrollado con fines de aprendizaje, práctica de arquitectura full-stack y demostración de habilidades QA. No se presenta como trabajo realizado para un cliente ni como experiencia laboral dependiente.

## Licencia

Código publicado bajo la [licencia MIT](LICENSE).
