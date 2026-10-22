import { describe, it, expect } from 'vitest';
import { experimental_AstroContainer } from 'astro/container';
import Button from './Button.astro';
import Badge from './Badge.astro';
import ProgressBar from './ProgressBar.astro';
import Alert from './Alert.astro';
import Spinner from './Spinner.astro';

describe('UI Components accessibility and rendering', () => {
  it('Button renders accessible attributes when loading and disabled', async () => {
    const container = await experimental_AstroContainer.create();

    const normalHtml = await container.renderToString(Button, {
      slots: { default: 'Enviar' },
    });
    expect(normalHtml).toContain('Enviar');
    expect(normalHtml).toContain('type="button"');

    const loadingHtml = await container.renderToString(Button, {
      props: { loading: true },
      slots: { default: 'Guardando' },
    });
    expect(loadingHtml).toContain('aria-busy="true"');
    expect(loadingHtml).toContain('disabled');
    expect(loadingHtml).toContain('role="status"'); // spinner inside

    const disabledHtml = await container.renderToString(Button, {
      props: { disabled: true },
      slots: { default: 'Inactivo' },
    });
    expect(disabledHtml).toContain('disabled');
    expect(disabledHtml).toContain('aria-disabled="true"');
  });

  it('Badge renders correct download status variants and indicator dot', async () => {
    const container = await experimental_AstroContainer.create();

    const badgeHtml = await container.renderToString(Badge, {
      props: { variant: 'downloading', dot: true },
      slots: { default: 'DOWNLOADING' },
    });

    expect(badgeHtml).toContain('DOWNLOADING');
    expect(badgeHtml).toContain('bg-emerald-950/80');
    expect(badgeHtml).toContain('animate-pulse');
  });

  it('ProgressBar renders compliant ARIA progressbar role and values', async () => {
    const container = await experimental_AstroContainer.create();

    const progressHtml = await container.renderToString(ProgressBar, {
      props: {
        value: 75,
        max: 100,
        speed: '1.2 MiB/s',
        eta: '00:15',
        status: 'downloading',
      },
    });

    expect(progressHtml).toContain('role="progressbar"');
    expect(progressHtml).toContain('aria-valuenow="75"');
    expect(progressHtml).toContain('aria-valuemin="0"');
    expect(progressHtml).toContain('aria-valuemax="100"');
    expect(progressHtml).toContain('aria-valuetext="75% downloading"');
    expect(progressHtml).toContain('1.2 MiB/s');
    expect(progressHtml).toContain('ETA: 00:15');
  });

  it('Alert renders role="alert" for errors and role="status" for info', async () => {
    const container = await experimental_AstroContainer.create();

    const errorHtml = await container.renderToString(Alert, {
      props: { variant: 'error', title: 'Fallo Crítico' },
      slots: { default: 'Detalle del error' },
    });
    expect(errorHtml).toContain('role="alert"');
    expect(errorHtml).toContain('Fallo Crítico');

    const infoHtml = await container.renderToString(Alert, {
      props: { variant: 'info', title: 'Aviso' },
      slots: { default: 'Operación normal' },
    });
    expect(infoHtml).toContain('role="status"');
    expect(infoHtml).toContain('Aviso');
  });

  it('Spinner renders role="status" and sr-only label', async () => {
    const container = await experimental_AstroContainer.create();

    const spinnerHtml = await container.renderToString(Spinner, {
      props: { label: 'Procesando descarga...' },
    });

    expect(spinnerHtml).toContain('role="status"');
    expect(spinnerHtml).toContain('Procesando descarga...');
    expect(spinnerHtml).toContain('sr-only');
  });
});
