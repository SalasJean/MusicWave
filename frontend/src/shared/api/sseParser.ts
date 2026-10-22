/**
 * Parser for text/event-stream according to the W3C Server-Sent Events specification.
 * Handles chunk fragmentation, multiple events per chunk, heartbeats, and custom event types.
 */

export interface SseRawEvent {
  id?: string;
  event: string;
  data: string;
  retry?: number;
}

export interface SseParserState {
  buffer: string;
  currentEvent: string;
  currentData: string[];
  currentId?: string;
  currentRetry?: number;
}

export function createSseParserState(): SseParserState {
  return {
    buffer: '',
    currentEvent: 'message',
    currentData: [],
  };
}

export interface SseParseCallbacks {
  onEvent: (event: SseRawEvent) => void;
  onComment?: (comment: string) => void;
  onRetry?: (retryMs: number) => void;
}

/**
 * Feeds a new text chunk into the parser state.
 * Emits events as complete message blocks are detected.
 */
export function feedSseChunk(
  chunk: string,
  state: SseParserState,
  callbacks: SseParseCallbacks
): void {
  // Normalize \r\n and \r to \n
  state.buffer += chunk.replace(/\r\n/g, '\n').replace(/\r/g, '\n');

  let newlineIndex: number;
  while ((newlineIndex = state.buffer.indexOf('\n')) !== -1) {
    const line = state.buffer.slice(0, newlineIndex);
    state.buffer = state.buffer.slice(newlineIndex + 1);

    parseSseLine(line, state, callbacks);
  }
}

/**
 * Parses a single line of SSE stream.
 */
function parseSseLine(
  line: string,
  state: SseParserState,
  callbacks: SseParseCallbacks
): void {
  // 1. Empty line signals dispatch of the accumulated event
  if (line === '') {
    dispatchCurrentEvent(state, callbacks);
    return;
  }

  // 2. Lines starting with ':' are comments (e.g. heartbeats)
  if (line.startsWith(':')) {
    const comment = line.slice(1).trimStart();
    if (callbacks.onComment) {
      callbacks.onComment(comment);
    }
    return;
  }

  // 3. Parse field: value
  const colonIndex = line.indexOf(':');
  let field: string;
  let value: string;

  if (colonIndex === -1) {
    field = line;
    value = '';
  } else {
    field = line.slice(0, colonIndex);
    value = line.slice(colonIndex + 1);
    // Spec: If value starts with a single space, remove it
    if (value.startsWith(' ')) {
      value = value.slice(1);
    }
  }

  switch (field) {
    case 'event':
      state.currentEvent = value;
      break;

    case 'data':
      state.currentData.push(value);
      break;

    case 'id':
      // The event ID cannot contain null character
      if (!value.includes('\0')) {
        state.currentId = value;
      }
      break;

    case 'retry': {
      const retryMs = parseInt(value, 10);
      if (!isNaN(retryMs) && retryMs >= 0) {
        state.currentRetry = retryMs;
        if (callbacks.onRetry) {
          callbacks.onRetry(retryMs);
        }
      }
      break;
    }

    default:
      // Unknown fields are ignored per specification
      break;
  }
}

function dispatchCurrentEvent(
  state: SseParserState,
  callbacks: SseParseCallbacks
): void {
  // If data buffer is empty, no event is dispatched
  if (state.currentData.length === 0) {
    state.currentEvent = 'message';
    return;
  }

  const rawEvent: SseRawEvent = {
    event: state.currentEvent,
    data: state.currentData.join('\n'),
    id: state.currentId,
    retry: state.currentRetry,
  };

  // Reset per-event fields; per spec, 'id' is retained until changed
  state.currentEvent = 'message';
  state.currentData = [];

  callbacks.onEvent(rawEvent);
}
