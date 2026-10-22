# Matriz de trazabilidad de requerimientos

| ID | Requerimiento / criterio de aceptación | Prueba manual | Evidencia automatizada | Estado |
|---|---|---|---|---|
| RF-01 | Una URL HTTP(S) válida crea un trabajo `PENDING`; una inválida produce error estable | Postman 02 y 06 | `downloads-black-box.feature`, `DownloadControllerTest` | Cubierto |
| RF-02 | Un lote acepta entradas válidas y reporta inválidas sin perder las válidas | Postman 02 | `CreateDownloadsServiceTest`, `downloadsApi.test.ts` | Cubierto |
| RF-03 | Un TXT UTF-8 conserva posiciones, ignora vacíos y limita tamaño | Postman 02 | `DownloadControllerTest`, `CreateDownloadsServiceTest` | Cubierto |
| RF-04 | El mismo `Idempotency-Key` y payload retorna el resultado original; otro payload retorna 409 | Postman 02 | `downloads-black-box.feature`, pruebas de aplicación/API | Cubierto |
| RF-05 | La cola inicia pendientes, respeta concurrencia 1–10 y no duplica dispatch | Postman 04 | `QueueServicesTest`, `DownloadExecutionDispatcherTest`, frontend store | Cubierto |
| RF-06 | Una cancelación es idempotente y termina procesos/limpia parciales cuando corresponde | Postman 04 y 05 | `DownloadControlServiceTest`, `ExecuteDownloadServiceTest` | Cubierto |
| RF-07 | Solo un trabajo `FAILED` puede volver a `PENDING` mediante retry | Postman 04 y 06 | `DownloadControlServiceTest`, frontend integration | Cubierto |
| RF-08 | El progreso SSE conserva orden, permite replay y solicita REST tras `stream-reset` | Postman 03 | `DownloadEventControllerTest`, `sseParser.test.ts`, `sseClient.test.ts` | Cubierto |
| RF-09 | Ajustes aceptan concurrencia 1–10 y calidades permitidas; rechazan valores fuera del rango | Postman 01 y 06 | `UserSettingsServiceTest`, `SettingsForm.test.ts`, Selenium | Cubierto |
| RF-10 | Un MP3 completado se entrega una sola vez y luego se elimina | Postman 04 | `GetArtifactServiceTest`, `ArtifactControllerTest`, `artifactManager.test.ts` | Cubierto |
| RF-11 | Limpiar la bandeja oculta trabajos y hace elegibles los artefactos para borrado | Postman 05 | pruebas de cola, `QueueControls.test.ts` | Cubierto |
| RF-12 | La interfaz permite agregar URL, lote/TXT, operar cola, observar estados y abrir ajustes | Recorrido exploratorio | 139 pruebas Vitest y `MusicWaveUiTest` | Cubierto |
| RNF-01 | Las URLs se revalidan contra destinos no públicos y no se acepta propietario desde el cliente | Negativos Postman/JWT controlado | `DnsSourceUrlGuardTest`, `JwtSecurityConfigurationTest` | Cubierto en backend |
| RNF-02 | API y worker exponen salud, métricas y logs correlacionables | Preflight y panel local | pruebas de status/correlación + Karate | Cubierto |
| RNF-03 | Trabajo aceptado persiste frente a redelivery o reinicio | Simulación operativa documentada | outbox/inbox y repositorios de integración | Cubierto |
| RNF-04 | Las rutas y DTO públicos coinciden con OpenAPI 3.1 | Importación/revisión Postman | `scripts/verify-openapi.sh` | Cubierto |

## Convención de estados

- **Cubierto:** existe al menos una evidencia repetible y su criterio está definido.
- **Parcial:** existe verificación, pero falta una capa o entorno relevante.
- **Pendiente:** no existe todavía evidencia suficiente.
- **No aplica:** el requerimiento no pertenece al alcance aprobado.

La matriz no sustituye los resultados de ejecución. El estado de la última pasada está en [`TEST_EXECUTION_REPORT.md`](TEST_EXECUTION_REPORT.md).
