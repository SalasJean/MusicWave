import { describe, it, expect } from 'vitest';
import { experimental_AstroContainer } from 'astro/container';
import DownloadWorkspace from './DownloadWorkspace.astro';

describe('DownloadWorkspace component', () => {
  it('renders complete integrated workspace with all landmarks and controls', async () => {
    const container = await experimental_AstroContainer.create();
    const html = await container.renderToString(DownloadWorkspace);

    // Root workspace and header
    expect(html).toContain('id="download-workspace"');
    expect(html).toContain('Bandeja de Descargas');
    expect(html).toContain('href="/settings"');

    // Ingestion section
    expect(html).toContain('id="single-url-input"');
    expect(html).toContain('id="single-submit-btn"');
    expect(html).toContain('bulk-ingest-modal');
    expect(html).toContain('import-txt-modal');

    // Queue controls section
    expect(html).toContain('id="queue-controls-container"');
    expect(html).toContain('id="start-queue-btn"');
    expect(html).toContain('id="cancel-queue-btn"');
    expect(html).toContain('id="sse-status-badge"');

    // List and cards section
    expect(html).toContain('id="download-list-container"');
    expect(html).toContain('id="downloads-cards-grid"');

    // Modals
    expect(html).toContain('id="bulk-ingest-modal"');
    expect(html).toContain('id="import-txt-modal"');

    // Network resilience alert
    expect(html).toContain('id="workspace-network-alert"');
    expect(html).toContain('id="retry-network-btn"');
  });

  it('contains accessible headings and ARIA landmarks', async () => {
    const container = await experimental_AstroContainer.create();
    const html = await container.renderToString(DownloadWorkspace);

    expect(html).toContain('aria-labelledby="heading-add-downloads"');
    expect(html).toContain('aria-labelledby="heading-queue-controls"');
    expect(html).toContain('aria-labelledby="heading-queue-list"');
  });
});
