package recargapay.wallet.adapter.in.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WalletResponse(UUID id, UUID userId, String alias, BigDecimal currentBalance, Instant createdAt) {
}
