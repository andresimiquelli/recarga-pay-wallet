package recargapay.wallet.application.port;

import java.util.Optional;
import recargapay.wallet.application.idempotency.IdempotencyRecord;

public interface IdempotencyRepositoryPort {
    Optional<IdempotencyRecord> findByIdempotencyKeyForUpdate(String idempotencyKey);

    IdempotencyRecord save(IdempotencyRecord record);

    void delete(IdempotencyRecord record);

    void flush();
}
