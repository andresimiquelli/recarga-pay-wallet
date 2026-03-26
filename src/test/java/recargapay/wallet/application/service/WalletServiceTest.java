package recargapay.wallet.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import recargapay.wallet.application.exception.InsufficientWalletBalanceException;
import recargapay.wallet.application.exception.InvalidTransactionAmountException;
import recargapay.wallet.application.exception.InvalidTransferOperationException;
import recargapay.wallet.application.exception.WalletNotFoundException;
import recargapay.wallet.application.port.TransactionRepositoryPort;
import recargapay.wallet.application.port.WalletRepositoryPort;
import recargapay.wallet.domain.Category;
import recargapay.wallet.domain.EntryType;
import recargapay.wallet.domain.Transaction;
import recargapay.wallet.domain.Wallet;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {
    @Mock
    private WalletRepositoryPort walletRepositoryPort;

    @Mock
    private TransactionRepositoryPort transactionRepositoryPort;

    @InjectMocks
    private WalletService walletService;

    @Test
    void shouldReturnCurrentBalanceFromWallet() {
        UUID walletId = UUID.randomUUID();
        Wallet wallet = new Wallet();
        wallet.setId(walletId);
        wallet.setCurrentBalance(new BigDecimal("125.50"));

        when(walletRepositoryPort.findById(walletId)).thenReturn(Optional.of(wallet));

        BigDecimal currentBalance = walletService.getCurrentBalance(walletId);

        assertEquals(new BigDecimal("125.50"), currentBalance);
    }

    @Test
    void shouldReturnZeroWhenWalletBalanceIsNull() {
        UUID walletId = UUID.randomUUID();
        Wallet wallet = new Wallet();
        wallet.setId(walletId);

        when(walletRepositoryPort.findById(walletId)).thenReturn(Optional.of(wallet));

        BigDecimal currentBalance = walletService.getCurrentBalance(walletId);

        assertEquals(BigDecimal.ZERO, currentBalance);
    }

    @Test
    void shouldReturnHistoricalBalanceFromLatestTransactionBeforeTargetDate() {
        UUID walletId = UUID.randomUUID();
        Instant targetAt = Instant.parse("2026-03-25T12:00:00Z");

        Wallet wallet = new Wallet();
        wallet.setId(walletId);

        Transaction transaction = new Transaction();
        transaction.setWalletId(walletId);
        transaction.setLeftBalance(new BigDecimal("73.40"));
        transaction.setCreatedAt(Instant.parse("2026-03-25T11:59:59Z"));

        when(walletRepositoryPort.findById(walletId)).thenReturn(Optional.of(wallet));
        when(transactionRepositoryPort.findLatestByWalletIdAt(walletId, targetAt)).thenReturn(Optional.of(transaction));

        BigDecimal balanceAt = walletService.getBalanceAt(walletId, targetAt);

        assertEquals(new BigDecimal("73.40"), balanceAt);
    }

    @Test
    void shouldReturnZeroWhenNoTransactionExistsBeforeTargetDate() {
        UUID walletId = UUID.randomUUID();
        Instant targetAt = Instant.parse("2026-03-25T12:00:00Z");

        Wallet wallet = new Wallet();
        wallet.setId(walletId);

        when(walletRepositoryPort.findById(walletId)).thenReturn(Optional.of(wallet));
        when(transactionRepositoryPort.findLatestByWalletIdAt(walletId, targetAt)).thenReturn(Optional.empty());

        BigDecimal balanceAt = walletService.getBalanceAt(walletId, targetAt);

        assertEquals(BigDecimal.ZERO, balanceAt);
    }

    @Test
    void shouldFailWhenWalletDoesNotExist() {
        UUID walletId = UUID.randomUUID();
        when(walletRepositoryPort.findById(walletId)).thenReturn(Optional.empty());

        assertThrows(WalletNotFoundException.class, () -> walletService.getCurrentBalance(walletId));
    }

    @Test
    void shouldDepositAndUpdateWalletCurrentBalance() {
        UUID walletId = UUID.randomUUID();
        Wallet wallet = new Wallet();
        wallet.setId(walletId);
        wallet.setCurrentBalance(new BigDecimal("10.00"));

        Transaction savedTransaction = new Transaction();
        savedTransaction.setId(UUID.randomUUID());
        savedTransaction.setWalletId(walletId);
        savedTransaction.setAmount(new BigDecimal("5.00"));
        savedTransaction.setLeftBalance(new BigDecimal("15.00"));
        savedTransaction.setEntryType(EntryType.CREDIT);
        savedTransaction.setCategory(Category.DEPOSIT);
        savedTransaction.setDescription("Deposit into wallet");

        when(walletRepositoryPort.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
        when(walletRepositoryPort.save(wallet)).thenReturn(wallet);
        when(transactionRepositoryPort.save(org.mockito.ArgumentMatchers.any(Transaction.class)))
                .thenReturn(savedTransaction);

        Transaction transaction = walletService.deposit(walletId, new BigDecimal("5.00"));

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepositoryPort).save(transactionCaptor.capture());

        assertEquals(savedTransaction.getId(), transaction.getId());
        assertEquals(new BigDecimal("15.00"), transaction.getLeftBalance());
        assertEquals(new BigDecimal("15.00"), wallet.getCurrentBalance());
        assertNotNull(transactionCaptor.getValue().getId());
        assertEquals(new BigDecimal("15.00"), transactionCaptor.getValue().getLeftBalance());
        assertEquals(EntryType.CREDIT, transaction.getEntryType());
        assertEquals(Category.DEPOSIT, transaction.getCategory());
    }

    @Test
    void shouldWithdrawAndUpdateWalletCurrentBalance() {
        UUID walletId = UUID.randomUUID();
        Wallet wallet = new Wallet();
        wallet.setId(walletId);
        wallet.setCurrentBalance(new BigDecimal("10.00"));

        Transaction savedTransaction = new Transaction();
        savedTransaction.setId(UUID.randomUUID());
        savedTransaction.setWalletId(walletId);
        savedTransaction.setAmount(new BigDecimal("4.00"));
        savedTransaction.setLeftBalance(new BigDecimal("6.00"));
        savedTransaction.setEntryType(EntryType.DEBIT);
        savedTransaction.setCategory(Category.WITHDRAWAL);
        savedTransaction.setDescription("Withdraw from wallet");

        when(walletRepositoryPort.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
        when(walletRepositoryPort.save(wallet)).thenReturn(wallet);
        when(transactionRepositoryPort.save(org.mockito.ArgumentMatchers.any(Transaction.class)))
                .thenReturn(savedTransaction);

        Transaction transaction = walletService.withdraw(walletId, new BigDecimal("4.00"));

        ArgumentCaptor<Transaction> transactionCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepositoryPort).save(transactionCaptor.capture());

        assertEquals(savedTransaction.getId(), transaction.getId());
        assertEquals(new BigDecimal("6.00"), transaction.getLeftBalance());
        assertEquals(new BigDecimal("6.00"), wallet.getCurrentBalance());
        assertNotNull(transactionCaptor.getValue().getId());
        assertEquals(new BigDecimal("6.00"), transactionCaptor.getValue().getLeftBalance());
        assertEquals(EntryType.DEBIT, transaction.getEntryType());
        assertEquals(Category.WITHDRAWAL, transaction.getCategory());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldTransferBetweenWalletsLockingInAscendingOrder() {
        UUID originWalletId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID destinationWalletId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        Wallet originWallet = new Wallet();
        originWallet.setId(originWalletId);
        originWallet.setAlias("origin@example.com");
        originWallet.setCurrentBalance(new BigDecimal("10.00"));

        Wallet destinationWallet = new Wallet();
        destinationWallet.setId(destinationWalletId);
        destinationWallet.setAlias("dest@example.com");
        destinationWallet.setCurrentBalance(new BigDecimal("3.00"));

        when(walletRepositoryPort.findByIdForUpdate(destinationWalletId)).thenReturn(Optional.of(destinationWallet));
        when(walletRepositoryPort.findByIdForUpdate(originWalletId)).thenReturn(Optional.of(originWallet));
        when(walletRepositoryPort.save(originWallet)).thenReturn(originWallet);
        when(walletRepositoryPort.save(destinationWallet)).thenReturn(destinationWallet);
        when(transactionRepositoryPort.saveAll(org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Transaction originTransaction = walletService.transfer(
                originWalletId,
                destinationWalletId,
                new BigDecimal("4.00"));

        ArgumentCaptor<List<Transaction>> transactionCaptor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepositoryPort).saveAll(transactionCaptor.capture());
        var savedTransactions = transactionCaptor.getValue();
        Transaction destinationTransaction = savedTransactions.get(0);
        Transaction savedOriginTransaction = savedTransactions.get(1);

        InOrder inOrder = inOrder(walletRepositoryPort);
        inOrder.verify(walletRepositoryPort).findByIdForUpdate(destinationWalletId);
        inOrder.verify(walletRepositoryPort).findByIdForUpdate(originWalletId);

        assertEquals(originWalletId, originTransaction.getWalletId());
        assertEquals(new BigDecimal("4.00"), originTransaction.getAmount());
        assertEquals(new BigDecimal("6.00"), originTransaction.getLeftBalance());
        assertEquals(new BigDecimal("6.00"), originWallet.getCurrentBalance());
        assertEquals(new BigDecimal("7.00"), destinationWallet.getCurrentBalance());

        assertEquals(EntryType.DEBIT, savedOriginTransaction.getEntryType());
        assertEquals(Category.TRANSFER_OUT, savedOriginTransaction.getCategory());
        assertEquals(destinationWalletId, savedOriginTransaction.getCounterpartyWalletId());
        assertEquals("Transfer to dest@example.com", savedOriginTransaction.getDescription());

        assertEquals(EntryType.CREDIT, destinationTransaction.getEntryType());
        assertEquals(Category.TRANSFER_IN, destinationTransaction.getCategory());
        assertEquals(originWalletId, destinationTransaction.getCounterpartyWalletId());
        assertEquals("Transfer from origin@example.com", destinationTransaction.getDescription());

        assertNotNull(savedOriginTransaction.getId());
        assertNotNull(destinationTransaction.getId());
        assertEquals(destinationTransaction.getId(), savedOriginTransaction.getRelatedTransactionId());
        assertEquals(savedOriginTransaction.getId(), destinationTransaction.getRelatedTransactionId());
    }

    @Test
    void shouldRejectTransferWhenWalletBalanceIsInsufficient() {
        UUID originWalletId = UUID.randomUUID();
        UUID destinationWalletId = UUID.randomUUID();
        Wallet originWallet = new Wallet();
        originWallet.setId(originWalletId);
        originWallet.setCurrentBalance(new BigDecimal("3.00"));
        Wallet destinationWallet = new Wallet();
        destinationWallet.setId(destinationWalletId);
        destinationWallet.setCurrentBalance(new BigDecimal("1.00"));

        when(walletRepositoryPort.findByIdForUpdate(originWalletId)).thenReturn(Optional.of(originWallet));
        when(walletRepositoryPort.findByIdForUpdate(destinationWalletId)).thenReturn(Optional.of(destinationWallet));

        assertThrows(
                InsufficientWalletBalanceException.class,
                () -> walletService.transfer(originWalletId, destinationWalletId, new BigDecimal("4.00")));
    }

    @Test
    void shouldRejectTransferWhenOriginAndDestinationAreTheSame() {
        UUID walletId = UUID.randomUUID();

        assertThrows(
                InvalidTransferOperationException.class,
                () -> walletService.transfer(walletId, walletId, new BigDecimal("1.00")));
    }

    @Test
    void shouldRejectDepositWhenAmountIsZeroOrNegative() {
        UUID walletId = UUID.randomUUID();

        assertThrows(
                InvalidTransactionAmountException.class,
                () -> walletService.deposit(walletId, BigDecimal.ZERO));
        assertThrows(
                InvalidTransactionAmountException.class,
                () -> walletService.deposit(walletId, new BigDecimal("-1.00")));
    }

    @Test
    void shouldRejectWithdrawWhenAmountIsZeroOrNegative() {
        UUID walletId = UUID.randomUUID();

        assertThrows(
                InvalidTransactionAmountException.class,
                () -> walletService.withdraw(walletId, BigDecimal.ZERO));
        assertThrows(
                InvalidTransactionAmountException.class,
                () -> walletService.withdraw(walletId, new BigDecimal("-1.00")));
    }

    @Test
    void shouldRejectTransferWhenAmountIsZeroOrNegative() {
        UUID originWalletId = UUID.randomUUID();
        UUID destinationWalletId = UUID.randomUUID();

        assertThrows(
                InvalidTransactionAmountException.class,
                () -> walletService.transfer(originWalletId, destinationWalletId, BigDecimal.ZERO));
        assertThrows(
                InvalidTransactionAmountException.class,
                () -> walletService.transfer(originWalletId, destinationWalletId, new BigDecimal("-1.00")));
    }
}
