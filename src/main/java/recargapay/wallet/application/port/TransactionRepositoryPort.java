package recargapay.wallet.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import recargapay.wallet.domain.Transaction;

public interface TransactionRepositoryPort {
    Transaction save(Transaction transaction);

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    Optional<Transaction> findLatestByWalletIdAt(UUID walletId, Instant targetAt);
}
