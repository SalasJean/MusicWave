# Portafolio QA de MusicWave

## Presentación

MusicWave es un proyecto independiente utilizado para practicar el ciclo completo de calidad sobre una aplicación full-stack real. La evidencia combina pruebas manuales, automatización, contrato de API, observabilidad y documentación de defectos.

El objetivo no es afirmar experiencia senior ni experiencia con un cliente. El objetivo es demostrar fundamentos aplicados, capacidad de análisis y disposición para aprender dentro de un equipo QA.

## Relación con las competencias QA

| Competencia | Aplicación práctica | Evidencia |
|---|---|---|
| Análisis de requerimientos | Los comportamientos funcionales y no funcionales se transforman en criterios verificables | [`REQUIREMENTS_TRACEABILITY.md`](REQUIREMENTS_TRACEABILITY.md) |
| Casos funcionales | Se validan entradas individuales, lotes, TXT, cola, ajustes, artefactos y limpieza | [`TEST_CASES.md`](TEST_CASES.md) |
| Regresión | Backend, frontend, API black-box y humo web poseen suites repetibles | `backend/**/src/test`, `frontend/src/**/*.test.ts`, `qa/` |
| Caja negra | Karate y Postman consumen únicamente HTTP, sin acceder a clases internas | [`qa`](../../qa/), [`postman`](../testing/postman/) |
| API e integración | Se validan 17 operaciones REST/SSE, estados HTTP, JSON, idempotencia y errores | [`MANUAL_API_TESTING.md`](../testing/MANUAL_API_TESTING.md) |
| Swagger/OpenAPI | El contrato OpenAPI 3.1 está congelado y se compara con la API ejecutándose | [`musicwave-api-v1.json`](../openapi/musicwave-api-v1.json), [`verify-openapi.sh`](../../scripts/verify-openapi.sh) |
| Bug tracking | Existe un flujo de estados, campos obligatorios y un defecto real documentado | [`DEFECT_LIFECYCLE.md`](DEFECT_LIFECYCLE.md), [`DEFECT_REPORT_YOUTUBE_PLAYER_CLIENT.md`](DEFECT_REPORT_YOUTUBE_PLAYER_CLIENT.md) |
| Selenium | Dos pruebas de humo comprueban navegación, formulario principal y ajustes | [`MusicWaveUiTest.java`](../../qa/src/test/java/com/salas/musicwave/qa/ui/MusicWaveUiTest.java) |
| Karate | La regresión valida salud, herramientas, creación, replay, conflicto, consulta, artefacto, cancelación y URL inválida | [`features`](../../qa/src/test/resources/features/) |
| CI | Cada push o pull request ejecuta gates backend y frontend; luego levanta el entorno para pruebas black-box | [`quality.yml`](../../.github/workflows/quality.yml) |

## Ruta rápida para revisión

1. Leer el [`README`](../../README.md) para comprender producto, arquitectura y comandos.
2. Revisar el [`plan de pruebas`](TEST_PLAN.md) y la [`matriz`](REQUIREMENTS_TRACEABILITY.md).
3. Abrir los escenarios Karate y las pruebas Selenium dentro de `qa/`.
4. Revisar el [defecto real de YouTube](DEFECT_REPORT_YOUTUBE_PLAYER_CLIENT.md).
5. Consultar el [reporte de ejecución](TEST_EXECUTION_REPORT.md) para diferenciar evidencia ejecutada de trabajo pendiente.

## Alcance honesto de automatización

- **Karate:** aplicado a APIs REST de MusicWave.
- **Selenium:** aplicado a navegación y controles críticos del frontend web.
- **Appium:** conocimiento en formación. No se incluye una suite porque MusicWave no posee una aplicación Android/iOS nativa ni un requisito móvil aprobado. Automatizar un objetivo inexistente produciría evidencia artificial.

## Aprendizajes principales

- Separar una falla del proveedor externo de un defecto del frontend o backend.
- Diseñar pruebas repetibles que no dependan de descargar contenido real.
- Conservar identificadores de correlación, resultados esperados y evidencia sanitizada.
- Evitar que una prueba positiva o una hipótesis inicial se conviertan en una conclusión general sin comparación.
- Expresar límites técnicos y resultados medidos sin exagerar capacidad productiva.
