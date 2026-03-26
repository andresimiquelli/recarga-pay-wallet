package recargapay.wallet.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import recargapay.wallet.PostgresIntegrationTest;
import recargapay.wallet.adapter.out.persistence.JpaTransactionRepository;
import recargapay.wallet.adapter.out.persistence.JpaWalletRepository;
import recargapay.wallet.adapter.out.persistence.TransactionEntity;
import recargapay.wallet.application.exception.InsufficientWalletBalanceException;
import recargapay.wallet.domain.Category;
import recargapay.wallet.domain.Transaction;
import recargapay.wallet.domain.Wallet;

@SpringBootTest
class WalletServiceConcurrencyTest extends PostgresIntegrationTest {
    @Autowired
    private WalletService walletService;

    @Autowired
    private JpaWalletRepository jpaWalletRepository;

    @Autowired
    private JpaTransactionRepository jpaTransactionRepository;

    @BeforeEach
    void setUp() {
        jpaTransactionRepository.deleteAll();
        jpaWalletRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        jpaTransactionRepository.deleteAll();
        jpaWalletRepository.deleteAll();
    }

    @Test
    void shouldHandleConcurrentDepositsOnSameWallet() throws Exception {
        Wallet wallet = walletService.create(UUID.randomUUID(), "deposit.concurrent@example.com");

        int operationCount = 10;
        BigDecimal amount = new BigDecimal("10.00");

        List<Transaction> transactions = executeConcurrently(operationCount, index ->
                walletService.deposit(wallet.getId(), amount));

        Wallet updatedWallet = walletService.getById(wallet.getId());

        assertEquals(operationCount, transactions.size());
        assertEquals(new BigDecimal("100.00"), updatedWallet.getCurrentBalance());
        assertEquals(operationCount, jpaTransactionRepository.count());
        assertEquals(
                operationCount,
                transactions.stream().map(Transaction::getId).distinct().count());
    }

    @Test
    void shouldPreventBalanceCorruptionDuringConcurrentWithdrawals() throws Exception {
        Wallet wallet = walletService.create(UUID.randomUUID(), "withdraw.concurrent@example.com");
        walletService.deposit(wallet.getId(), new BigDecimal("100.00"));

        List<Object> results = executeConcurrently(2, index -> {
            try {
                return walletService.withdraw(wallet.getId(), new BigDecimal("80.00"));
            } catch (InsufficientWalletBalanceException exception) {
                return exception;
            }
        });

        long successCount = results.stream().filter(Transaction.class::isInstance).count();
        long failureCount = results.stream().filter(InsufficientWalletBalanceException.class::isInstance).count();
        Wallet updatedWallet = walletService.getById(wallet.getId());

        assertEquals(1L, successCount);
        assertEquals(1L, failureCount);
        assertEquals(new BigDecimal("20.00"), updatedWallet.getCurrentBalance());
        assertEquals(2L, jpaTransactionRepository.count());
    }

    @Test
    void shouldPersistRelatedTransactionsAtomicallyDuringConcurrentTransfers() throws Exception {
        Wallet originWallet = walletService.create(UUID.randomUUID(), "origin.concurrent@example.com");
        Wallet destinationWallet = walletService.create(UUID.randomUUID(), "destination.concurrent@example.com");
        walletService.deposit(originWallet.getId(), new BigDecimal("100.00"));

        int transferCount = 5;
        BigDecimal amount = new BigDecimal("10.00");

        executeConcurrently(transferCount, index ->
                walletService.transfer(
                        originWallet.getId(),
                        destinationWallet.getId(),
                        amount));

        Wallet updatedOriginWallet = walletService.getById(originWallet.getId());
        Wallet updatedDestinationWallet = walletService.getById(destinationWallet.getId());
        List<TransactionEntity> transferTransactions = jpaTransactionRepository.findAll().stream()
                .filter(transaction -> transaction.getCategory() == Category.TRANSFER_IN
                        || transaction.getCategory() == Category.TRANSFER_OUT)
                .toList();

        assertEquals(new BigDecimal("50.00"), updatedOriginWallet.getCurrentBalance());
        assertEquals(new BigDecimal("50.00"), updatedDestinationWallet.getCurrentBalance());
        assertEquals(transferCount * 2L, transferTransactions.size());

        for (TransactionEntity transaction : transferTransactions) {
            assertNotNull(transaction.getId());
            assertNotNull(transaction.getRelatedTransactionId());

            TransactionEntity relatedTransaction = transferTransactions.stream()
                    .filter(candidate -> candidate.getId().equals(transaction.getRelatedTransactionId()))
                    .findFirst()
                    .orElse(null);

            assertNotNull(relatedTransaction);
            assertEquals(transaction.getId(), relatedTransaction.getRelatedTransactionId());
            assertNotSame(transaction.getCategory(), relatedTransaction.getCategory());
        }
    }

