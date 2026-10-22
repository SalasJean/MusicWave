# Gestión del ciclo de vida de defectos

## Objetivo

Registrar defectos de forma reproducible, separar fallos del producto de problemas ambientales y conservar evidencia suficiente para verificar la corrección.

## Flujo propuesto

```text
Nuevo -> En análisis -> Confirmado -> En corrección -> Listo para QA
                                             |              |
                                             v              v
                                         Rechazado      Verificado -> Cerrado
                                                            |
                                                            v
                                                         Reabierto
```

## Estados

| Estado | Significado |
|---|---|
| Nuevo | Reporte creado, todavía sin triage |
| En análisis | Se está reproduciendo y delimitando causa/alcance |
| Confirmado | Existe evidencia repetible y se acepta como defecto |
| En corrección | Se está implementando una solución |
| Listo para QA | Cambio disponible para retest |
| Verificado | El caso original y la regresión relacionada pasan |
| Cerrado | Se acepta la solución y la evidencia final |
| Reabierto | El problema continúa o reapareció |
| Rechazado | Duplicado, no reproducible, comportamiento esperado o problema externo; exige motivo |

## Severidad

| Nivel | Criterio |
|---|---|
| Crítica | Pérdida de datos, vulnerabilidad grave o sistema principal totalmente inutilizable sin alternativa |
| Alta | Funcionalidad principal bloqueada para un conjunto relevante de casos |
| Media | Flujo secundario afectado o existe una alternativa razonable |
| Baja | Problema visual, de texto o impacto reducido |

La prioridad representa el orden de atención y puede diferir de la severidad. La determina el equipo según impacto, frecuencia, riesgo y fecha de entrega.

## Información obligatoria

- Título concreto, sin describir la solución como si fuera la causa.
- Entorno, versión o commit.
- Precondiciones.
- Pasos numerados.
- Resultado esperado.
- Resultado actual.
- Frecuencia de reproducción.
- Severidad propuesta y justificación.
- Estado HTTP, respuesta, `X-Request-ID` y `jobId` cuando aplique.
- Evidencia sanitizada.
- Alcance de regresión después de la corrección.

## Reglas de triage

1. Reproducir con el menor caso posible.
2. Confirmar salud del entorno antes de atribuir la falla al producto.
3. Comparar contra requerimiento, criterio de aceptación u OpenAPI.
4. Diferenciar frontend, API, worker, infraestructura y proveedor externo.
5. Registrar hipótesis como hipótesis, no como causa confirmada.
6. No adjuntar credenciales, cookies, tokens, archivos personales ni contenido multimedia.

## Criterio de cierre

Un defecto se cierra solamente cuando:

- El caso original pasa en el entorno acordado.
- Se ejecutan casos relacionados de regresión.
- No se introduce un cambio no documentado en el contrato.
- La evidencia y el riesgo residual quedan registrados.

La plantilla pública está en [`.github/ISSUE_TEMPLATE/bug_report.yml`](../../.github/ISSUE_TEMPLATE/bug_report.yml).
