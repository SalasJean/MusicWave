ALTER TABLE outbox_event
    ADD COLUMN trace_id VARCHAR(32),
    ADD COLUMN span_id VARCHAR(16);

ALTER TABLE worker_inbox
    ADD COLUMN trace_id VARCHAR(32),
    ADD COLUMN span_id VARCHAR(16);

ALTER TABLE outbox_event
    ADD CONSTRAINT outbox_event_trace_context_check CHECK (
        (trace_id IS NULL AND span_id IS NULL)
        OR (trace_id ~ '^[0-9a-f]{32}$' AND span_id ~ '^[0-9a-f]{16}$')
    );

ALTER TABLE worker_inbox
    ADD CONSTRAINT worker_inbox_trace_context_check CHECK (
        (trace_id IS NULL AND span_id IS NULL)
        OR (trace_id ~ '^[0-9a-f]{32}$' AND span_id ~ '^[0-9a-f]{16}$')
    );
