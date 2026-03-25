package recargapay.wallet.adapter.in.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletBalanceResponse(UUID walletId, BigDecimal balanceAmount, Instant balanceAt) {
}
