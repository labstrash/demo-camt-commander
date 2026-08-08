package com.example.commander.port;

import com.example.commander.domain.deadletter.DeadLetterMessage;
import java.time.Instant;
import java.util.List;

/**
 * Repository for {@code CAMT.DeadLetterMessage} — messages {@link
 * com.example.commander.adapter.message.ResilientMqSender} couldn't deliver (retries
 * exhausted, permanent failure, or the circuit breaker was open), for a later active
 * recovery job to retry (deferred until Quartz exists — see the Phase 3 doc's Decision 6).
 */
public interface DeadLetterMessageRepository {

    /**
     * Inserts a new dead-letter row with {@code status='PENDING_RETRY'} and
     * {@code retry_count=0}.
     *
     * @param row the fields to insert
     */
    void insert(DeadLetterMessage row);

    /**
     * Finds rows due for a recovery attempt: {@code status='PENDING_RETRY'} and
     * {@code next_retry_at <= now}, oldest first.
     *
     * @param limit maximum number of rows to return
     * @return the due rows, oldest {@code next_retry_at} first
     */
    List<DeadLetterMessage> findDueForRetry(int limit);

    /**
     * Deletes a row once it's been successfully recovered — a resent message needs no
     * further tracking, so it's removed rather than kept around with a terminal status.
     *
     * @param id the row's surrogate ID
     */
    void delete(long id);

    /**
     * Records a failed recovery attempt that hasn't yet exhausted {@code max_retries} —
     * updates {@code retry_count}, {@code next_retry_at}, {@code last_error}, and
     * {@code updated_at}, leaving {@code status='PENDING_RETRY'}.
     *
     * @param id the row's surrogate ID
     * @param retryCount the new attempt count
     * @param nextRetryAt when the next recovery attempt becomes eligible
     * @param lastError the failure's message
     */
    void markRetryScheduled(long id, int retryCount, Instant nextRetryAt, String lastError);

    /**
     * Marks a row terminally failed: {@code status='FAILED'}, {@code retry_count} set to its
     * final value, {@code last_error}, and {@code updated_at}. No further recovery attempts
     * are made — this is for manual/dashboard attention.
     *
     * @param id the row's surrogate ID
     * @param retryCount the final attempt count
     * @param lastError the failure's message
     */
    void markFailed(long id, int retryCount, String lastError);
}
