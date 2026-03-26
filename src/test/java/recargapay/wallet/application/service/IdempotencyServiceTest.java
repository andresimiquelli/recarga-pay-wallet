package recargapay.wallet.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import recargapay.wallet.application.exception.IdempotencyKeyConflictException;
import recargapay.wallet.application.idempotency.IdempotencyDecision;
import recargapay.wallet.application.idempotency.IdempotencyRecord;
import recargapay.wallet.application.idempotency.IdempotencyStatus;
import recargapay.wallet.application.port.IdempotencyRepositoryPort;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {
    @Mock
    private IdempotencyRepositoryPort idempotencyRepositoryPort;

    @InjectMocks
    private IdempotencyService idempotencyService;

    @Test
    void shouldClaimKeyWhenNoRecordExists() {
        when(idempotencyRepositoryPort.findByIdempotencyKeyForUpdate("key-1")).thenReturn(Optional.empty());
        when(idempotencyRepositoryPort.save(org.mockito.ArgumentMatchers.any(IdempotencyRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IdempotencyDecision decision = idempotencyService.begin("key-1", "hash-1");

        assertEquals(IdempotencyDecision.Type.CLAIMED, decision.type());

        ArgumentCaptor<IdempotencyRecord> captor = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(idempotencyRepositoryPort).save(captor.capture());
        assertEquals("key-1", captor.getValue().getIdempotencyKey());
        assertEquals("hash-1", captor.getValue().getRequestHash());
        assertEquals(IdempotencyStatus.PROCESSING, captor.getValue().getStatus());
    }

    @Test
    void shouldReplayStoredResponseWhenKeyAndHashMatch() {
        IdempotencyRecord existingRecord = new IdempotencyRecord();
        existingRecord.setId(UUID.randomUUID());
        existingRecord.setIdempotencyKey("key-2");
        existingRecord.setRequestHash("hash-2");
        existingRecord.setStatus(IdempotencyStatus.COMPLETED);
        existingRecord.setResponseStatus(200);
        existingRecord.setResponseBody("{\"ok\":true}");
        existingRecord.setExpiresAt(Instant.now().plusSeconds(60));

        when(idempotencyRepositoryPort.findByIdempotencyKeyForUpdate("key-2"))
                .thenReturn(Optional.of(existingRecord));

        IdempotencyDecision decision = idempotencyService.begin("key-2", "hash-2");

        assertEquals(IdempotencyDecision.Type.REPLAY, decision.type());
        assertSame(existingRecord, decision.record());
    }

    @Test
    void shouldRejectReusedKeyWithDifferentHash() {
        IdempotencyRecord existingRecord = new IdempotencyRecord();
        existingRecord.setId(UUID.randomUUID());
        existingRecord.setIdempotencyKey("key-3");
        existingRecord.setRequestHash("hash-3");
        existingRecord.setStatus(IdempotencyStatus.COMPLETED);
        existingRecord.setExpiresAt(Instant.now().plusSeconds(60));

        when(idempotencyRepositoryPort.findByIdempotencyKeyForUpdate("key-3"))
                .thenReturn(Optional.of(existingRecord));

        assertThrows(IdempotencyKeyConflictException.class, () -> idempotencyService.begin("key-3", "other-hash"));
    }

    @Test
    void shouldReplaceExpiredRecordWithNewClaim() {
        IdempotencyRecord expiredRecord = new IdempotencyRecord();
        expiredRecord.setId(UUID.randomUUID());
        expiredRecord.setIdempotencyKey("key-4");
        expiredRecord.setRequestHash("hash-4");
        expiredRecord.setStatus(IdempotencyStatus.COMPLETED);
        expiredRecord.setExpiresAt(Instant.now().minusSeconds(1));

        when(idempotencyRepositoryPort.findByIdempotencyKeyForUpdate("key-4"))
                .thenReturn(Optional.of(expiredRecord));
        when(idempotencyRepositoryPort.save(org.mockito.ArgumentMatchers.any(IdempotencyRecord.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IdempotencyDecision decision = idempotencyService.begin("key-4", "hash-4");

        assertEquals(IdempotencyDecision.Type.CLAIMED, decision.type());
        verify(idempotencyRepositoryPort).delete(expiredRecord);
        verify(idempotencyRepositoryPort).flush();
    }
}
