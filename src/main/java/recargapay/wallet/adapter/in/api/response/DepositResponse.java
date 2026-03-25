package recargapay.wallet.adapter.in.api.response;

import java.math.BigDecimal;
import java.util.UUID;

public record DepositResponse(
        UUID walletId,
        UUID transactionId,
        BigDecimal depositedAmount,
        BigDecimal updatedBalance
) {
}
