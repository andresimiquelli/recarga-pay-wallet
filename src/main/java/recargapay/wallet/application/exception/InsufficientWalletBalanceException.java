package recargapay.wallet.application.exception;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientWalletBalanceException extends RuntimeException {
    public InsufficientWalletBalanceException(UUID walletId, BigDecimal currentBalance, BigDecimal requestedAmount) {
        super("insufficient balance for wallet " + walletId + ": current balance=" + currentBalance
                + ", requested amount=" + requestedAmount);
    }
}
