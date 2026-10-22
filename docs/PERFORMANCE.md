# Measured local capacity

Measured 2026-10-11 on Linux, AMD Ryzen 7 5700U (8 cores/16 threads), 14 GiB RAM, Docker Engine, the full eleven-service local topology, Java 17 and the pinned `grafana/k6:2.3.0` image. These are repeatable development measurements, not a production SLO or a promise for thousands of users.

## Results

| Surface | Profile | Result |
|---|---|---|
| Read API | 25 constant virtual users, 30 s; status, tools and 20-row download page per iteration | 21,609/21,609 HTTP requests succeeded; 718.44 req/s; p95 5.64 ms; max 107.45 ms |
| Queue control | 5 virtual users, 50 idempotent empty `queue/start` operations | 50/50 succeeded; 85.48 req/s; p95 15.82 ms; max 16 ms |
| SSE | 100 simultaneous idle connections held through a heartbeat | 100 accepted; connection 101 rejected with the designed HTTP 503 capacity response |
| Worker scheduler | Five blocking deterministic executions offered to a worker configured for five slots | Active gauge reached exactly 5; a second dispatch made no additional claim |
| Per-owner worker fairness | PostgreSQL integration with owner preference 1 and deployment ceiling 5 | Exactly one job claimed; its persisted `320K` preference reached the execution command |

## Reproduction

```bash
MUSICWAVE_POSTGRES_PORT=55432 docker compose -f infrastructure/compose.yaml --profile load run --rm loadtest
MUSICWAVE_POSTGRES_PORT=55432 docker compose -f infrastructure/compose.yaml --profile load run --rm loadtest run /scripts/queue-control.js
infrastructure/load/sse-capacity.sh
cd backend && ./gradlew :apps:worker:test --tests '*DownloadExecutionDispatcherTest' --no-daemon
```

## 2026-10-12 concurrency amendment

The configurable local worker and per-owner ceilings changed from five/two to ten/ten so a user preference of 10 can admit ten independent jobs and leave later work queued. The deterministic scheduler test now reaches exactly ten active executions and refuses an additional claim.

This is a functional bound, not a new throughput measurement. The earlier five-process result remains the only measured local real-media envelope. Ten simultaneous yt-dlp/FFmpeg executions must be re-measured with representative media, bandwidth and VPS CPU/memory before production rollout; deployment can lower both environment values without changing the API.

## Safe local envelope

- Keep this single-node development profile at or below 25 concurrent read clients and 500 mixed read requests/s if the objective is to preserve generous headroom relative to the observed run.
- The SSE hard limit is 100. Plan for at most 80 persistent clients per API replica until a VPS-specific soak test establishes a new limit; clients must reconnect and rebuild from REST after 503 or stream reset.
- The current configured hard limit is 10 local processes and 10 per owner, while the last measured real-media envelope remains 5. CPU, bandwidth, upstream throttling and media duration dominate actual completion throughput, so no downloads/minute guarantee is stated and production may use a lower ceiling.
- Re-run every profile on the target VPS with production TLS/proxy/JWT enabled. Scale only from those measurements and database/broker/CPU evidence.
