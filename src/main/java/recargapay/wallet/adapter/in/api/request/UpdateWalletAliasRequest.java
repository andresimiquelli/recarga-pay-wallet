package recargapay.wallet.adapter.in.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateWalletAliasRequest(
        @NotBlank(message = "alias is required")
        @Size(max = 254, message = "alias must have at most 254 characters")
        @Email(message = "alias must be a valid email address")
        @Pattern(
                regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
                message = "alias contains invalid characters")
        String alias
) {
}
