package recargapay.wallet.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class Transaction {
    private UUID id;
    private UUID walletId;
    private EntryType entryType;
    private Category category;
    private UUID counterpartyWalletId;
    private UUID relatedTransactionId;
    private String description;
    private BigDecimal amount;
    private String idempotencyKey;
    private Instant createdAt;
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

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public BigDecimal getLeftBalance() {
        return leftBalance;
    }

    public void setLeftBalance(BigDecimal leftBalance) {
        this.leftBalance = leftBalance;
    }
}
