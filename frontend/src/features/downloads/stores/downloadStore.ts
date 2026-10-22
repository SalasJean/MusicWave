import {
  getDownloads,
  createDownload,
  createBulkDownloads,
  importDownloadsText,
  startQueue,
  cancelQueue,
  cancelDownload,
  retryDownload,
  clearCompletedQueue,
  clearQueue,
  type QueueStartResponse,
  type QueueCancelResponse,
  type QueueClearResponse,
  type DownloadControlResponse,
} from '@/features/downloads/api/downloadsApi';
import {
  mapJobDtoToViewModel,
  mapCreateResponseToSummary,
  type DownloadJobViewModel,
  type IngestionResultSummary,
} from '../model/downloadMappers';
import {
  getStatusConfig,
  isJobCancellable,
  isJobRetryable,
  type DownloadStatus,
} from '../model/downloadStatus';
import { formatJobError } from '../lib/formatters';
import {
  retrieveArtifact,
  saveBlobToDisk,
  handleArtifactError,
  type ArtifactStatus,
} from '../lib/artifactManager';
import { generateIdempotencyKey } from '@/shared/utils/id';
import { ApiProblemError, NetworkError } from '@/shared/api/problemDetails';
import type {
  SseConnectionStatus,
  SseDownloadEventData,
  SseStreamResetData,
} from '@/shared/api/sseClient';

export interface DownloadStoreFeedback {
  type: 'success' | 'info' | 'warning' | 'error';
  message: string;
}

export interface DownloadStoreState {
  items: DownloadJobViewModel[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  loading: boolean;
  isOperating: boolean;
  activeJobOperationId: string | null;
  retryAfterSeconds: number | null;
  error: string | null;
  feedback: DownloadStoreFeedback | null;
  lastIngestionSummary: IngestionResultSummary | null;
  sseStatus: SseConnectionStatus;
  artifactStatuses: Record<string, ArtifactStatus>;
}

export class DownloadStore extends EventTarget {
  private autoDispatchedJobs = new Set<string>();

  private state: DownloadStoreState = {
    items: [],
    page: 0,
    size: 20,
    totalElements: 0,
    totalPages: 0,
    loading: false,
    isOperating: false,
    activeJobOperationId: null,
    retryAfterSeconds: null,
    error: null,
    feedback: null,
    lastIngestionSummary: null,
    sseStatus: 'disconnected',
    artifactStatuses: {},
  };

  getState(): DownloadStoreState {
    return { ...this.state };
  }

  private setState(partial: Partial<DownloadStoreState>): void {
    this.state = { ...this.state, ...partial };
    this.dispatchEvent(
      new CustomEvent('stateChange', { detail: this.getState() })
    );
  }

  /**
   * Clears the current feedback notification.
   */
  clearFeedback(): void {
    this.setState({ feedback: null });
  }

  /**
   * Updates SSE connection status in the store.
   */
  setSseStatus(status: SseConnectionStatus): void {
    this.setState({ sseStatus: status });
  }

  /**
   * Applies real-time lifecycle changes received via SSE stream.
   * Updates state item in-place without tearing down un-related items.
   */
  /**
   * Resets the auto-dispatched tracking set (useful for test isolation).
   */
  resetAutoDispatchedJobs(): void {
    this.autoDispatchedJobs.clear();
  }

