package recargapay.wallet.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import recargapay.wallet.application.exception.IdempotencyKeyConflictException;
import recargapay.wallet.application.exception.MissingIdempotencyKeyException;
import recargapay.wallet.application.idempotency.IdempotencyDecision;
import recargapay.wallet.application.idempotency.IdempotencyRecord;
import recargapay.wallet.application.idempotency.IdempotencyStatus;
import recargapay.wallet.application.port.IdempotencyRepositoryPort;

@Service
public class IdempotencyService {
    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final int MAX_KEY_LENGTH = 255;
    private static final Duration TTL = Duration.ofHours(24);

    private final IdempotencyRepositoryPort idempotencyRepositoryPort;

    public IdempotencyService(IdempotencyRepositoryPort idempotencyRepositoryPort) {
        this.idempotencyRepositoryPort = idempotencyRepositoryPort;
    }

    @Transactional
    public IdempotencyDecision begin(String rawIdempotencyKey, String requestHash) {
        String idempotencyKey = normalizeIdempotencyKey(rawIdempotencyKey);
        Instant now = Instant.now();

        IdempotencyRecord existingRecord = idempotencyRepositoryPort
                .findByIdempotencyKeyForUpdate(idempotencyKey)
                .orElse(null);
        if (existingRecord != null) {
            return resolveExistingRecord(existingRecord, requestHash, now);
        }

        return createRecord(idempotencyKey, requestHash, now);
    }

    @Transactional
    public void complete(String rawIdempotencyKey, int responseStatus, String responseContentType, String responseBody) {
        String idempotencyKey = normalizeIdempotencyKey(rawIdempotencyKey);
        IdempotencyRecord record = idempotencyRepositoryPort
                .findByIdempotencyKeyForUpdate(idempotencyKey)
                .orElseThrow(() -> new MissingIdempotencyKeyException("idempotency record not found"));

        record.setStatus(responseStatus >= 400 ? IdempotencyStatus.FAILED : IdempotencyStatus.COMPLETED);
        record.setResponseStatus(responseStatus);
        record.setResponseContentType(responseContentType);
        record.setResponseBody(responseBody);
        idempotencyRepositoryPort.save(record);
    }

    private IdempotencyDecision resolveExistingRecord(
            IdempotencyRecord existingRecord, String requestHash, Instant now) {
        if (!existingRecord.getExpiresAt().isAfter(now)) {
            idempotencyRepositoryPort.delete(existingRecord);
            idempotencyRepositoryPort.flush();
            return createRecord(existingRecord.getIdempotencyKey(), requestHash, now);
        }

        assertSameHash(existingRecord, requestHash);

        return switch (existingRecord.getStatus()) {
            case PROCESSING -> IdempotencyDecision.inProgress(existingRecord);
            case COMPLETED, FAILED -> IdempotencyDecision.replay(existingRecord);
        };
    }

    private IdempotencyDecision createRecord(String idempotencyKey, String requestHash, Instant now) {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setId(UUID.randomUUID());
        record.setIdempotencyKey(idempotencyKey);
        record.setRequestHash(requestHash);
        record.setStatus(IdempotencyStatus.PROCESSING);
        record.setCreatedAt(now);
        record.setExpiresAt(now.plus(TTL));

        try {
            return IdempotencyDecision.claimed(idempotencyRepositoryPort.save(record));
        } catch (DataIntegrityViolationException exception) {
            IdempotencyRecord existingRecord = idempotencyRepositoryPort
                    .findByIdempotencyKeyForUpdate(idempotencyKey)
                    .orElseThrow(() -> exception);
            return resolveExistingRecord(existingRecord, requestHash, now);
        }
    }

    private void assertSameHash(IdempotencyRecord existingRecord, String requestHash) {
        if (!existingRecord.getRequestHash().equals(requestHash)) {
            throw new IdempotencyKeyConflictException(
                    "Idempotency-Key already used with a different request payload");
        }
    }

    private String normalizeIdempotencyKey(String rawIdempotencyKey) {
        if (rawIdempotencyKey == null) {
            throw new MissingIdempotencyKeyException("Idempotency-Key header is required");
        }

        String idempotencyKey = rawIdempotencyKey.trim();
        if (idempotencyKey.isBlank()) {
            throw new MissingIdempotencyKeyException("Idempotency-Key header is required");
        }

        if (idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new MissingIdempotencyKeyException("Idempotency-Key header must have at most 255 characters");
        }

        return idempotencyKey;
    }
}
