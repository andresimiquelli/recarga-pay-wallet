package recargapay.wallet.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import recargapay.wallet.application.port.TransactionRepositoryPort;
import recargapay.wallet.domain.Transaction;

@Component
public class TransactionPersistenceAdapter implements TransactionRepositoryPort {
    private final JpaTransactionRepository jpaTransactionRepository;

    public TransactionPersistenceAdapter(JpaTransactionRepository jpaTransactionRepository) {
        this.jpaTransactionRepository = jpaTransactionRepository;
    }

    @Override
    public Transaction save(Transaction transaction) {
        TransactionEntity transactionEntity = toEntity(transaction);
        TransactionEntity savedTransactionEntity = jpaTransactionRepository.save(transactionEntity);
        return toDomain(savedTransactionEntity);
    }

    @Override
    public Optional<Transaction> findByIdempotencyKey(String idempotencyKey) {
        return jpaTransactionRepository.findByIdempotencyKey(idempotencyKey).map(this::toDomain);
    }

    @Override
    public Optional<Transaction> findLatestByWalletIdAt(UUID walletId, Instant targetAt) {
        return jpaTransactionRepository
                .findTopByWalletIdAndCreatedAtLessThanEqualOrderByCreatedAtDescIdDesc(walletId, targetAt)
                .map(this::toDomain);
    }

    private TransactionEntity toEntity(Transaction transaction) {
        TransactionEntity transactionEntity = new TransactionEntity();
        transactionEntity.setId(transaction.getId());
        transactionEntity.setWalletId(transaction.getWalletId());
        transactionEntity.setEntryType(transaction.getEntryType());
        transactionEntity.setCategory(transaction.getCategory());
        transactionEntity.setCounterpartyWalletId(transaction.getCounterpartyWalletId());
        transactionEntity.setRelatedTransactionId(transaction.getRelatedTransactionId());
        transactionEntity.setDescription(transaction.getDescription());
        transactionEntity.setAmount(transaction.getAmount());
        transactionEntity.setIdempotencyKey(transaction.getIdempotencyKey());
        transactionEntity.setLeftBalance(transaction.getLeftBalance());
        return transactionEntity;
    }

    private Transaction toDomain(TransactionEntity transactionEntity) {
        Transaction transaction = new Transaction();
        transaction.setId(transactionEntity.getId());
        transaction.setWalletId(transactionEntity.getWalletId());
        transaction.setEntryType(transactionEntity.getEntryType());
        transaction.setCategory(transactionEntity.getCategory());
        transaction.setCounterpartyWalletId(transactionEntity.getCounterpartyWalletId());
        transaction.setRelatedTransactionId(transactionEntity.getRelatedTransactionId());
        transaction.setDescription(transactionEntity.getDescription());
        transaction.setAmount(transactionEntity.getAmount());
        transaction.setIdempotencyKey(transactionEntity.getIdempotencyKey());
        transaction.setCreatedAt(transactionEntity.getCreatedAt());
        transaction.setLeftBalance(transactionEntity.getLeftBalance());
        return transaction;
    }
}
