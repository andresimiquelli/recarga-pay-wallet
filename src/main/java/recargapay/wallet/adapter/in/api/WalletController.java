package recargapay.wallet.adapter.in.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import recargapay.wallet.adapter.in.api.request.CreateWalletRequest;
import recargapay.wallet.adapter.in.api.request.DepositRequest;
import recargapay.wallet.adapter.in.api.request.TransferRequest;
import recargapay.wallet.adapter.in.api.request.UpdateWalletAliasRequest;
import recargapay.wallet.adapter.in.api.request.WithdrawRequest;
import recargapay.wallet.adapter.in.api.response.DepositResponse;
import recargapay.wallet.adapter.in.api.response.TransferResponse;
import recargapay.wallet.adapter.in.api.response.WalletBalanceResponse;
import recargapay.wallet.adapter.in.api.response.WalletResponse;
import recargapay.wallet.adapter.in.api.response.WithdrawResponse;
import recargapay.wallet.application.mapper.WalletMapper;
import recargapay.wallet.application.service.WalletService;
import recargapay.wallet.domain.Transaction;

@Validated
@RestController
@RequestMapping("/api/wallets")
public class WalletController {
    private final WalletService walletService;
    private final WalletMapper walletMapper;

    public WalletController(WalletService walletService, WalletMapper walletMapper) {
        this.walletService = walletService;
        this.walletMapper = walletMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WalletResponse create(@Valid @RequestBody CreateWalletRequest request) {
        return walletMapper.toResponse(walletService.create(request.userId(), request.alias()));
    }

    @GetMapping("/{id}")
    public WalletResponse getById(@PathVariable UUID id) {
        return walletMapper.toResponse(walletService.getById(id));
    }

    @GetMapping("/{id}/balance")
    public WalletBalanceResponse getCurrentBalance(@PathVariable UUID id) {
        return new WalletBalanceResponse(id, walletService.getCurrentBalance(id));
    }

    @PostMapping("/{id}/deposits")
    public DepositResponse deposit(@PathVariable UUID id, @Valid @RequestBody DepositRequest request) {
        Transaction transaction = walletService.deposit(id, request.amount(), request.idempotencyKey());
        return new DepositResponse(
                transaction.getWalletId(),
                transaction.getId(),
                transaction.getAmount(),
                transaction.getLeftBalance());
    }

    @PostMapping("/{id}/withdrawals")
    public WithdrawResponse withdraw(@PathVariable UUID id, @Valid @RequestBody WithdrawRequest request) {
        Transaction transaction = walletService.withdraw(id, request.amount(), request.idempotencyKey());
        return new WithdrawResponse(
                transaction.getWalletId(),
                transaction.getId(),
                transaction.getAmount(),
                transaction.getLeftBalance());
    }

    @PostMapping("/{id}/transfers")
    public TransferResponse transfer(@PathVariable UUID id, @Valid @RequestBody TransferRequest request) {
        Transaction transaction = walletService.transfer(
                id,
                request.destinationWalletId(),
                request.amount(),
                request.idempotencyKey());
        return new TransferResponse(
                transaction.getId(),
                transaction.getWalletId(),
                transaction.getAmount(),
                transaction.getLeftBalance());
    }

    @GetMapping("/user/{userId}")
    public WalletResponse getByUserId(@PathVariable UUID userId) {
        return walletMapper.toResponse(walletService.getByUserId(userId));
    }

    @GetMapping("/alias/{alias}")
    public WalletResponse getByAlias(
            @PathVariable
            @Email(message = "alias must be a valid email address")
            @Pattern(
                    regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$",
                    message = "alias contains invalid characters")
            String alias) {
        return walletMapper.toResponse(walletService.getByAlias(alias));
    }

    @PutMapping("/{id}/alias")
    public WalletResponse updateAlias(
            @PathVariable UUID id, @Valid @RequestBody UpdateWalletAliasRequest request) {
        return walletMapper.toResponse(walletService.updateAlias(id, request.alias()));
    }
}