  /**
   * Downloads an MP3 artifact according to ADR-0006:
   * - One-shot ephemeral delivery.
   * - Never stores blob in localStorage, sessionStorage, IndexedDB, Cache API or Service Workers.
   * - Respects 200, 409 and 410 response codes.
   */
  async downloadArtifact(
    id: string,
    options?: { isAutomatic?: boolean }
  ): Promise<void> {
    const currentStatus = this.state.artifactStatuses[id];
    if (
      currentStatus === 'downloaded' ||
      currentStatus === 'expired' ||
      currentStatus === 'missing' ||
      currentStatus === 'delivering'
    ) {
      return;
    }

    // Set artifact delivering status
    const newStatuses = {
      ...this.state.artifactStatuses,
      [id]: 'delivering' as ArtifactStatus,
    };
    const deliveringItems = this.state.items.map((item) =>
      item.id === id
        ? {
            ...item,
            artifactStatus: 'delivering' as ArtifactStatus,
            canDownloadArtifact: false,
          }
        : item
    );
    this.setState({ artifactStatuses: newStatuses, items: deliveringItems });

    try {
      const result = await retrieveArtifact(id);
      saveBlobToDisk(result.blob, result.filename);

      const downloadedStatuses = {
        ...this.state.artifactStatuses,
        [id]: 'downloaded' as ArtifactStatus,
      };
      const downloadedItems = this.state.items.map((item) =>
        item.id === id
          ? {
              ...item,
              artifactStatus: 'downloaded' as ArtifactStatus,
              canDownloadArtifact: false,
            }
          : item
      );

      this.setState({
        artifactStatuses: downloadedStatuses,
        items: downloadedItems,
        feedback: {
          type: 'success',
          message: `Archivo "${result.filename}" descargado con éxito.`,
        },
      });
    } catch (err: unknown) {
      const errorResult = handleArtifactError(err);
      const erroredStatuses = {
        ...this.state.artifactStatuses,
        [id]: errorResult.status,
      };
      const errorItems = this.state.items.map((item) =>
        item.id === id
          ? {
              ...item,
              artifactStatus: errorResult.status,
              canDownloadArtifact: errorResult.canRetry,
            }
          : item
      );

      this.setState({
        artifactStatuses: erroredStatuses,
        items: errorItems,
        feedback: {
          type: errorResult.canRetry ? 'warning' : 'error',
          message: errorResult.message,
        },
      });

      if (!options?.isAutomatic) {
        throw err;
      }
    }
  }

  /**
   * Dispatches automatic download once per completed job.
   */
  autoDownloadIfCompleted(jobId: string): void {
    if (this.autoDispatchedJobs.has(jobId)) {
      return;
    }
    const currentStatus = this.state.artifactStatuses[jobId];
    if (
      currentStatus === 'downloaded' ||
      currentStatus === 'expired' ||
      currentStatus === 'missing'
    ) {
      return;
    }

    this.autoDispatchedJobs.add(jobId);
    this.downloadArtifact(jobId, { isAutomatic: true }).catch(() => {
      // Handled via store state
    });
  }

  /**
   * Applies real-time lifecycle changes received via SSE stream.
   * Updates state item in-place without tearing down un-related items.
   */
  applyRealtimeUpdate(event: SseDownloadEventData): void {
    const status = (event.status as DownloadStatus) || 'PENDING';
    const progress = Math.min(Math.max(Number(event.progress) || 0, 0), 100);
    const existingIndex = this.state.items.findIndex(
      (item) => item.id === event.jobId
    );

    if (existingIndex !== -1) {
      const existing = this.state.items[existingIndex];
      const isCompleted = status === 'COMPLETED';
      const artifactStatus =
        this.state.artifactStatuses[event.jobId] ?? existing.artifactStatus;
      const canDownloadArtifact =
        isCompleted &&
        artifactStatus !== 'downloaded' &&
        artifactStatus !== 'expired' &&
        artifactStatus !== 'missing';

      const updatedItem: DownloadJobViewModel = {
        ...existing,
        status,
        statusConfig: getStatusConfig(status),
        progress: isCompleted ? 100 : progress,
        speed: event.speed !== undefined ? event.speed : existing.speed,
        eta: event.eta !== undefined ? event.eta : existing.eta,
        displayTitle:
          event.title && event.title.trim() !== ''
            ? event.title.trim()
            : existing.displayTitle,
        isTitleFallback: event.title ? false : existing.isTitleFallback,
        errorCode:
          event.errorCode !== undefined ? event.errorCode : existing.errorCode,
        errorMessage: event.errorCode
          ? formatJobError(event.errorCode, null)
          : existing.errorMessage,
        canCancel: isJobCancellable(status, existing.cancellationRequested),
        canRetry: isJobRetryable(status),
        artifactStatus,
        canDownloadArtifact,
      };

      const newItems = [...this.state.items];
      newItems[existingIndex] = updatedItem;
      this.setState({ items: newItems });

      if (isCompleted) {
        this.autoDownloadIfCompleted(event.jobId);
      }
    } else {
      // If the job belongs to another page or is a newly created transition,
      // refresh authoritative page if on page 0
      if (this.state.page === 0) {
        this.loadPage(0);
      }
    }
  }

