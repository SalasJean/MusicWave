import { describe, it, expect } from 'vitest';
import {
  mapJobDtoToViewModel,
  mapCreateResponseToSummary,
} from './downloadMappers';
import type {
  DownloadJobResponse,
  CreateDownloadsResponse,
} from '../api/downloadsApi';

describe('downloadMappers', () => {
  it('mapJobDtoToViewModel maps complete DTO with fallback title when title is pending', () => {
    const dto: DownloadJobResponse = {
      id: 'job-uuid-1',
      sourceUrl: 'https://www.youtube.com/watch?v=BaW_jenozKc',
      title: undefined,
      titleStatus: 'PENDING',
      status: 'DOWNLOADING',
      progress: 45.8,
      speed: '1.8MiB/s',
      eta: '00:20',
      retryCount: 1,
      createdAt: '2026-10-14T02:00:00Z',
      startedAt: '2026-10-14T02:01:00Z',
    };

    const vm = mapJobDtoToViewModel(dto);

    expect(vm.id).toBe('job-uuid-1');
    expect(vm.displayTitle).toBe('YouTube (BaW_jenozKc)');
    expect(vm.isTitleFallback).toBe(true);
    expect(vm.status).toBe('DOWNLOADING');
    expect(vm.statusConfig.label).toBe('Descargando');
    expect(vm.progress).toBe(45.8);
    expect(vm.speed).toBe('1.8MiB/s');
    expect(vm.eta).toBe('00:20');
    expect(vm.retryCount).toBe(1);
    expect(vm.errorMessage).toBeNull();
  });

  it('mapJobDtoToViewModel maps FAILED state with formatted error message', () => {
    const dto: DownloadJobResponse = {
      id: 'job-uuid-2',
      sourceUrl: 'http://127.0.0.1/private',
      title: 'Restricted media',
      titleStatus: 'RESOLVED',
      status: 'FAILED',
      progress: 0,
      error: {
        code: 'URL_DESTINATION_NOT_ALLOWED',
        message: 'Loopback destination rejected',
      },
      createdAt: '2026-10-14T02:00:00Z',
    };

    const vm = mapJobDtoToViewModel(dto);

    expect(vm.status).toBe('FAILED');
    expect(vm.statusConfig.badgeVariant).toBe('failed');
    expect(vm.errorCode).toBe('URL_DESTINATION_NOT_ALLOWED');
    expect(vm.errorMessage).toContain('Destino no permitido');
  });

  it('mapCreateResponseToSummary maps partial bulk acceptance with rejected positions', () => {
    const response: CreateDownloadsResponse = {
      requestId: 'req-batch-1',
      replayed: false,
      createdCount: 1,
      rejectedCount: 1,
      jobs: [
        {
          id: 'job-1',
          sourceUrl: 'https://example.com/valid',
          status: 'PENDING',
        },
      ],
      rejected: [
        {
          position: 2,
          value: 'invalid-url',
          code: 'URL_INVALID',
          message: 'Malformed URL format',
        },
      ],
    };

    const summary = mapCreateResponseToSummary(response);

    expect(summary.createdCount).toBe(1);
    expect(summary.rejectedCount).toBe(1);
    expect(summary.createdJobs.length).toBe(1);
    expect(summary.createdJobs[0].id).toBe('job-1');
    expect(summary.rejectedItems.length).toBe(1);
    expect(summary.rejectedItems[0].position).toBe(2);
    expect(summary.rejectedItems[0].value).toBe('invalid-url');
    expect(summary.rejectedItems[0].formattedError).toContain(
      'formato no válido'
    );
  });

  it('sets canCancel and canRetry flags according to contract rules', () => {
    // PENDING: canCancel = true, canRetry = false
    const pendingVm = mapJobDtoToViewModel({
      id: 'job-p',
      sourceUrl: 'https://example.com/1',
      status: 'PENDING',
    });
    expect(pendingVm.canCancel).toBe(true);
    expect(pendingVm.canRetry).toBe(false);

    // DOWNLOADING: canCancel = true, canRetry = false
    const downloadingVm = mapJobDtoToViewModel({
      id: 'job-d',
      sourceUrl: 'https://example.com/2',
      status: 'DOWNLOADING',
    });
    expect(downloadingVm.canCancel).toBe(true);
    expect(downloadingVm.canRetry).toBe(false);

    // DOWNLOADING with cancellationRequested: canCancel = false
    const cancellingVm = mapJobDtoToViewModel({
      id: 'job-c',
      sourceUrl: 'https://example.com/3',
      status: 'DOWNLOADING',
      cancellationRequested: true,
    } as DownloadJobResponse);
    expect(cancellingVm.cancellationRequested).toBe(true);
    expect(cancellingVm.canCancel).toBe(false);

    // FAILED: canCancel = false, canRetry = true (ONLY state allowed to retry)
    const failedVm = mapJobDtoToViewModel({
      id: 'job-f',
      sourceUrl: 'https://example.com/4',
      status: 'FAILED',
    });
    expect(failedVm.canCancel).toBe(false);
    expect(failedVm.canRetry).toBe(true);

    // COMPLETED: terminal, neither cancellable nor retryable
    const completedVm = mapJobDtoToViewModel({
      id: 'job-comp',
      sourceUrl: 'https://example.com/5',
      status: 'COMPLETED',
    });
    expect(completedVm.canCancel).toBe(false);
    expect(completedVm.canRetry).toBe(false);

    // CANCELLED: terminal, neither cancellable nor retryable
    const cancelledVm = mapJobDtoToViewModel({
      id: 'job-canc',
      sourceUrl: 'https://example.com/6',
      status: 'CANCELLED',
    });
    expect(cancelledVm.canCancel).toBe(false);
    expect(cancelledVm.canRetry).toBe(false);
  });
});
