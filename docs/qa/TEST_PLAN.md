# Plan de pruebas

## 1. Identificación

- Proyecto: MusicWave.
- Tipo: proyecto independiente full-stack.
- Versión evaluada: `0.1.0-SNAPSHOT`.
- Responsable: autor del proyecto en rol de desarrollo y QA junior.
- Referencias: `API_CONTRACT.md`, OpenAPI 3.1 y criterios registrados en esta carpeta.

## 2. Objetivo

Comprobar que las funcionalidades críticas de ingestión, cola, ejecución, seguimiento y entrega se comporten según el contrato; detectar regresiones; y producir evidencia reproducible para defectos del producto o dependencias externas.

## 3. Alcance incluido

- Creación individual, masiva y mediante TXT.
- Validación de entradas y errores Problem Details.
- Idempotencia y consultas paginadas.
- Inicio, cancelación, reintento y limpieza de la cola.
- Estados, progreso y recuperación SSE.
- Ajustes de concurrencia y calidad.
- Entrega MP3 temporal de un solo uso.
- Salud, disponibilidad de herramientas y observabilidad básica.
- Controles de seguridad verificables en desarrollo: propiedad, URL guard, cuotas y rate limit mediante pruebas de componentes/integración.
- Accesibilidad básica y navegación crítica del frontend.

## 4. Fuera de alcance

- Certificación de rendimiento en un VPS todavía no medido.
- Pruebas de penetración completas.
- Compatibilidad exhaustiva con todos los navegadores y dispositivos.
- Automatización Appium sin una aplicación móvil objetivo.
- Garantizar disponibilidad o compatibilidad permanente de YouTube.
- Validar derechos sobre contenido introducido por terceros.

## 5. Estrategia

| Nivel | Técnica | Herramienta | Objetivo |
|---|---|---|---|
| Dominio/aplicación | Unitarias, particiones y estados | JUnit 5 | Reglas de negocio y transiciones |
| Adaptadores | Integración y componentes | JUnit, MockMvc, PostgreSQL | Persistencia, HTTP, Redis, procesos y almacenamiento |
| Frontend | Unitarias e integración de módulos | Vitest | API clients, stores, mappers y componentes |
| API | Caja negra y regresión | Karate, Postman | Contrato observable, HTTP y JSON |
| Web | Humo de caja negra | Selenium | Navegación y controles principales |
| Contrato | Comparación de esquema | OpenAPI + script | Evitar cambios no documentados |
| Rendimiento | Carga controlada | k6 | Evidencia local, no promesa productiva |

Se priorizan combinaciones representativas mediante partición de equivalencia y valores límite. Ejemplos: concurrencia `1`, `10`, `0` y `11`; URL HTTP válida frente a esquema no permitido; artefacto disponible frente a pendiente, consumido o vencido.

## 6. Entorno

- Linux con Docker Engine y Compose v2.
- Java 17 y Gradle 8.14.5.
- Node.js 22 y npm 10.
- Chromium/Chrome para Selenium.
- Servicios: API, worker, PostgreSQL, RabbitMQ, Redis y almacenamiento S3 compatible.
- Seguridad local: propietario fijo de desarrollo; las pruebas JWT se mantienen en backend y requieren configuración separada para una pasada black-box productiva.

## 7. Datos de prueba

- UUID e `Idempotency-Key` únicos por ejecución.
- `example.com` para trabajos que no deben ejecutarse contra un proveedor multimedia real.
- URLs autorizadas y controladas solamente en pruebas end-to-end manuales.
- Archivos TXT pequeños, UTF-8, con líneas válidas, inválidas y vacías.
- Nunca usar tokens, cookies, credenciales reales ni información personal como fixture.

## 8. Criterios de entrada

- Compose valida con `docker compose ... config --quiet`.
- API y worker están `healthy`.
- `/actuator/health` responde `UP`.
- `/api/v1/tools/status` responde `READY` para pruebas de ejecución.
- Contrato OpenAPI disponible.
- Dependencias backend y frontend instaladas.

## 9. Criterios de salida

- Gates backend y frontend finalizan sin fallos.
- Karate y Selenium pasan en un entorno limpio.
- Las 17 operaciones estables tienen cobertura manual documentada.
- No existen defectos abiertos de severidad crítica o alta sin decisión explícita.
- Los defectos conocidos tienen estado, evidencia y riesgo residual.
- No se detectan secretos, binarios multimedia ni datos personales en la versión pública.

## 10. Suspensión y reanudación

La ejecución se suspende si el entorno no está saludable, el contrato no coincide, faltan herramientas o un proveedor externo impide separar un fallo ambiental de un defecto. Se reanuda después de restaurar el entorno y registrar la causa; no se oculta la ejecución fallida.

## 11. Gestión de defectos

Los defectos siguen el flujo definido en [`DEFECT_LIFECYCLE.md`](DEFECT_LIFECYCLE.md). Como mínimo se registra entorno, precondición, pasos, esperado, actual, severidad, evidencia y resultado de regresión.

## 12. Riesgos

| Riesgo | Mitigación |
|---|---|
| Cambios del proveedor multimedia | Runtime fijado, logs, prueba por cliente y runbook específico |
| Pruebas que dejan datos | IDs únicos y limpieza final; CI usa volúmenes efímeros |
| Falso positivo por entorno caído | Preflight de salud antes de Karate/Selenium |
| Exposición de secretos | `.gitignore`, archivos example y escaneo previo a publicación |
| Afirmaciones de capacidad no medidas | Resultados locales identificados como tales |