  /**
   * Handles stream-reset event: purges cursor and reconciles with authoritative REST API.
   */
  async handleStreamReset(data?: SseStreamResetData): Promise<void> {
    const reason =
      data?.code === 'EVENT_HISTORY_EXPIRED'
        ? 'Historial de eventos expirado en el servidor.'
        : data?.code === 'EVENT_CURSOR_AHEAD'
          ? 'Cursor adelantado.'
          : 'Sincronizando estado con el servidor.';

    this.setState({
      feedback: {
        type: 'info',
        message: `${reason} Reconciliando con el backend...`,
      },
    });

    await this.loadPage(this.state.page);
  }

  /**
   * Loads a paginated list of downloads from the authoritative REST backend.
   */
  async loadPage(
    page = this.state.page,
    size = this.state.size
  ): Promise<void> {
    this.setState({ loading: true, error: null });

    try {
      const response = await getDownloads({ page, size });
      const items = (response.data.items || []).map((dto) =>
        mapJobDtoToViewModel(
          dto,
          this.state.artifactStatuses[dto.id ?? ''] ?? 'idle'
        )
      );
      this.setState({
        items,
        page: response.data.page ?? page,
        size: response.data.size ?? size,
        totalElements: Number(response.data.totalElements) || items.length,
        totalPages: response.data.totalPages ?? 1,
        loading: false,
      });
    } catch (err: unknown) {
      const errorMessage =
        err instanceof ApiProblemError
          ? err.problem.detail || err.problem.title
          : err instanceof NetworkError
            ? err.message
            : 'Error al cargar la bandeja de descargas.';

      this.setState({
        loading: false,
        error: errorMessage,
      });
    }
  }

  /**
   * Ingests a single URL with an Idempotency-Key.
   */
  async addSingle(url: string): Promise<IngestionResultSummary> {
    const idempotencyKey = generateIdempotencyKey('single');
    this.setState({ loading: true, error: null, feedback: null });

    try {
      const response = await createDownload({ url }, { idempotencyKey });
      const summary = mapCreateResponseToSummary(response.data);
      this.setState({ lastIngestionSummary: summary, loading: false });

      await this.loadPage(0);
      return summary;
    } catch (err: unknown) {
      this.handleMutationError(err);
      throw err;
    }
  }

  /**
   * Ingests multiple URLs with an Idempotency-Key.
   */
  async addBulk(urls: string[]): Promise<IngestionResultSummary> {
    const idempotencyKey = generateIdempotencyKey('bulk');
    this.setState({ loading: true, error: null, feedback: null });

    try {
      const response = await createBulkDownloads({ urls }, { idempotencyKey });
      const summary = mapCreateResponseToSummary(response.data);
      this.setState({ lastIngestionSummary: summary, loading: false });

      await this.loadPage(0);
      return summary;
    } catch (err: unknown) {
      this.handleMutationError(err);
      throw err;
    }
  }

