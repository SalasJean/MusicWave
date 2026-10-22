# MusicWave Frontend

Frontend oficial de **MusicWave**, desarrollado con **Astro**, **TypeScript estricto** y **Tailwind CSS 4**.

Diseñado bajo una arquitectura de capas orientada a características (**Feature-Driven Architecture**) y una política de cero dependencias externas de librerías visuales (cero React, Vue, Svelte, Preact; 100% nativo Astro + Web Standards).

---

## Requisitos Previos
- **Node.js**: >= 20.x (recomendado Node 22 o 24 LTS)
- **npm**: >= 10.x
- **Backend MusicWave**: En ejecución en `http://localhost:8080` (orquestado vía Docker Compose en `infrastructure/compose.yaml`)

---

## Puesta en Marcha Local

### 1. Instalación de dependencias
```bash
cd frontend
npm install
```

### 2. Variables de entorno
Copiar el archivo de plantilla `.env.example` a `.env`:
```bash
cp .env.example .env
```

Configuración por defecto:
```ini
PUBLIC_API_BASE_PATH=/api/v1
PUBLIC_SECURITY_MODE=local
PUBLIC_APP_NAME=MusicWave
```

> **Importante**: No modifique la ruta relativa hacia `http://localhost:8080` en variables públicas. El archivo `astro.config.mjs` contiene el proxy de desarrollo de Vite que redirige `/api` de manera transparente y segura hacia el backend.

### 3. Ejecución en modo desarrollo
```bash
npm run dev
```
La aplicación estará disponible de inmediato en `http://localhost:4321`.

### 4. Construcción estática para producción
```bash
npm run build
```
Genera los archivos optimizados en el directorio `dist/` en modo estático.

### 5. Verificación de calidad y pruebas
```bash
# Comprobación de tipos de Astro y TypeScript
npm run check

# Linter de código
npm run lint

# Verificación de formateo con Prettier
npm run format

# Ejecución de la suite completa de pruebas unitarias y de integración (19 suites, 139 tests)
npm test
```

---

## Arquitectura de Capas

El proyecto sigue una dirección estricta de dependencias:
```text
pages -> features -> shared / generated
```

```text
src/
├── layouts/               # Layouts base (AppLayout con skip-link WCAG AA y header sticky)
├── pages/                 # Rutas Astro de alto nivel (/, /settings, /ui-catalog)
├── features/              # Módulos encapsulados por dominio
│   ├── downloads/         # Dominio de descargas y cola distribuida
│   │   ├── api/           # Adapters HTTP (downloadsApi, artifactApi)
│   │   ├── components/    # Componentes visuales (AddUrlForm, BulkModal, DownloadCard,
│   │   │                  # DownloadList, DownloadWorkspace, ImportTxtModal, Pagination, QueueControls)
│   │   ├── lib/           # artifactManager (ADR-0006), formatters
│   │   ├── model/         # Mappers, view models y estados tipados
│   │   └── stores/        # downloadStore (EventTarget reactivo)
│   └── settings/          # Dominio de ajustes de usuario (concurrencia y calidad)
│       ├── api/           # settingsApi
│       ├── components/    # SettingsForm
│       ├── model/         # settingsModel (validación de rangos 1..10 y calidades MP3)
│       └── stores/        # settingsStore
├── shared/                # Bloques compartidos de la aplicación
│   ├── api/               # httpClient (fetch nativo), problemDetails (RFC 7807), sseClient, sseParser
│   ├── components/ui/     # Sistema visual nativo accesible (Alert, Badge, Button, EmptyState,
│   │                      # IconButton, Input, Modal, ProgressBar, Select, Skeleton, Spinner, Textarea, Toast)
│   ├── config/            # env y validación de variables de entorno
│   └── utils/             # Generación de UUIDs y claves de idempotencia
├── generated/             # Tipos TypeScript generados automáticamente desde OpenAPI (no editar manualmente)
└── styles/                # Tailwind CSS 4 y variables de diseño CSS (@theme)
```

---

## Matriz de Trazabilidad de Endpoints

Todos los endpoints oficiales expuestos por el backend de Spring Boot se encuentran mapeados y testeados al 100%:

