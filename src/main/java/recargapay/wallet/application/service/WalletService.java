package recargapay.wallet.application.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import recargapay.wallet.application.exception.InsufficientWalletBalanceException;
import recargapay.wallet.application.exception.InvalidTransactionAmountException;
import recargapay.wallet.application.exception.InvalidTransferOperationException;
import recargapay.wallet.application.exception.InvalidWalletAliasException;
import recargapay.wallet.application.exception.WalletAliasAlreadyInUseException;
import recargapay.wallet.application.exception.WalletAlreadyExistsForUserException;
import recargapay.wallet.application.exception.WalletNotFoundException;
import recargapay.wallet.application.port.TransactionRepositoryPort;
import recargapay.wallet.application.port.WalletRepositoryPort;
import recargapay.wallet.domain.Category;
import recargapay.wallet.domain.EntryType;
import recargapay.wallet.domain.Transaction;
import recargapay.wallet.domain.Wallet;

@Service
public class WalletService {
    private static final Pattern EMAIL_ALIAS_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final String DEPOSIT_DESCRIPTION = "Deposit into wallet";
    private static final String WITHDRAW_DESCRIPTION = "Withdraw from wallet";
    private static final String TRANSFER_OUT_DESCRIPTION_TEMPLATE = "Transfer to %s";
    private static final String TRANSFER_IN_DESCRIPTION_TEMPLATE = "Transfer from %s";

    private final TransactionRepositoryPort transactionRepositoryPort;
    private final WalletRepositoryPort walletRepositoryPort;

    public WalletService(
            WalletRepositoryPort walletRepositoryPort,
            TransactionRepositoryPort transactionRepositoryPort) {
        this.walletRepositoryPort = walletRepositoryPort;
        this.transactionRepositoryPort = transactionRepositoryPort;
    }

    @Transactional
    public Wallet create(UUID userId, String alias) {
        String normalizedAlias = normalizeAlias(alias);

        if (walletRepositoryPort.existsByUserId(userId)) {
            throw new WalletAlreadyExistsForUserException(userId);
        }

        if (walletRepositoryPort.existsByAlias(normalizedAlias)) {
            throw new WalletAliasAlreadyInUseException(normalizedAlias);
        }

        Wallet wallet = new Wallet();
        wallet.setUserId(userId);
        wallet.setAlias(normalizedAlias);
        wallet.setCurrentBalance(BigDecimal.ZERO);

        return walletRepositoryPort.save(wallet);
    }

    @Transactional(readOnly = true)
    public Wallet getById(UUID id) {
        return walletRepositoryPort
                .findById(id)
                .orElseThrow(() -> new WalletNotFoundException("wallet not found for id: " + id));
    }