  /**
   * Ingests a UTF-8 TXT file with an Idempotency-Key.
   */
  async importTxt(file: File | Blob): Promise<IngestionResultSummary> {
    const idempotencyKey = generateIdempotencyKey('import');
    this.setState({ loading: true, error: null, feedback: null });

    try {
      const response = await importDownloadsText(file, { idempotencyKey });
      const summary = mapCreateResponseToSummary(response.data);
      this.setState({ lastIngestionSummary: summary, loading: false });

      await this.loadPage(0);
      return summary;
    } catch (err: unknown) {
      this.handleMutationError(err);
      throw err;
    }
  }

  /**
   * Starts processing pending jobs in the queue.
   * Handles HTTP 202 (asynchronous acceptance of queued work) and 200 (no work to queue).
   * Guarded against double-clicks via isOperating mutex.
   */
  async startQueue(): Promise<QueueStartResponse> {
    if (this.state.isOperating) {
      return { queuedCount: 0, jobIds: [] };
    }

    this.setState({
      isOperating: true,
      error: null,
      feedback: null,
      retryAfterSeconds: null,
    });

    try {
      const response = await startQueue();
      const queuedCount = response.data.queuedCount ?? 0;

      if (response.meta.status === 202 && queuedCount > 0) {
        this.setState({
          feedback: {
            type: 'success',
            message: `${queuedCount} descarga(s) encolada(s) para procesamiento.`,
          },
        });
      } else {
        this.setState({
          feedback: {
            type: 'info',
            message: 'No hay descargas pendientes por encolar.',
          },
        });
      }

      await this.loadPage(this.state.page);
      return response.data;
    } catch (err: unknown) {
      this.handleMutationError(err);
      throw err;
    } finally {
      this.setState({ isOperating: false });
    }
  }

  /**
   * Cancels all active jobs (pending, queued, downloading, converting).
   * Guarded against double-clicks via isOperating mutex.
   */
  async cancelQueue(): Promise<QueueCancelResponse> {
    if (this.state.isOperating) {
      return { cancelledCount: 0, cancellationRequestedCount: 0 };
    }

    this.setState({
      isOperating: true,
      error: null,
      feedback: null,
      retryAfterSeconds: null,
    });

    try {
      const response = await cancelQueue();
      const cancelled = response.data.cancelledCount ?? 0;
      const requested = response.data.cancellationRequestedCount ?? 0;

      this.setState({
        feedback: {
          type: 'info',
          message: `Cola cancelada: ${cancelled} detenida(s) inmediatamente, ${requested} en proceso de detención.`,
        },
      });

      await this.loadPage(this.state.page);
      return response.data;
    } catch (err: unknown) {
      this.handleMutationError(err);
      throw err;
    } finally {
      this.setState({ isOperating: false });
    }
  }

  /**
   * Cancels an individual download job.
   * Handles 200 (immediate cancellation) and 202 (asynchronous cancellation requested).
   */
  async cancelDownload(id: string): Promise<DownloadControlResponse> {
    if (this.state.activeJobOperationId === id) {
      throw new Error('Operación de cancelación en curso.');
    }

    this.setState({
      activeJobOperationId: id,
      error: null,
      feedback: null,
      retryAfterSeconds: null,
    });

    try {
      const response = await cancelDownload(id);
      const isAsync =
        response.meta.status === 202 ||
        Boolean(response.data.cancellationRequested);

      this.setState({
        feedback: {
          type: 'info',
          message: isAsync
            ? 'Cancelación solicitada al worker. Finalizando procesos...'
            : 'Descarga cancelada correctamente.',
        },
      });

      await this.loadPage(this.state.page);
      return response.data;
    } catch (err: unknown) {
      this.handleMutationError(err);
      throw err;
    } finally {
      this.setState({ activeJobOperationId: null });
    }
  }

