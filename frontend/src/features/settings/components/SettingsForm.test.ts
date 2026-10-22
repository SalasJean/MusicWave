import { describe, it, expect } from 'vitest';
import { experimental_AstroContainer } from 'astro/container';
import SettingsForm from './SettingsForm.astro';
import { AUDIO_QUALITY_OPTIONS } from '../model/settingsModel';

describe('SettingsForm component', () => {
  it('renders concurrency number input and range slider with range 1 to 10', async () => {
    const container = await experimental_AstroContainer.create();
    const html = await container.renderToString(SettingsForm, {
      props: {
        initialConcurrency: 4,
        initialQuality: '192K',
      },
    });

    expect(html).toContain('id="max-concurrency-input"');
    expect(html).toContain('min="1"');
    expect(html).toContain('max="10"');
    expect(html).toContain('value="4"');
    expect(html).toContain('id="max-concurrency-slider"');
    expect(html).toContain('id="concurrency-value-display"');
  });

  it('renders all audio quality options in select dropdown', async () => {
    const container = await experimental_AstroContainer.create();
    const html = await container.renderToString(SettingsForm, {
      props: {
        initialConcurrency: 3,
        initialQuality: '320K',
      },
    });

    expect(html).toContain('id="audio-quality-select"');

    for (const opt of AUDIO_QUALITY_OPTIONS) {
      expect(html).toContain(`value="${opt.value}"`);
    }

    // Selected option verification
    expect(html).toContain('value="320K" selected');
  });

  it('renders action buttons and sync badge indicator', async () => {
    const container = await experimental_AstroContainer.create();
    const html = await container.renderToString(SettingsForm);

    expect(html).toContain('id="save-settings-btn"');
    expect(html).toContain('Guardar ajustes');
    expect(html).toContain('id="reset-settings-btn"');
    expect(html).toContain('Restablecer');
    expect(html).toContain('id="sync-status-badge"');
    expect(html).toContain('Sincronizado');
  });

  it('includes accessible error alert and inline field error containers', async () => {
    const container = await experimental_AstroContainer.create();
    const html = await container.renderToString(SettingsForm);

    expect(html).toContain('id="concurrency-error"');
    expect(html).toContain('id="audio-quality-error"');
    expect(html).toContain('id="settings-success-alert-wrapper"');
    expect(html).toContain('id="settings-error-alert-wrapper"');
  });
});
