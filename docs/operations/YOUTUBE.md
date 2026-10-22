# YouTube execution operations

This runbook records the verified recovery path for YouTube changes that affect one player client without necessarily affecting the whole host or egress IP.

## Runtime baseline

The worker image pins and checksum-verifies yt-dlp and Deno for amd64 and arm64. Deno is passed explicitly through `--js-runtimes deno`; FFmpeg remains responsible for MP3 extraction. A worker image build must fail if either downloaded checksum is wrong or a required executable cannot start.

The worker also reads `/etc/yt-dlp.conf`. Compose mounts the repository file `infrastructure/yt-dlp.conf` there as read-only. Its current provider compatibility setting is:

```text
--extractor-args "youtube:player_client=android,web"
```

Android is attempted before the default web client. The web client remains as a fallback.

Verify the live container with:

```bash
docker compose -f infrastructure/compose.yaml exec -T worker yt-dlp --version
docker compose -f infrastructure/compose.yaml exec -T worker deno --version
docker compose -f infrastructure/compose.yaml exec -T worker yt-dlp --verbose --simulate --no-playlist URL
```

The verbose output must include `JS runtimes: deno-...`.

## Resolved incident: web-client anti-bot challenge

On 2026-10-14, commercial music and long-mix URLs began failing through yt-dlp's default YouTube web client. The visible symptoms were:

```text
HTTP Error 429: Too Many Requests
Sign in to confirm you're not a bot
LOGIN_REQUIRED
SABR-only streaming warnings
```

The frontend was not the source of the failure: it displayed the persisted worker error correctly. The backend queue, conversion and delivery flow were also operating as designed; yt-dlp failed while negotiating playback with YouTube.

The first investigation confirmed that yt-dlp was already current. Deno was then added because the verbose output showed no supported JavaScript runtime, but the affected URL still failed through the web client. That result alone did **not** prove a general IP block.

The decisive differential test selected YouTube's Android client before web:

```bash
docker compose -f infrastructure/compose.yaml exec -T worker \
  yt-dlp --verbose --simulate --no-playlist \
  --extractor-args "youtube:player_client=android,web" URL
```

The test URL `Avicii ◢◤ Mix Mejores Canciones ◢◤` resolved immediately. The subsequent end-to-end job reached 100%, converted to MP3 and completed in approximately four seconds. This isolated the incident to the web player path/restrictions in that test, rather than the frontend, Spring services or a proven host-wide IP restriction.

The permanent fix is the read-only configuration mount already present in Compose:

```yaml
volumes:
  - media-data:/var/lib/musicwave/media
  - ./yt-dlp.conf:/etc/yt-dlp.conf:ro
```

When this mount or file changes, recreate only the worker:

```bash
docker compose -f infrastructure/compose.yaml up -d --no-deps --force-recreate worker
docker compose -f infrastructure/compose.yaml ps worker
```

No application rebuild is required for a bind-mounted configuration-only change. A rebuild is still required when the worker image, Java code, yt-dlp version or Deno version changes.

## Diagnostic procedure for a recurrence

1. Preserve the exact frontend error, job ID and timestamp. Confirm whether the frontend merely displayed a backend failure.
2. Inspect correlated worker logs and look for `429`, `LOGIN_REQUIRED`, bot confirmation, SABR, missing JavaScript runtime or unsupported-client messages.
3. Confirm the mounted configuration and runtime:

   ```bash
   docker compose -f infrastructure/compose.yaml exec -T worker \
     sh -c 'cat /etc/yt-dlp.conf && yt-dlp --version && deno --version'
   ```

4. Run a non-downloading verbose probe with the mounted configuration:

   ```bash
   docker compose -f infrastructure/compose.yaml exec -T worker \
     yt-dlp --verbose --simulate --no-playlist URL
   ```

5. Verify an end-to-end download through the normal API/frontend. A metadata-only probe does not validate MP3 conversion, artifact delivery or cleanup.
6. If Android and web both fail, investigate the next layer: a provider change, PO Token enforcement, authentication requirement, temporary rate limit or egress-IP reputation. Do not conclude that the IP is the cause until client-specific behavior has been separated.

The known-good acceptance evidence for this incident was: worker healthy, yt-dlp exit code `0`, MP3 conversion complete, normal lifecycle completion and the reported frontend suite of 139 tests green.

## Expected provider failures

- `YOUTUBE_RATE_LIMITED`: YouTube returned HTTP 429. First determine whether it affects only the selected player client. If every configured client fails, avoid repeated retries and inspect traffic volume and egress reputation.
- `YOUTUBE_AUTH_REQUIRED`: YouTube required a signed-in or trusted session and no HTTP 429 was observed. Confirm whether the Android-first fallback still works before considering authentication.
- `YT_DLP_EXIT_CODE`: another yt-dlp extraction failure; inspect the sanitized error detail and worker logs.

Deno solves JavaScript challenge execution but is independent from player-client selection. A PO Token provider may help with formats that enforce Proof of Origin, but neither yt-dlp nor the featured BgUtils provider promises that it bypasses IP-based login restrictions.

## Secret policy

Never bake browser cookies, account credentials or exported cookie files into the image, repository, logs or frontend. Do not share one personal YouTube account across public users. Any future authenticated mode requires a separate threat model, encrypted secret delivery, rotation, revocation and an explicit operator decision; it is not enabled by default.

For a VPS, deploy the pinned runtime together with the read-only yt-dlp configuration and run the non-downloading verbose probe. Player-client behavior may change again because it is controlled upstream by YouTube; `android,web` is a verified compatibility strategy, not a permanent guarantee for every URL. If every configured client receives HTTP 429 or `LOGIN_REQUIRED`, scaling workers on the same IP increases pressure and is not a fix. Keep provider-specific concurrency conservative.