  /**
   * Retries an individual failed download job.
   * Client-side rule: ONLY jobs in FAILED status can be retried.
   * Calling retry on non-FAILED jobs is blocked locally to avoid 409 DOWNLOAD_NOT_RETRYABLE.
   */
  async retryDownload(id: string): Promise<DownloadControlResponse> {
    const existing = this.state.items.find((i) => i.id === id);
    if (existing && existing.status !== 'FAILED') {
      const message = `La descarga en estado ${existing.statusConfig.label} no puede ser reintentada (solo trabajos Fallidos).`;
      this.setState({ error: message });
      throw new Error(message);
    }

    if (this.state.activeJobOperationId === id) {
      throw new Error('Operación de reintento en curso.');
    }

    this.setState({
      activeJobOperationId: id,
      error: null,
      feedback: null,
      retryAfterSeconds: null,
    });

    try {
      const response = await retryDownload(id);
      this.setState({
        feedback: {
          type: 'success',
          message:
            'Descarga restablecida a Pendiente. Inicie la cola para procesarla.',
        },
      });

      await this.loadPage(this.state.page);
      return response.data;
    } catch (err: unknown) {
      this.handleMutationError(err);
      throw err;
    } finally {
      this.setState({ activeJobOperationId: null });
    }
  }

  /**
   * Cleans up completed downloads from the view and marks artifacts for immediate deletion.
   */
  async clearCompleted(): Promise<QueueClearResponse> {
    if (this.state.isOperating) {
      return {
        clearedCount: 0,
        cancelledCount: 0,
        cancellationRequestedCount: 0,
      };
    }

    this.setState({
      isOperating: true,
      error: null,
      feedback: null,
      retryAfterSeconds: null,
    });

    try {
      const response = await clearCompletedQueue();
      const cleared = response.data.clearedCount ?? 0;

      this.setState({
        feedback: {
          type: 'info',
          message: `${cleared} descarga(s) completada(s) eliminada(s) de la bandeja.`,
        },
      });

      await this.loadPage(this.state.page);
      return response.data;
    } catch (err: unknown) {
      this.handleMutationError(err);
      throw err;
    } finally {
      this.setState({ isOperating: false });
    }
  }

  /**
   * Cancels active jobs and clears the entire visible queue.
   */
  async clearAll(): Promise<QueueClearResponse> {
    if (this.state.isOperating) {
      return {
        clearedCount: 0,
        cancelledCount: 0,
        cancellationRequestedCount: 0,
      };
    }

    this.setState({
      isOperating: true,
      error: null,
      feedback: null,
      retryAfterSeconds: null,
    });

    try {
      const response = await clearQueue();
      const cleared = response.data.clearedCount ?? 0;

      this.setState({
        feedback: {
          type: 'info',
          message: `Bandeja de descargas vaciada (${cleared} eliminadas).`,
        },
      });

      await this.loadPage(0);
      return response.data;
    } catch (err: unknown) {
      this.handleMutationError(err);
      throw err;
    } finally {
      this.setState({ isOperating: false });
    }
  }

  private handleMutationError(err: unknown): void {
    if (err instanceof ApiProblemError) {
      if (err.status === 429) {
        const seconds = err.retryAfterSeconds;
        const msg = seconds
          ? `Límite de solicitudes alcanzado. Por favor espere ${seconds} segundos antes de reintentar.`
          : 'Límite de solicitudes alcanzado. Por favor espere un momento antes de reintentar.';

        this.setState({
          loading: false,
          isOperating: false,
          activeJobOperationId: null,
          retryAfterSeconds: seconds ?? null,
          error: msg,
        });
        return;
      }

      this.setState({
        loading: false,
        isOperating: false,
        activeJobOperationId: null,
        error: err.problem.detail || err.problem.title,
      });
      return;
    }

    const errorMessage =
      err instanceof NetworkError
        ? err.message
        : err instanceof Error
          ? err.message
          : 'Error en la operación de cola.';

    this.setState({
      loading: false,
      isOperating: false,
      activeJobOperationId: null,
      error: errorMessage,
    });
  }
}

// Global store instance for client-side use
export const downloadStore = new DownloadStore();