| Endpoint HTTP | Adapter Frontend | Consumidor / Store | Componente Visual | Suite de Pruebas |
| :--- | :--- | :--- | :--- | :--- |
| `POST /api/v1/downloads` | `downloadsApi.createDownload` | `downloadStore.addSingle` | `AddUrlForm.astro` | `downloadsApi.test.ts`, `downloadStore.test.ts`, `IntegrationFlow.test.ts` |
| `POST /api/v1/downloads/bulk` | `downloadsApi.createBulkDownloads` | `downloadStore.addBulk` | `BulkModal.astro` | `downloadsApi.test.ts`, `downloadStore.test.ts`, `IntegrationFlow.test.ts` |
| `POST /api/v1/downloads/import` | `downloadsApi.importDownloadsText` | `downloadStore.importTxt` | `ImportTxtModal.astro` | `downloadsApi.test.ts`, `downloadStore.test.ts` |
| `GET /api/v1/downloads` | `downloadsApi.getDownloads` | `downloadStore.loadPage` | `DownloadList.astro` | `downloadsApi.test.ts`, `downloadStore.test.ts` |
| `POST /api/v1/downloads/{id}/cancel` | `downloadsApi.cancelDownload` | `downloadStore.cancelDownload` | `DownloadCard.astro`, `DownloadList.astro` | `downloadsApi.test.ts`, `downloadStore.test.ts`, `DownloadCard.test.ts` |
| `POST /api/v1/downloads/{id}/retry` | `downloadsApi.retryDownload` | `downloadStore.retryDownload` | `DownloadCard.astro`, `DownloadList.astro` | `downloadsApi.test.ts`, `downloadStore.test.ts`, `IntegrationFlow.test.ts` |
| `GET /api/v1/downloads/{id}/artifact` | `artifactApi.getArtifactBlob` | `artifactManager.retrieveArtifact`, `downloadStore.downloadArtifact` | `DownloadCard.astro`, `DownloadList.astro` | `artifactApi.test.ts`, `artifactManager.test.ts`, `downloadStore.test.ts`, `IntegrationFlow.test.ts` |
| `POST /api/v1/queue/start` | `downloadsApi.startQueue` | `downloadStore.startQueue` | `QueueControls.astro` | `downloadsApi.test.ts`, `downloadStore.test.ts`, `IntegrationFlow.test.ts` |
| `POST /api/v1/queue/cancel` | `downloadsApi.cancelQueue` | `downloadStore.cancelQueue` | `QueueControls.astro` | `downloadsApi.test.ts`, `downloadStore.test.ts` |
| `DELETE /api/v1/queue/completed` | `downloadsApi.clearCompletedQueue` | `downloadStore.clearCompleted` | `QueueControls.astro` | `downloadsApi.test.ts`, `downloadStore.test.ts` |
| `DELETE /api/v1/queue` | `downloadsApi.clearQueue` | `downloadStore.clearAll` | `QueueControls.astro` | `downloadsApi.test.ts`, `downloadStore.test.ts` |
| `GET /api/v1/events` | `sseClient.ts` | `downloadStore.applyRealtimeUpdate`, `handleStreamReset` | `QueueControls.astro` (badge en vivo), `DownloadList.astro` | `sseParser.test.ts`, `sseClient.test.ts`, `downloadStore.test.ts` |
| `GET /api/v1/settings` | `settingsApi.getSettings` | `settingsStore.loadSettings` | `SettingsForm.astro` | `settingsApi.test.ts`, `settingsStore.test.ts`, `IntegrationFlow.test.ts` |
| `PUT /api/v1/settings` | `settingsApi.updateSettings` | `settingsStore.saveSettings` | `SettingsForm.astro` | `settingsApi.test.ts`, `settingsStore.test.ts`, `settingsModel.test.ts`, `IntegrationFlow.test.ts` |

---

## Invariantes y Decisiones de Arquitectura

1. **ADR-0006 (One-Shot Delivery & No Persistent Storage)**:
   - Los archivos de audio MP3 son artefactos efímeros de **un solo uso**.
   - Tras el streaming HTTP 200, el backend purga inmediatamente el objeto en S3/MinIO y su registro en base de datos.
   - Si el backend responde 410 (`ARTIFACT_ALREADY_DOWNLOADED` o `ARTIFACT_EXPIRED`), la aplicación prohíbe reintentos ciegos y refleja el estado final.
   - **Invariante Crítico**: El audio binario **NUNCA** se almacena ni se cachea en `localStorage`, `sessionStorage`, `IndexedDB`, `Cache API` ni Service Workers. La entrega al disco se realiza exclusivamente mediante Object URLs efímeros (`URL.createObjectURL`) revocados inmediatamente tras la descarga.
2. **Streaming SSE Resiliente (W3C)**:
   - El cursor `Last-Event-ID` se conserva **únicamente en memoria**, jamás en almacenamiento persistente del navegador.
   - Reconexión con backoff exponencial y jitter aleatorio (+/- 20%).
   - Al recibir `stream-reset`, purga el cursor y reconcilia el estado completo mediante consulta autoritativa a `GET /api/v1/downloads`.
3. **Accesibilidad WCAG AA**:
   - Enlace `skip-link` (*"Saltar al contenido principal"*) accesible con tabulador en todas las páginas.
   - Atajo global `Alt+N` para enfocar la caja de ingreso de URLs.
   - Diálogos modales nativos `<dialog>` con cierre mediante tecla `Escape`, trampa de foco y click en backdrop.
   - Contraste de colores verificado sobre fondos oscuros y soporte de `prefers-reduced-motion`.
4. **Separación de responsabilidades**:
   - El frontend consume el contrato público del backend sin importar clases ni detalles internos de Spring Boot.
