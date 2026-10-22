# Defecto QA-001: URLs musicales fallan con desafío anti-bot del cliente web

## Resumen

| Campo | Valor |
|---|---|
| ID | QA-001 |
| Estado final | Verificado y cerrado |
| Severidad | Alta |
| Prioridad | Alta |
| Componente observado | Worker / integración yt-dlp–YouTube |
| Fecha de investigación | 2026-10-14 |

## Descripción

Algunas pistas y mezclas musicales comerciales no completaban la descarga. La interfaz mostraba `YT_DLP_EXIT_CODE`, mientras el worker recibía `HTTP 429`, `LOGIN_REQUIRED` y la solicitud de confirmar que el cliente no era un bot.

## Precondiciones

- Stack local saludable.
- API y worker activos.
- yt-dlp y FFmpeg reportados como disponibles.
- URL pública compatible con YouTube.

## Pasos para reproducir

1. Agregar una URL musical afectada a la bandeja.
2. Iniciar la cola.
3. Esperar el resultado del worker.
4. Revisar el estado persistido y el log verbose de yt-dlp.

## Resultado esperado

El worker obtiene el audio, lo convierte a MP3 y publica el estado `COMPLETED`; o devuelve un error específico que permita distinguir una restricción externa.

## Resultado observado

- Estado `FAILED`.
- Código genérico `YT_DLP_EXIT_CODE` en la primera versión.
- Log con `HTTP Error 429: Too Many Requests`.
- `Sign in to confirm you're not a bot` y `LOGIN_REQUIRED`.
- Advertencias relacionadas con streaming SABR en el cliente web.

## Investigación

1. Se confirmó que el frontend presentaba correctamente el error recibido; no originaba la falla.
2. Se verificó que el backend, la cola y el worker estaban saludables.
3. Se descartó una versión antigua: yt-dlp ya estaba actualizado.
4. El log mostró ausencia de runtime JavaScript. Se añadió Deno y se verificó `JS runtimes: deno-2.9.7`, pero el cliente web continuó fallando.
5. Inicialmente se consideró un bloqueo general de IP. Esa hipótesis no quedó confirmada.
6. Se ejecutó una prueba diferencial con `youtube:player_client=android,web`.
7. La misma clase de URL resolvió correctamente al priorizar Android.

## Causa confirmada

La ruta del cliente web utilizada por yt-dlp estaba sujeta a restricciones de sesión/anti-bot para los casos probados. No era un defecto del frontend ni se demostró un bloqueo total de la IP.

## Corrección

- Configuración persistente en `infrastructure/yt-dlp.conf`:

  ```text
  --extractor-args "youtube:player_client=android,web"
  ```

- Montaje read-only en el worker: `./yt-dlp.conf:/etc/yt-dlp.conf:ro`.
- Deno fijado y verificado para desafíos JavaScript.
- Clasificación estable de `YOUTUBE_RATE_LIMITED` y `YOUTUBE_AUTH_REQUIRED`.

## Retest

- Worker recreado y saludable.
- Resolución de la URL de prueba con exit code `0`.
- La mezcla de Avicii utilizada como fixture completó 100%, conversión MP3 y ciclo final en aproximadamente cuatro segundos.
- Suite frontend: 19 archivos y 139 pruebas aprobadas.

## Regresión y riesgo residual

- Confirmar que `/etc/yt-dlp.conf` esté montado después de cada despliegue.
- Ejecutar primero un probe `--simulate` y después un caso end-to-end autorizado.
- Si Android y web fallan, investigar PO Tokens, autenticación, rate limit o reputación de egreso sin asumir la causa.
- La solución es una estrategia de compatibilidad comprobada, no una garantía permanente frente a cambios de YouTube.

El procedimiento operativo completo está en [`docs/operations/YOUTUBE.md`](../operations/YOUTUBE.md).