    @Test
    void shouldAvoidDeadlockWhenWalletsTransferToEachOtherConcurrently() throws Exception {
        Wallet walletA = walletService.create(UUID.randomUUID(), "wallet.a.concurrent@example.com");
        Wallet walletB = walletService.create(UUID.randomUUID(), "wallet.b.concurrent@example.com");
        walletService.deposit(walletA.getId(), new BigDecimal("100.00"));
        walletService.deposit(walletB.getId(), new BigDecimal("100.00"));

        List<Transaction> transactions = executeConcurrently(2, index -> {
            if (index == 0) {
                return walletService.transfer(walletA.getId(), walletB.getId(), new BigDecimal("30.00"));
            }
            return walletService.transfer(walletB.getId(), walletA.getId(), new BigDecimal("10.00"));
        });

        Wallet updatedWalletA = walletService.getById(walletA.getId());
        Wallet updatedWalletB = walletService.getById(walletB.getId());
        List<TransactionEntity> transferTransactions = jpaTransactionRepository.findAll().stream()
                .filter(transaction -> transaction.getCategory() == Category.TRANSFER_IN
                        || transaction.getCategory() == Category.TRANSFER_OUT)
                .toList();

        assertEquals(2, transactions.size());
        assertEquals(new BigDecimal("80.00"), updatedWalletA.getCurrentBalance());
        assertEquals(new BigDecimal("120.00"), updatedWalletB.getCurrentBalance());
        assertEquals(4L, transferTransactions.size());

        for (Transaction transaction : transactions) {
            assertNotNull(transaction.getId());
            assertNotNull(transaction.getRelatedTransactionId());
        }

        for (TransactionEntity transaction : transferTransactions) {
            TransactionEntity relatedTransaction = transferTransactions.stream()
                    .filter(candidate -> candidate.getId().equals(transaction.getRelatedTransactionId()))
                    .findFirst()
                    .orElse(null);

            assertNotNull(relatedTransaction);
            assertEquals(transaction.getId(), relatedTransaction.getRelatedTransactionId());
            assertTrue(transaction.getWalletId().equals(walletA.getId()) || transaction.getWalletId().equals(walletB.getId()));
        }
    }

    private <T> List<T> executeConcurrently(int taskCount, IndexedTask<T> task) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(taskCount);
        CountDownLatch readyLatch = new CountDownLatch(taskCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int index = 0; index < taskCount; index++) {
                final int taskIndex = index;
                futures.add(executorService.submit(() -> {
                    readyLatch.countDown();
                    assertTrue(startLatch.await(10, TimeUnit.SECONDS));
                    return task.execute(taskIndex);
                }));
            }

            assertTrue(readyLatch.await(10, TimeUnit.SECONDS));
            startLatch.countDown();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                try {
                    results.add(future.get(30, TimeUnit.SECONDS));
                } catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    if (cause instanceof Exception castedCause) {
                        throw castedCause;
                    }
                    throw exception;
                }
            }
            return results;
        } finally {
            executorService.shutdownNow();
            executorService.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @FunctionalInterface
    private interface IndexedTask<T> {
        T execute(int index) throws Exception;
    }
}
