package recargapay.wallet.adapter.in.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record WithdrawRequest(
        @NotNull(message = "amount is required")
        @Positive(message = "amount must be greater than zero")
        BigDecimal amount,
        @NotBlank(message = "idempotencyKey is required")
        @Size(max = 255, message = "idempotencyKey must have at most 255 characters")
        String idempotencyKey
) {
}
