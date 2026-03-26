package recargapay.wallet.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import recargapay.wallet.domain.Category;
import recargapay.wallet.domain.EntryType;

@Entity
@Table(name = "transactions")
public class TransactionEntity {
    @Id
    private UUID id;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private UUID walletId;

    @Column(name = "entry_type", nullable = false, updatable = false)
    private EntryType entryType;

    @Column(name = "category", nullable = false, updatable = false)
    private Category category;

    @Column(name = "counterparty_wallet_id", updatable = false, nullable = true)
    private UUID counterpartyWalletId;

    @Column(name = "related_transaction_id", updatable = false, nullable = true)
    private UUID relatedTransactionId;

    @Column(name = "description", nullable = false, updatable = false, length = 255)
    private String description;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP")
    private Instant createdAt = Instant.now();

    @Column(name = "left_balance", nullable = false, updatable = true, precision = 19, scale = 4)
    private BigDecimal leftBalance;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public void setWalletId(UUID walletId) {
        this.walletId = walletId;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(EntryType entryType) {
        this.entryType = entryType;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public UUID getCounterpartyWalletId() {
        return counterpartyWalletId;
    }

    public void setCounterpartyWalletId(UUID counterpartyWalletId) {
        this.counterpartyWalletId = counterpartyWalletId;
    }

    public UUID getRelatedTransactionId() {
        return relatedTransactionId;
    }

    public void setRelatedTransactionId(UUID relatedTransactionId) {
        this.relatedTransactionId = relatedTransactionId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public BigDecimal getLeftBalance() {
        return leftBalance;
    }

    public void setLeftBalance(BigDecimal leftBalance) {
        this.leftBalance = leftBalance;
    }
}
