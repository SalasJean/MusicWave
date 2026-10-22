import { describe, it, expect } from 'vitest';
import { experimental_AstroContainer } from 'astro/container';
import DownloadCard from './DownloadCard.astro';
import { mapJobDtoToViewModel } from '../model/downloadMappers';

describe('DownloadCard component', () => {
  it('renders cancel button for cancellable active job', async () => {
    const container = await experimental_AstroContainer.create();
    const job = mapJobDtoToViewModel({
      id: 'job-pending-1',
      sourceUrl: 'https://youtube.com/watch?v=123',
      status: 'PENDING',
      createdAt: '2026-10-14T02:00:00Z',
    });

    const html = await container.renderToString(DownloadCard, {
      props: { job },
    });

    expect(html).toContain('data-action="cancel-job"');
    expect(html).toContain('Cancelar');
    expect(html).not.toContain('data-action="retry-job"');
  });

  it('renders cancellation requested badge when in progress', async () => {
    const container = await experimental_AstroContainer.create();
    const job = mapJobDtoToViewModel({
      id: 'job-downloading-1',
      sourceUrl: 'https://youtube.com/watch?v=123',
      status: 'DOWNLOADING',
      cancellationRequested: true,
      createdAt: '2026-10-14T02:00:00Z',
    } as never);

    const html = await container.renderToString(DownloadCard, {
      props: { job },
    });

    expect(html).toContain('Cancelación solicitada...');
    expect(html).not.toContain('data-action="cancel-job"');
  });

  it('renders retry button ONLY for FAILED jobs', async () => {
    const container = await experimental_AstroContainer.create();
    const failedJob = mapJobDtoToViewModel({
      id: 'job-failed-1',
      sourceUrl: 'https://youtube.com/watch?v=123',
      status: 'FAILED',
      error: { code: 'HTTP_404', message: 'Video unavailable' },
      createdAt: '2026-10-14T02:00:00Z',
    });

    const html = await container.renderToString(DownloadCard, {
      props: { job: failedJob },
    });

    expect(html).toContain('data-action="retry-job"');
    expect(html).toContain('Reintentar');
    expect(html).not.toContain('data-action="cancel-job"');
  });

  it('does NOT render cancel or retry button for terminal COMPLETED jobs', async () => {
    const container = await experimental_AstroContainer.create();
    const completedJob = mapJobDtoToViewModel({
      id: 'job-comp-1',
      sourceUrl: 'https://youtube.com/watch?v=123',
      status: 'COMPLETED',
      progress: 100,
      createdAt: '2026-10-14T02:00:00Z',
    });

    const html = await container.renderToString(DownloadCard, {
      props: { job: completedJob },
    });

    expect(html).not.toContain('data-action="cancel-job"');
    expect(html).not.toContain('data-action="retry-job"');
  });

  it('renders "Descargar MP3" button when job is completed and artifact is available', async () => {
    const container = await experimental_AstroContainer.create();
    const completedJob = mapJobDtoToViewModel(
      {
        id: 'job-comp-ready',
        sourceUrl: 'https://youtube.com/watch?v=123',
        status: 'COMPLETED',
        progress: 100,
        createdAt: '2026-10-14T02:00:00Z',
      },
      'idle'
    );

    const html = await container.renderToString(DownloadCard, {
      props: { job: completedJob },
    });

    expect(html).toContain('data-action="download-artifact"');
    expect(html).toContain('Descargar MP3');
  });

  it('renders "Descargando MP3..." indicator when artifactStatus is delivering', async () => {
    const container = await experimental_AstroContainer.create();
    const deliveringJob = mapJobDtoToViewModel(
      {
        id: 'job-comp-delivering',
        sourceUrl: 'https://youtube.com/watch?v=123',
        status: 'COMPLETED',
        progress: 100,
        createdAt: '2026-10-14T02:00:00Z',
      },
      'delivering'
    );

    const html = await container.renderToString(DownloadCard, {
      props: { job: deliveringJob },
    });

    expect(html).toContain('Descargando MP3...');
    expect(html).not.toContain('data-action="download-artifact"');
  });

  it('renders "Descargado (un solo uso)" badge when artifactStatus is downloaded', async () => {
    const container = await experimental_AstroContainer.create();
    const downloadedJob = mapJobDtoToViewModel(
      {
        id: 'job-comp-downloaded',
        sourceUrl: 'https://youtube.com/watch?v=123',
        status: 'COMPLETED',
        progress: 100,
        createdAt: '2026-10-14T02:00:00Z',
      },
      'downloaded'
    );

    const html = await container.renderToString(DownloadCard, {
      props: { job: downloadedJob },
    });

    expect(html).toContain('Descargado (un solo uso)');
    expect(html).not.toContain('data-action="download-artifact"');
  });

  it('renders "Artefacto expirado" badge when artifactStatus is expired', async () => {
    const container = await experimental_AstroContainer.create();
    const expiredJob = mapJobDtoToViewModel(
      {
        id: 'job-comp-expired',
        sourceUrl: 'https://youtube.com/watch?v=123',
        status: 'COMPLETED',
        progress: 100,
        createdAt: '2026-10-14T02:00:00Z',
      },
      'expired'
    );

    const html = await container.renderToString(DownloadCard, {
      props: { job: expiredJob },
    });

    expect(html).toContain('Artefacto expirado');
    expect(html).not.toContain('data-action="download-artifact"');
  });

  it('renders "No disponible" badge when artifactStatus is missing', async () => {
    const container = await experimental_AstroContainer.create();
    const missingJob = mapJobDtoToViewModel(
      {
        id: 'job-comp-missing',
        sourceUrl: 'https://youtube.com/watch?v=123',
        status: 'COMPLETED',
        progress: 100,
        createdAt: '2026-10-14T02:00:00Z',
      },
      'missing'
    );

    const html = await container.renderToString(DownloadCard, {
      props: { job: missingJob },
    });

    expect(html).toContain('No disponible');
    expect(html).not.toContain('data-action="download-artifact"');
  });
});
