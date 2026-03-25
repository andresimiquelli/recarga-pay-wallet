package recargapay.wallet.application.mapper;

import org.mapstruct.Mapper;
import recargapay.wallet.adapter.in.api.response.WalletResponse;
import recargapay.wallet.domain.Wallet;

@Mapper(componentModel = "spring")
public interface WalletMapper {
    WalletResponse toResponse(Wallet wallet);
}