    @Transactional(readOnly = true)
    public Wallet getByUserId(UUID userId) {
        return walletRepositoryPort
                .findByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException("wallet not found for userId: " + userId));
    }

    @Transactional(readOnly = true)
    public Wallet getByAlias(String alias) {
        String normalizedAlias = normalizeAlias(alias);
        return walletRepositoryPort
                .findByAlias(normalizedAlias)
                .orElseThrow(() -> new WalletNotFoundException("wallet not found for alias: " + normalizedAlias));
    }

    @Transactional
    public Wallet updateAlias(UUID id, String alias) {
        Wallet wallet = getById(id);
        String normalizedAlias = normalizeAlias(alias);

        if (!wallet.getAlias().equalsIgnoreCase(normalizedAlias)
                && walletRepositoryPort.existsByAlias(normalizedAlias)) {
            throw new WalletAliasAlreadyInUseException(normalizedAlias);
        }

        wallet.setAlias(normalizedAlias);
        return walletRepositoryPort.save(wallet);
    }

    @Transactional(readOnly = true)
    public BigDecimal getCurrentBalance(UUID walletId) {
        BigDecimal currentBalance = getById(walletId).getCurrentBalance();
        return currentBalance == null ? BigDecimal.ZERO : currentBalance;
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalanceAt(UUID walletId, Instant targetAt) {
        getById(walletId);
        return transactionRepositoryPort.findLatestByWalletIdAt(walletId, targetAt)
                .map(Transaction::getLeftBalance)
                .orElse(BigDecimal.ZERO);
    }

    @Transactional
    public Transaction deposit(UUID walletId, BigDecimal amount) {
        return processBalanceChange(walletId, amount, EntryType.CREDIT, Category.DEPOSIT, DEPOSIT_DESCRIPTION);
    }

    @Transactional
    public Transaction withdraw(UUID walletId, BigDecimal amount) {
        return processBalanceChange(walletId, amount, EntryType.DEBIT, Category.WITHDRAWAL, WITHDRAW_DESCRIPTION);
    }

    @Transactional
    public Transaction transfer(UUID originWalletId, UUID destinationWalletId, BigDecimal amount) {
        validateAmount(amount);
        validateTransferWallets(originWalletId, destinationWalletId);

        UUID firstWalletId = originWalletId.compareTo(destinationWalletId) <= 0 ? originWalletId : destinationWalletId;
        UUID secondWalletId = originWalletId.compareTo(destinationWalletId) <= 0 ? destinationWalletId : originWalletId;

        Wallet firstLockedWallet = lockWallet(firstWalletId);
        Wallet secondLockedWallet = lockWallet(secondWalletId);

        Wallet originWallet = originWalletId.equals(firstWalletId) ? firstLockedWallet : secondLockedWallet;
        Wallet destinationWallet = destinationWalletId.equals(secondWalletId) ? secondLockedWallet : firstLockedWallet;
        if (destinationWalletId.equals(firstWalletId)) {
            destinationWallet = firstLockedWallet;
        }
        if (originWalletId.equals(secondWalletId)) {
            originWallet = secondLockedWallet;
        }

        BigDecimal originCurrentBalance = getSafeBalance(originWallet);
        BigDecimal destinationCurrentBalance = getSafeBalance(destinationWallet);
        if (originCurrentBalance.compareTo(amount) < 0) {
            throw new InsufficientWalletBalanceException(originWalletId, originCurrentBalance, amount);
        }

        BigDecimal updatedOriginBalance = originCurrentBalance.subtract(amount);
        BigDecimal updatedDestinationBalance = destinationCurrentBalance.add(amount);

        originWallet.setCurrentBalance(updatedOriginBalance);
        destinationWallet.setCurrentBalance(updatedDestinationBalance);
        walletRepositoryPort.save(originWallet);
        walletRepositoryPort.save(destinationWallet);

        UUID originTransactionId = UUID.randomUUID();
        UUID destinationTransactionId = UUID.randomUUID();

        Transaction originTransaction = new Transaction();
        originTransaction.setId(originTransactionId);
        originTransaction.setWalletId(originWalletId);
        originTransaction.setEntryType(EntryType.DEBIT);
        originTransaction.setCategory(Category.TRANSFER_OUT);
        originTransaction.setCounterpartyWalletId(destinationWalletId);
        originTransaction.setRelatedTransactionId(destinationTransactionId);
        originTransaction.setDescription(String.format(TRANSFER_OUT_DESCRIPTION_TEMPLATE, destinationWallet.getAlias()));
        originTransaction.setAmount(amount);
        originTransaction.setLeftBalance(updatedOriginBalance);

        Transaction destinationTransaction = new Transaction();
        destinationTransaction.setId(destinationTransactionId);
        destinationTransaction.setWalletId(destinationWalletId);
        destinationTransaction.setEntryType(EntryType.CREDIT);
        destinationTransaction.setCategory(Category.TRANSFER_IN);
        destinationTransaction.setCounterpartyWalletId(originWalletId);
        destinationTransaction.setRelatedTransactionId(originTransactionId);
        destinationTransaction.setDescription(String.format(TRANSFER_IN_DESCRIPTION_TEMPLATE, originWallet.getAlias()));
        destinationTransaction.setAmount(amount);
        destinationTransaction.setLeftBalance(updatedDestinationBalance);

        List<Transaction> savedTransactions =
                transactionRepositoryPort.saveAll(List.of(destinationTransaction, originTransaction));
        return savedTransactions.get(1);
    }

    private Transaction processBalanceChange(
            UUID walletId,
            BigDecimal amount,
            EntryType entryType,
            Category category,
            String description) {
        validateAmount(amount);

        Wallet wallet = lockWallet(walletId);

        BigDecimal currentBalance = getSafeBalance(wallet);
        BigDecimal updatedBalance = calculateUpdatedBalance(walletId, amount, entryType, currentBalance);

        wallet.setCurrentBalance(updatedBalance);
        walletRepositoryPort.save(wallet);

        Transaction transaction = new Transaction();
        transaction.setId(UUID.randomUUID());
        transaction.setWalletId(walletId);
        transaction.setEntryType(entryType);
        transaction.setCategory(category);
        transaction.setDescription(description);
        transaction.setAmount(amount);
        transaction.setLeftBalance(updatedBalance);

        return transactionRepositoryPort.save(transaction);
    }

    private Wallet lockWallet(UUID walletId) {
        return walletRepositoryPort
                .findByIdForUpdate(walletId)
                .orElseThrow(() -> new WalletNotFoundException("wallet not found for id: " + walletId));
    }

    private BigDecimal getSafeBalance(Wallet wallet) {
        return wallet.getCurrentBalance() == null ? BigDecimal.ZERO : wallet.getCurrentBalance();
    }

    private BigDecimal calculateUpdatedBalance(
            UUID walletId, BigDecimal amount, EntryType entryType, BigDecimal currentBalance) {
        if (entryType == EntryType.CREDIT) {
            return currentBalance.add(amount);
        }

        if (currentBalance.compareTo(amount) < 0) {
            throw new InsufficientWalletBalanceException(walletId, currentBalance, amount);
        }

        return currentBalance.subtract(amount);
    }

    private void validateTransferWallets(UUID originWalletId, UUID destinationWalletId) {
        if (originWalletId == null || destinationWalletId == null) {
            throw new InvalidTransferOperationException("origin and destination wallets are required");
        }

        if (originWalletId.equals(destinationWalletId)) {
            throw new InvalidTransferOperationException("origin and destination wallets must be different");
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidTransactionAmountException("amount must be greater than zero");
        }
    }

    private String normalizeAlias(String alias) {
        if (alias == null) {
            throw new InvalidWalletAliasException("alias is required");
        }

        String normalizedAlias = alias.trim().toLowerCase(Locale.ROOT);
        if (normalizedAlias.isBlank()) {
            throw new InvalidWalletAliasException("alias is required");
        }

        if (normalizedAlias.contains(" ")) {
            throw new InvalidWalletAliasException("alias must not contain spaces");
        }

        if (!EMAIL_ALIAS_PATTERN.matcher(normalizedAlias).matches()) {
            throw new InvalidWalletAliasException("alias must be a valid email address");
        }

        return normalizedAlias;
    }
}
