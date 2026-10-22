# MusicWave manual API verification

This runbook validates the complete stable v1 backend contract manually and supports release and regression testing of the integrated application.

## Recommended client

Use the **Postman desktop app** for this manual pass. It keeps requests, variables, response tests and the execution order together, supports multipart uploads, and displays server-sent events as they arrive. MusicWave includes a ready-to-import collection and local environment:

- `docs/testing/postman/MusicWave.postman_collection.json`
- `docs/testing/postman/MusicWave.local.postman_environment.json`
- `docs/testing/sample-urls.txt`

The frozen OpenAPI contract remains authoritative at `docs/openapi/musicwave-api-v1.json`. It can also be imported into Postman, but the supplied collection is preferable for this pass because it includes the stateful test order, expected response checks and automatic ID capture.

Official Postman references:

- [Import an OpenAPI definition](https://learning.postman.com/v11/docs/getting-started/importing-and-exporting/importing-from-swagger/)
- [Run requests in a collection](https://learning.postman.com/docs/tests-and-scripts/running-collections/intro-to-collection-runs/)
- [Use environment variables](https://learning.postman.com/docs/use/send-requests/variables/environment-variables/)
- [Inspect server-sent events](https://learning.postman.com/latest-v-12/docs/use/send-requests/response-data/responses/#server-sent-events)

## 1. Start and verify the local stack

Run from the `musicWave` directory. Port `55432` is intentional because port `5432` was already occupied in the verified local environment.

```bash
MUSICWAVE_POSTGRES_PORT=55432 docker compose -f infrastructure/compose.yaml up -d --build
docker compose -f infrastructure/compose.yaml ps
curl --silent --show-error http://localhost:8080/actuator/health
curl --silent --show-error http://localhost:8080/api/v1/tools/status
```

Continue only when the API health is `UP`, all Compose services are healthy and `/api/v1/tools/status` reports `READY`. The worker heartbeat can take a few seconds after startup.

The Compose development profile explicitly uses local-owner security. No JWT is needed locally, so leave `bearerToken` empty. If the API is later started in JWT mode, place an externally issued valid token in that variable; MusicWave validates tokens but does not issue them.

## 2. Import and prepare Postman

1. Open Postman Desktop and choose **Import**.
2. Import both JSON files in `docs/testing/postman/`.
3. Select the **MusicWave Local** environment in the upper-right environment selector.
4. Confirm `baseUrl` is `http://localhost:8080`.
5. Leave `runId` empty. The first request creates one and all idempotency tests reuse it.
6. In `02 - Ingestion and queries / Import TXT`, select `docs/testing/sample-urls.txt` as the `file` form-data value. Postman intentionally does not embed a local file in the exported collection.

If the same imported environment is reused for a fresh database test, clear `runId` and every captured `...JobId` value first. You may replace `validMediaUrl` with another public HTTP(S) media URL supported by yt-dlp. Never use private, loopback or local-network destinations except in the explicit rejection test.

## 3. Recommended execution order

Send the requests manually in the collection order. Do not run the entire collection unattended: downloads are asynchronous, the TXT upload needs a local file selection, and the SSE request remains open.

### A. Preflight

Run folder `00 - Preflight`.

- Actuator health: HTTP 200 and `status=UP`.
- System status: HTTP 200.
- Tool status: HTTP 200 and top-level `status=READY`.

### B. Settings

Run folder `01 - Settings`.

1. Read the current settings.
2. Update to `maxConcurrency=2` and `audioQuality=128K`.
3. Read again and confirm both persisted values.
4. Restore `maxConcurrency=5` and `audioQuality=192K`.

The supported concurrency range is 1–10. Supported quality values are `64K`, `96K`, `128K`, `160K`, `192K`, `256K` and `320K`.

### C. Ingestion, idempotency and ownership-scoped reads

Run folder `02 - Ingestion and queries` from top to bottom.

- **Create single**: HTTP 201, one `PENDING` job. The script stores `singleJobId` and `singleRequestId`.
- **Replay single**: HTTP 200, `replayed=true`, and the same request/job IDs.
- **Conflict same key**: HTTP 409 with `IDEMPOTENCY_KEY_REUSED` because the key is reused with a different payload.
- **List downloads**: HTTP 200 and the new job is visible.
- **Get single download**: HTTP 200 for `singleJobId`.
- **Artifact before completion**: HTTP 409 with `ARTIFACT_NOT_READY`.
- **Create then cancel fixture**: creation returns 201 and stores `cancelJobId`; cancellation returns 200 with `status=CANCELLED`.
- **Bulk create**: creates the valid entry and reports `not-a-url` in `rejected`.
- **Import TXT**: creates valid lines and reports the invalid line in `rejected`.
- **Create failure fixture**: stores `failureJobId`; its public but unsupported media page should later make yt-dlp produce a durable `FAILED` job.

Bulk ingestion accepts at most 100 entries. TXT import accepts valid UTF-8 up to 256 KiB, ignores blank lines and preserves one-based line positions for rejections.

### D. Open the realtime stream

Open `03 - Realtime events / Live event stream` in a separate Postman tab and leave it connected. Postman displays each SSE message as it arrives. A fresh connection starts at the Redis stream tail, so open it **before** starting the queue.

Then continue with folder `04 - Queue, execution, artifact and retry` in another tab:

1. **Start queue** returns HTTP 202 when pending jobs are queued.
2. **Start queue again** returns HTTP 200 with `queuedCount=0`.
3. Observe `download` events and heartbeats in the SSE tab.
4. Poll the saved GET requests until each job reaches `COMPLETED`, `FAILED` or `CANCELLED`.

Execution depends on the external media site and local network. A slow or blocked provider is an environment result, not automatically an API defect. Preserve the job error and correlated logs before deciding.

For SSE replay, copy an observed event ID such as `1720000000000-4` to the `lastEventId` environment variable, stop the first stream, then send **Reconnect from Last-Event-ID**. The acknowledged event must not be duplicated. **Malformed Last-Event-ID** must return HTTP 400 with `INVALID_EVENT_CURSOR`.

### E. Artifact retrieval

When `singleJobId` is `COMPLETED`, send **Download completed MP3**.

- Expected HTTP 200.
- `Content-Type: audio/mpeg`.
- `Content-Disposition` contains a safe attachment filename.
- `X-Content-Type-Options: nosniff`.
- `Cache-Control` contains `no-store`.

Use Postman's **Save response to file** action if you want to play the resulting MP3. The response is one-shot: after Postman receives it completely, MusicWave deletes the S3 object and artifact row. Send **Confirm artifact was consumed** next; it must return HTTP 410 `ARTIFACT_ALREADY_DOWNLOADED`. If the job has not completed, HTTP 409 `ARTIFACT_NOT_READY` is correct. An unclaimed artifact expires after one hour by default and then returns `ARTIFACT_EXPIRED`.

### F. Failure and retry

Poll **Get failure fixture** until `failureJobId` reaches `FAILED`. Then send **Retry failed download**.

- Expected HTTP 200.
- The response status returns to `PENDING`.
- `retryCount` increases in the next detail response.
- A later `POST /queue/start` dispatches it again.

Retrying a job that is not `FAILED` must return HTTP 409 with `DOWNLOAD_NOT_RETRYABLE`.

### G. Queue cancellation and cleanup

To observe active cancellation clearly, create another job, start the queue, and immediately send **Cancel active queue**. Pending or queued jobs become `CANCELLED`; a running job records `cancellationRequested=true` and the worker ends its process tree asynchronously.

Finish with folder `05 - Cleanup`:

1. `DELETE /queue/completed` hides completed rows.
2. `DELETE /queue` cancels active work and hides every remaining visible row.
3. The final list should have `totalElements=0`.
4. Run **Restore settings** again if you changed them after the earlier restore.

Clearing retains execution/audit job rows but makes temporary MP3 objects immediately eligible for physical deletion. It does not retain a hidden music library.

After the isolated negative checks in folder `06`, send its final cleanup request. That folder creates one fresh pending fixture to verify the retry state conflict, so the last request hides it and leaves the visible queue empty again.

## 4. Stable endpoint checklist

| # | Method | Path | Normal success |
|---:|---|---|---|
| 1 | GET | `/api/v1/system/status` | 200 |
| 2 | GET | `/api/v1/tools/status` | 200 |
| 3 | POST | `/api/v1/downloads` | 201 new / 200 replay |
| 4 | POST | `/api/v1/downloads/bulk` | 201 new / 200 replay |
| 5 | POST | `/api/v1/downloads/import` | 201 new / 200 replay |
| 6 | GET | `/api/v1/downloads` | 200 |
| 7 | GET | `/api/v1/downloads/{id}` | 200 |
| 8 | POST | `/api/v1/downloads/{id}/cancel` | 200 terminal / 202 running |
| 9 | POST | `/api/v1/downloads/{id}/retry` | 200 |
| 10 | GET | `/api/v1/downloads/{id}/artifact` | 200 completed |
| 11 | POST | `/api/v1/queue/start` | 202 queued / 200 no-op |
| 12 | POST | `/api/v1/queue/cancel` | 200 |
| 13 | DELETE | `/api/v1/queue/completed` | 200 |
| 14 | DELETE | `/api/v1/queue` | 200 |
| 15 | GET | `/api/v1/settings` | 200 |
| 16 | PUT | `/api/v1/settings` | 200 |
| 17 | GET | `/api/v1/events` | 200 streaming |

`GET /actuator/health` is an additional operational preflight endpoint and is not counted among the 17 stable product operations.

## 5. Required negative checks

Folder `06 - Negative checks` contains the safe baseline cases:

| Case | Expected result |
|---|---|
| Invalid source URL | 400 problem detail |
| Loopback destination | 400 `URL_DESTINATION_NOT_ALLOWED` |
| Invalid settings | 400 problem detail |
| Unknown download UUID | 404 `DOWNLOAD_NOT_FOUND` |
| Artifact before completion | 409 `ARTIFACT_NOT_READY` |
| Retry in the wrong state | 409 `DOWNLOAD_NOT_RETRYABLE` |
| Second artifact request after successful delivery | 410 `ARTIFACT_ALREADY_DOWNLOADED` |
| Artifact past its configured TTL | 410 `ARTIFACT_EXPIRED` |
| Concurrent request for the same artifact | 409 `ARTIFACT_DELIVERY_IN_PROGRESS` |
| Malformed SSE cursor | 400 `INVALID_EVENT_CURSOR` |
| Reused key with different payload | 409 `IDEMPOTENCY_KEY_REUSED` |

Rate-limit exhaustion, two-owner isolation and JWT 401/403 checks are deliberately not part of this local-owner smoke pass. They require a controlled rate test or a JWT profile with valid tokens. Do not simulate ownership with request headers: the server ignores client-supplied owner identity by design.

## 6. Evidence to record

For every failure, save:

- Collection request name and UTC time.
- Expected and actual HTTP status.
- Request body and non-secret headers.
- Complete response body or SSE event.
- Response `X-Request-ID` header.
- `jobId`, when present.
- API and worker logs around the same request/job ID.

Useful log commands:

```bash
docker compose -f infrastructure/compose.yaml logs --since=10m api
docker compose -f infrastructure/compose.yaml logs --since=10m worker
```

Minimal report template:

```text
Request:
UTC time:
Expected:
Actual:
X-Request-ID:
jobId:
Response/error:
Reproducible: yes/no
```

## 7. Completion gate

The manual backend gate passes when all 17 operations have their documented success behavior, required negative cases produce their stable errors, SSE reflects the durable lifecycle, the completed artifact downloads as MP3, cleanup leaves the visible list empty, and no unexplained API/worker error remains. This gate should pass before accepting an integrated frontend release.
