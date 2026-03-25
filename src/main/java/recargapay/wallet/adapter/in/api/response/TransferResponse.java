package recargapay.wallet.adapter.in.api.response;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferResponse(
        UUID transactionId,
        UUID walletId,
        BigDecimal transferredAmount,
        BigDecimal updatedBalance
) {
}
