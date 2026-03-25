package recargapay.wallet.adapter.in.api.response;

import java.math.BigDecimal;
import java.util.UUID;

public record WithdrawResponse(
        UUID walletId,
        UUID transactionId,
        BigDecimal withdrawAmount,
        BigDecimal updatedBalance
) {
}
