import type {
  DownloadJobResponse,
  RejectedUrlResponse,
  CreateDownloadsResponse,
} from '@/features/downloads/api/downloadsApi';
import {
  getStatusConfig,
  isJobCancellable,
  isJobRetryable,
  type StatusConfig,
  type DownloadStatus,
} from './downloadStatus';
import {
  resolveDisplayTitle,
  formatDate,
  formatJobError,
} from '../lib/formatters';
import type { ArtifactStatus } from '../lib/artifactManager';

export interface DownloadJobViewModel {
  id: string;
  sourceUrl: string;
  displayTitle: string;
  isTitleFallback: boolean;
  titleStatus: string;
  status: DownloadStatus;
  statusConfig: StatusConfig;
  progress: number;
  speed: string | null;
  eta: string | null;
  retryCount: number;
  errorMessage: string | null;
  errorCode: string | null;
  createdAtFormatted: string;
  startedAtFormatted: string | null;
  completedAtFormatted: string | null;
  cancellationRequested: boolean;
  canCancel: boolean;
  canRetry: boolean;
  artifactStatus: ArtifactStatus;
  canDownloadArtifact: boolean;
  raw: DownloadJobResponse;
}

export function mapJobDtoToViewModel(
  dto: DownloadJobResponse,
  artifactStatus: ArtifactStatus = 'idle'
): DownloadJobViewModel {
  const { title, isFallback } = resolveDisplayTitle(
    dto.title,
    dto.titleStatus,
    dto.sourceUrl
  );

  const status = (dto.status as DownloadStatus) || 'PENDING';
  const statusConfig = getStatusConfig(dto.status);
  const progress = Math.min(Math.max(Number(dto.progress) || 0, 0), 100);
  const cancellationRequested = Boolean(
    (dto as { cancellationRequested?: boolean }).cancellationRequested
  );

  let errorMessage: string | null = null;
  let errorCode: string | null = null;
  if (dto.error) {
    errorCode = dto.error.code || null;
    errorMessage = formatJobError(dto.error.code, dto.error.message);
  }

  const isCompleted = status === 'COMPLETED';
  const canDownloadArtifact =
    isCompleted &&
    artifactStatus !== 'downloaded' &&
    artifactStatus !== 'expired' &&
    artifactStatus !== 'missing';

  return {
    id: dto.id || '',
    sourceUrl: dto.sourceUrl || '',
    displayTitle: title,
    isTitleFallback: isFallback,
    titleStatus: dto.titleStatus || 'NOT_APPLICABLE',
    status,
    statusConfig,
    progress,
    speed: dto.speed || null,
    eta: dto.eta || null,
    retryCount: dto.retryCount || 0,
    errorMessage,
    errorCode,
    createdAtFormatted: formatDate(dto.createdAt),
    startedAtFormatted: dto.startedAt ? formatDate(dto.startedAt) : null,
    completedAtFormatted: dto.completedAt ? formatDate(dto.completedAt) : null,
    cancellationRequested,
    canCancel: isJobCancellable(status, cancellationRequested),
    canRetry: isJobRetryable(status),
    artifactStatus,
    canDownloadArtifact,
    raw: dto,
  };
}

export interface IngestionResultSummary {
  replayed: boolean;
  createdCount: number;
  rejectedCount: number;
  createdJobs: DownloadJobViewModel[];
  rejectedItems: Array<{
    position: number;
    value: string;
    code: string;
    message: string;
    formattedError: string;
  }>;
}

export function mapCreateResponseToSummary(
  response: CreateDownloadsResponse
): IngestionResultSummary {
  const createdJobs = (response.jobs || []).map((job) =>
    mapJobDtoToViewModel(job)
  );
  const rejectedItems = (response.rejected || []).map(
    (rej: RejectedUrlResponse) => ({
      position: rej.position || 0,
      value: rej.value || '',
      code: rej.code || 'INVALID_URL',
      message: rej.message || '',
      formattedError: formatJobError(rej.code, rej.message),
    })
  );

  return {
    replayed: Boolean(response.replayed),
    createdCount: response.createdCount || 0,
    rejectedCount: response.rejectedCount || 0,
    createdJobs,
    rejectedItems,
  };
}
