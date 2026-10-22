import { describe, it, expect } from 'vitest';
import { experimental_AstroContainer } from 'astro/container';
import QueueControls from './QueueControls.astro';

describe('QueueControls component', () => {
  it('renders queue controls with accessible labels and action buttons', async () => {
    const container = await experimental_AstroContainer.create();
    const html = await container.renderToString(QueueControls);

    // Structural elements
    expect(html).toContain('id="queue-controls-container"');
    expect(html).toContain(
      'aria-label="Controles y operaciones de la cola de descargas"'
    );

    // Queue action buttons
    expect(html).toContain('id="start-queue-btn"');
    expect(html).toContain('Iniciar cola');
    expect(html).toContain('id="cancel-queue-btn"');
    expect(html).toContain('Cancelar cola');
    expect(html).toContain('id="open-clear-completed-modal-btn"');
    expect(html).toContain('id="open-clear-all-modal-btn"');

    // Modals for destructive actions
    expect(html).toContain('id="clear-completed-modal"');
    expect(html).toContain('Limpiar descargas completadas');
    expect(html).toContain('id="confirm-clear-completed-btn"');

    expect(html).toContain('id="clear-all-modal"');
    expect(html).toContain('¿Vaciar toda la bandeja de descargas?');
    expect(html).toContain('id="confirm-clear-all-btn"');

    // Feedback wrapper
    expect(html).toContain('id="queue-feedback-wrapper"');

    // SSE connection indicator
    expect(html).toContain('id="sse-status-badge"');
    expect(html).toContain('role="status"');
    expect(html).toContain('aria-live="polite"');
  });
});
