# Casos de prueba funcionales y de regresión

## Convenciones

- Prioridad: P0 crítica, P1 alta, P2 media.
- Tipo: funcional, negativo, límite, integración o humo.
- Precondición común: servicios requeridos saludables y perfil local activo.

| ID | Tipo | Prioridad | Escenario | Datos / pasos resumidos | Resultado esperado |
|---|---|---:|---|---|---|
| CP-API-001 | Humo | P0 | Salud de API | `GET /actuator/health` | 200, `status=UP` |
| CP-API-002 | Integración | P0 | Herramientas del worker | `GET /api/v1/tools/status` | 200, `status=READY`, worker visible |
| CP-FUN-001 | Funcional | P0 | Crear URL individual | POST con HTTPS e idempotencia nueva | 201, un trabajo `PENDING` |
| CP-FUN-002 | Regresión | P1 | Replay idempotente | Repetir clave y payload | 200, `replayed=true`, mismos IDs |
| CP-NEG-001 | Negativo | P1 | Conflicto de idempotencia | Misma clave y URL distinta | 409 `IDEMPOTENCY_KEY_REUSED` |
| CP-NEG-002 | Negativo | P0 | Esquema de URL inválido | Enviar `ftp://...` | 400 Problem Details, código estable |
| CP-SEC-001 | Negativo | P0 | Destino privado | URL loopback/privada | 400 `URL_DESTINATION_NOT_ALLOWED` |
| CP-FUN-003 | Funcional | P1 | Creación masiva parcial | Una URL válida y una inválida | Válida creada; inválida en `rejected` |
| CP-FUN-004 | Funcional | P1 | Importación TXT | UTF-8, vacíos, inválida y BOM | Posiciones conservadas y rechazo parcial |
| CP-LIM-001 | Límite | P1 | TXT demasiado grande | Más de 256 KiB | 413 `IMPORT_TOO_LARGE` |
| CP-FUN-005 | Funcional | P0 | Inicio de cola | Crear pendientes y ejecutar start | 202 si encola; repetición sin trabajo devuelve 200 |
| CP-LIM-002 | Límite | P0 | Concurrencia | Guardar 1 y 10; intentar 0 y 11 | Límites aceptados; fuera de rango devuelve 400 |
| CP-FUN-006 | Funcional | P0 | Cancelar pendiente | Crear y cancelar antes de start | 200, estado `CANCELLED` |
| CP-FUN-007 | Funcional | P1 | Reintentar fallo | Trabajo `FAILED`, luego retry | 200, vuelve a `PENDING`, incrementa contador |
| CP-NEG-003 | Negativo | P1 | Retry en estado incorrecto | Retry de `PENDING` | 409 `DOWNLOAD_NOT_RETRYABLE` |
| CP-SSE-001 | Integración | P1 | Flujo SSE | Conectar antes de iniciar cola | Eventos ordenados y heartbeat |
| CP-SSE-002 | Regresión | P1 | Recuperación SSE | Reconectar con `Last-Event-ID` | Continúa sin duplicar el evento confirmado |
| CP-ART-001 | Funcional | P0 | Entrega de MP3 | Obtener artefacto completado | 200 `audio/mpeg`, attachment y `no-store` |
| CP-ART-002 | Negativo | P0 | Segunda descarga | Repetir después de consumo | 410 `ARTIFACT_ALREADY_DOWNLOADED` |
| CP-ART-003 | Negativo | P1 | Artefacto aún no listo | Solicitarlo en `PENDING` | 409 `ARTIFACT_NOT_READY` |
| CP-UI-001 | Humo | P0 | Bandeja web | Abrir `/` en Chromium | Título, navegación y formulario visibles |
| CP-UI-002 | Humo/límite | P1 | Ajustes web | Abrir `/settings` | Control 1–10 y siete calidades visibles |
| CP-OPS-001 | Regresión | P1 | Compatibilidad YouTube | URL autorizada afectada por cliente web | Android-first resuelve; flujo completa o error queda clasificado |
| CP-CLEAN-001 | Funcional | P1 | Limpieza final | Eliminar cola visible | 200 y lista final vacía |

## Evidencia mínima por ejecución manual

- ID del caso y fecha/hora UTC.
- Entorno y versión/commit.
- Resultado esperado y real.
- Estado HTTP y cuerpo JSON cuando aplique.
- `X-Request-ID` y `jobId` sin información sensible.
- Captura o log sanitizado para fallos.
- Estado final: Passed, Failed, Blocked o Not Run.
