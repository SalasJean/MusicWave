import {
  getSettings,
  updateSettings,
  type SettingsResponse,
} from '../api/settingsApi';
import {
  validateSettings,
  mapBackendSettingsError,
  DEFAULT_CONCURRENCY,
  DEFAULT_AUDIO_QUALITY,
} from '../model/settingsModel';
import { ApiProblemError, NetworkError } from '@/shared/api/problemDetails';

export interface SettingsState {
  settings: {
    maxConcurrency: number;
    audioQuality: string;
  };
  initialSettings: {
    maxConcurrency: number;
    audioQuality: string;
  };
  loading: boolean;
  saving: boolean;
  error: string | null;
  fieldErrors: {
    maxConcurrency?: string;
    audioQuality?: string;
  };
  successMessage: string | null;
  isDirty: boolean;
}

export class SettingsStore extends EventTarget {
  private state: SettingsState;

  constructor() {
    super();
    const defaults = {
      maxConcurrency: DEFAULT_CONCURRENCY,
      audioQuality: DEFAULT_AUDIO_QUALITY,
    };

    this.state = {
      settings: { ...defaults },
      initialSettings: { ...defaults },
      loading: false,
      saving: false,
      error: null,
      fieldErrors: {},
      successMessage: null,
      isDirty: false,
    };
  }

  getState(): SettingsState {
    return {
      ...this.state,
      settings: { ...this.state.settings },
      initialSettings: { ...this.state.initialSettings },
      fieldErrors: { ...this.state.fieldErrors },
    };
  }

  private setState(partial: Partial<SettingsState>): void {
    this.state = {
      ...this.state,
      ...partial,
      fieldErrors: partial.fieldErrors ?? this.state.fieldErrors,
      settings: partial.settings
        ? { ...partial.settings }
        : this.state.settings,
      initialSettings: partial.initialSettings
        ? { ...partial.initialSettings }
        : this.state.initialSettings,
    };

    this.dispatchEvent(
      new CustomEvent<SettingsState>('stateChange', {
        detail: this.getState(),
      })
    );
  }

  /**
   * Fetches settings from GET /api/v1/settings and synchronizes state.
   */
  async loadSettings(options?: { signal?: AbortSignal }): Promise<void> {
    this.setState({ loading: true, error: null, successMessage: null });

    try {
      const response = await getSettings(options);
      const data: SettingsResponse = response.data;

      const loaded = {
        maxConcurrency: data.maxConcurrency ?? DEFAULT_CONCURRENCY,
        audioQuality: data.audioQuality ?? DEFAULT_AUDIO_QUALITY,
      };

      this.setState({
        settings: { ...loaded },
        initialSettings: { ...loaded },
        loading: false,
        isDirty: false,
        fieldErrors: {},
      });
    } catch (err: unknown) {
      const errorMessage =
        err instanceof ApiProblemError
          ? err.problem.detail || err.problem.title
          : err instanceof NetworkError
            ? err.message
            : 'Error al cargar los ajustes del servidor.';

      this.setState({
        loading: false,
        error: errorMessage,
      });
    }
  }

  /**
   * Updates an in-memory draft field and calculates dirty state.
   */
  updateField(
    field: 'maxConcurrency' | 'audioQuality',
    value: number | string
  ): void {
    const updatedSettings = {
      ...this.state.settings,
      [field]: field === 'maxConcurrency' ? Number(value) : String(value),
    };

    const isDirty =
      updatedSettings.maxConcurrency !==
        this.state.initialSettings.maxConcurrency ||
      updatedSettings.audioQuality !== this.state.initialSettings.audioQuality;

    const remainingFieldErrors = { ...this.state.fieldErrors };
    delete remainingFieldErrors[field];

    this.setState({
      settings: updatedSettings,
      fieldErrors: remainingFieldErrors,
      error: null,
      successMessage: null,
      isDirty,
    });
  }

  /**
   * Resets the form draft back to the last saved initial settings.
   */
  resetToInitial(): void {
    this.setState({
      settings: { ...this.state.initialSettings },
      fieldErrors: {},
      error: null,
      successMessage: null,
      isDirty: false,
    });
  }

  /**
   * Validates client-side and saves settings to PUT /api/v1/settings.
   */
  async saveSettings(): Promise<boolean> {
    if (this.state.saving) {
      return false;
    }

    // Client-side validation first
    const validation = validateSettings(this.state.settings);
    if (!validation.valid) {
      this.setState({
        fieldErrors: validation.errors,
        error: 'Por favor corrija los errores indicados en el formulario.',
        successMessage: null,
      });
      return false;
    }

    this.setState({
      saving: true,
      error: null,
      successMessage: null,
      fieldErrors: {},
    });

    try {
      const response = await updateSettings({
        maxConcurrency: this.state.settings.maxConcurrency,
        audioQuality: this.state.settings.audioQuality,
      });

      const savedData = response.data;
      const synced = {
        maxConcurrency:
          savedData.maxConcurrency ?? this.state.settings.maxConcurrency,
        audioQuality:
          savedData.audioQuality ?? this.state.settings.audioQuality,
      };

      this.setState({
        settings: { ...synced },
        initialSettings: { ...synced },
        saving: false,
        isDirty: false,
        successMessage: 'Ajustes guardados correctamente en el servidor.',
      });

      return true;
    } catch (err: unknown) {
      if (err instanceof ApiProblemError) {
        const mapped = mapBackendSettingsError(
          err.code,
          err.problem.detail || err.problem.title
        );

        const newFieldErrors = { ...this.state.fieldErrors };
        if (mapped.field) {
          newFieldErrors[mapped.field] = mapped.message;
        }

        this.setState({
          saving: false,
          fieldErrors: newFieldErrors,
          error: mapped.message,
        });
        return false;
      }

      const errorMessage =
        err instanceof NetworkError
          ? err.message
          : err instanceof Error
            ? err.message
            : 'Error al guardar los ajustes en el servidor.';

      this.setState({
        saving: false,
        error: errorMessage,
      });
      return false;
    }
  }
}

export const settingsStore = new SettingsStore();
