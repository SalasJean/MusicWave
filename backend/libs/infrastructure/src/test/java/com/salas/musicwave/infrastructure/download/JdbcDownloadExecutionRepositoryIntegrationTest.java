package com.salas.musicwave.infrastructure.download;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salas.musicwave.application.download.DownloadQuotaExceededException;
import com.salas.musicwave.application.download.DownloadRequestType;
import com.salas.musicwave.application.download.NewDownloadBatch;
import com.salas.musicwave.application.download.OwnerIdentity;
import com.salas.musicwave.application.execution.QueuedDownloadWork;
import com.salas.musicwave.application.execution.QueueClearResult;
import com.salas.musicwave.application.queue.DeliveryAcceptance;
import com.salas.musicwave.application.queue.DownloadDispatchCommand;
import com.salas.musicwave.domain.download.DownloadJob;
import com.salas.musicwave.domain.download.SourceUrl;
import com.salas.musicwave.infrastructure.queue.JdbcQueueRepository;

import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "MUSICWAVE_INTEGRATION_DB_URL", matches = ".+")
class JdbcDownloadExecutionRepositoryIntegrationTest {

    @Test
    void serializesConcurrentCreationSoTheOwnerQuotaCannotBeExceeded() throws Exception {
        DriverManagerDataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        JdbcDownloadJobRepository repository = new JdbcDownloadJobRepository(jdbc, 1);
        TransactionTemplate transactions = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource));
        UUID ownerId = UUID.randomUUID();
        OwnerIdentity owner = new OwnerIdentity(ownerId, "quota-" + ownerId);
        Instant now = Instant.now();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> createConcurrently(
                    transactions, repository, batch(owner, now, "a"), ready, start));
            var second = executor.submit(() -> createConcurrently(
                    transactions, repository, batch(owner, now, "b"), ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<String> results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter("created"::equals).count());
            assertEquals(1, results.stream().filter("quota"::equals).count());
            assertEquals(1, jdbc.queryForObject(
                    "SELECT count(*) FROM download_job WHERE owner_id = ?", Integer.class, ownerId));
        } finally {
            start.countDown();
            executor.shutdownNow();
            jdbc.update("DELETE FROM download_job WHERE owner_id = ?", ownerId);
            jdbc.update("DELETE FROM download_request WHERE owner_id = ?", ownerId);
            jdbc.update("DELETE FROM app_user WHERE id = ?", ownerId);
        }
    }

    @Test
    void claimsOneReadyJobPerOwnerBeforeGivingEitherOwnerAnotherSlot() {
        JdbcTemplate jdbc = jdbcTemplate();
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        UUID requestA = UUID.randomUUID();
        UUID requestB = UUID.randomUUID();
        UUID jobA1 = UUID.randomUUID();
        UUID jobA2 = UUID.randomUUID();
        UUID jobB1 = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            insertOwner(jdbc, ownerA, "fair-a-" + ownerA, now);
            insertOwner(jdbc, ownerB, "fair-b-" + ownerB, now);
            insertRequest(jdbc, requestA, ownerA, now);
            insertRequest(jdbc, requestB, ownerB, now);
            insertJob(jdbc, jobA1, requestA, ownerA, 0, now.minusSeconds(3));
            insertJob(jdbc, jobA2, requestA, ownerA, 1, now.minusSeconds(2));
            insertJob(jdbc, jobB1, requestB, ownerB, 0, now.minusSeconds(1));
            insertInbox(jdbc, jobA1, now.minusSeconds(3));
            insertInbox(jdbc, jobA2, now.minusSeconds(2));
            insertInbox(jdbc, jobB1, now.minusSeconds(1));

            List<QueuedDownloadWork> claimed = new JdbcDownloadExecutionRepository(jdbc)
                    .claimReady("fair-worker", now, Duration.ofMinutes(1), 5, 1);

            assertEquals(2, claimed.size());
            assertEquals(2, claimed.stream().map(QueuedDownloadWork::ownerId).distinct().count());
            assertEquals(1, jdbc.queryForObject(
                    "SELECT count(*) FROM worker_inbox WHERE processing_status = 'RECEIVED' AND job_id IN (?, ?, ?)",
                    Integer.class, jobA1, jobA2, jobB1));
        } finally {
            jdbc.update("DELETE FROM worker_inbox WHERE job_id IN (?, ?, ?)", jobA1, jobA2, jobB1);
            jdbc.update("DELETE FROM download_job WHERE id IN (?, ?, ?)", jobA1, jobA2, jobB1);
            jdbc.update("DELETE FROM download_request WHERE id IN (?, ?)", requestA, requestB);
            jdbc.update("DELETE FROM app_user WHERE id IN (?, ?)", ownerA, ownerB);
        }
    }

    @Test
    void appliesPersistedOwnerConcurrencyAndQualityWhileKeepingDeploymentCeiling() {
        JdbcTemplate jdbc = jdbcTemplate();
        UUID owner = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            insertOwner(jdbc, owner, "settings-" + owner, now);
            jdbc.update("""
                    INSERT INTO user_settings (user_id, max_concurrency, audio_quality)
                    VALUES (?, 1, '320K')
                    """, owner);
            insertRequest(jdbc, request, owner, now);
            insertJob(jdbc, first, request, owner, 0, now.minusSeconds(2));
            insertJob(jdbc, second, request, owner, 1, now.minusSeconds(1));
            insertInbox(jdbc, first, now.minusSeconds(2));
            insertInbox(jdbc, second, now.minusSeconds(1));

            List<QueuedDownloadWork> claimed = new JdbcDownloadExecutionRepository(jdbc)
                    .claimReady("settings-worker", now, Duration.ofMinutes(1), 5, 5);

            assertEquals(1, claimed.size());
            assertEquals("320K", claimed.get(0).audioQuality());
        } finally {
            jdbc.update("DELETE FROM worker_inbox WHERE job_id IN (?, ?)", first, second);
            jdbc.update("DELETE FROM download_job WHERE id IN (?, ?)", first, second);
            jdbc.update("DELETE FROM download_request WHERE id = ?", request);
            jdbc.update("DELETE FROM app_user WHERE id = ?", owner);
        }
    }

    @Test
    void duplicateBrokerDeliveryCreatesOnlyOneDurableInboxReceipt() {
        JdbcTemplate jdbc = jdbcTemplate();
        UUID owner = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        UUID job = UUID.randomUUID();
        UUID message = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            insertOwner(jdbc, owner, "duplicate-" + owner, now);
            insertRequest(jdbc, request, owner, now);
            insertJob(jdbc, job, request, owner, 0, now);
            JdbcQueueRepository repository = new JdbcQueueRepository(jdbc, new ObjectMapper());
            DownloadDispatchCommand command = new DownloadDispatchCommand(message, job, 0, now);

            assertEquals(DeliveryAcceptance.ACCEPTED,
                    repository.acceptDelivery(command, "worker-a", now));
            assertEquals(DeliveryAcceptance.DUPLICATE,
                    repository.acceptDelivery(command, "worker-b", now.plusSeconds(1)));
            assertEquals(1, jdbc.queryForObject(
                    "SELECT count(*) FROM worker_inbox WHERE job_id = ?", Integer.class, job));
        } finally {
            jdbc.update("DELETE FROM worker_inbox WHERE job_id = ?", job);
            jdbc.update("DELETE FROM download_job WHERE id = ?", job);
            jdbc.update("DELETE FROM download_request WHERE id = ?", request);
            jdbc.update("DELETE FROM app_user WHERE id = ?", owner);
        }
    }

    @Test
    void clearingVisibleQueueKeepsRunningRowUntilWorkerCompletesCancellation() {
        JdbcTemplate jdbc = jdbcTemplate();
        UUID owner = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        UUID job = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            insertOwner(jdbc, owner, "clear-" + owner, now);
            insertRequest(jdbc, request, owner, now);
            insertJob(jdbc, job, request, owner, 0, now);
            insertInbox(jdbc, job, now);
            jdbc.update("""
                    UPDATE worker_inbox SET processing_status = 'PROCESSING',
                        claimed_by = 'clear-worker', claim_until = ? WHERE job_id = ?
                    """, Timestamp.from(now.plusSeconds(60)), job);
            jdbc.update("UPDATE download_job SET status = 'DOWNLOADING' WHERE id = ?", job);
            JdbcDownloadExecutionRepository repository = new JdbcDownloadExecutionRepository(jdbc);

            QueueClearResult result = repository.clearAll(owner, now.plusSeconds(1));

            assertEquals(1, result.clearedCount());
            assertEquals(1, result.cancellationRequestedCount());
            assertEquals(Boolean.FALSE, jdbc.queryForObject(
                    "SELECT visible FROM download_job WHERE id = ?", Boolean.class, job));
            assertTrue(repository.markCancelled(job, "cancelled after clear", now.plusSeconds(2)));
            assertEquals("CANCELLED", jdbc.queryForObject(
                    "SELECT status FROM download_job WHERE id = ?", String.class, job));
        } finally {
            jdbc.update("DELETE FROM worker_inbox WHERE job_id = ?", job);
            jdbc.update("DELETE FROM download_job WHERE id = ?", job);
            jdbc.update("DELETE FROM download_request WHERE id = ?", request);
            jdbc.update("DELETE FROM app_user WHERE id = ?", owner);
        }
    }

    private JdbcTemplate jdbcTemplate() {
        return new JdbcTemplate(dataSource());
    }

    private DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl(System.getenv("MUSICWAVE_INTEGRATION_DB_URL"));
        dataSource.setUsername(System.getenv().getOrDefault("MUSICWAVE_INTEGRATION_DB_USER", "musicwave"));
        dataSource.setPassword(System.getenv().getOrDefault("MUSICWAVE_INTEGRATION_DB_PASSWORD", "musicwave"));
        return dataSource;
    }

    private String createConcurrently(TransactionTemplate transactions,
                                      JdbcDownloadJobRepository repository,
                                      NewDownloadBatch batch,
                                      CountDownLatch ready,
                                      CountDownLatch start) {
        try {
            return transactions.execute(status -> {
                ready.countDown();
                try {
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("concurrent start timed out");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                repository.create(batch);
                return "created";
            });
        } catch (DownloadQuotaExceededException exception) {
            return "quota";
        }
    }

    private NewDownloadBatch batch(OwnerIdentity owner, Instant now, String suffix) {
        UUID requestId = UUID.randomUUID();
        return new NewDownloadBatch(
                requestId,
                owner,
                DownloadRequestType.SINGLE,
                "quota-" + suffix,
                suffix.repeat(64),
                now,
                List.of(DownloadJob.pending(
                        UUID.randomUUID(), owner.id(), requestId, 0,
                        SourceUrl.parse("https://example.com/" + suffix), now)));
    }

    private void insertOwner(JdbcTemplate jdbc, UUID id, String subject, Instant now) {
        jdbc.update("INSERT INTO app_user (id, external_subject, created_at) VALUES (?, ?, ?)",
                id, subject, Timestamp.from(now));
    }

    private void insertRequest(JdbcTemplate jdbc, UUID id, UUID owner, Instant now) {
        jdbc.update("""
                INSERT INTO download_request
                    (id, owner_id, request_type, request_fingerprint, created_at)
                VALUES (?, ?, 'BULK', ?, ?)
                """, id, owner, "0".repeat(64), Timestamp.from(now));
    }

    private void insertJob(JdbcTemplate jdbc,
                           UUID id,
                           UUID request,
                           UUID owner,
                           int position,
                           Instant createdAt) {
        jdbc.update("""
                INSERT INTO download_job
                    (id, owner_id, source_url, title, status, progress, request_id,
                     request_index, title_status, created_at, updated_at)
                VALUES (?, ?, 'https://example.com/audio', 'audio', 'QUEUED', 0,
                        ?, ?, 'NOT_APPLICABLE', ?, ?)
                """, id, owner, request, position,
                Timestamp.from(createdAt), Timestamp.from(createdAt));
    }

    private void insertInbox(JdbcTemplate jdbc, UUID job, Instant receivedAt) {
        jdbc.update("""
                INSERT INTO worker_inbox
                    (message_id, job_id, delivery_attempt, first_received_by, received_at)
                VALUES (?, ?, 1, 'integration-test', ?)
                """, UUID.randomUUID(), job, Timestamp.from(receivedAt));
    }
}
