package recargapay.wallet.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaTransactionRepository extends JpaRepository<TransactionEntity, UUID> {
    Optional<TransactionEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<TransactionEntity> findTopByWalletIdOrderByCreatedAtDescIdDesc(UUID walletId);
}
