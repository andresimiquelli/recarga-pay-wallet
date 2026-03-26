package recargapay.wallet.adapter.in.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import recargapay.wallet.PostgresIntegrationTest;
import recargapay.wallet.adapter.out.persistence.JpaIdempotencyRepository;
import recargapay.wallet.adapter.out.persistence.JpaTransactionRepository;
import recargapay.wallet.adapter.out.persistence.JpaWalletRepository;
import recargapay.wallet.application.service.IdempotencyService;
import recargapay.wallet.application.service.WalletService;
import recargapay.wallet.domain.Wallet;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdempotencyFilterIntegrationTest extends PostgresIntegrationTest {
    @LocalServerPort
    private int port;

    @Autowired
    private WalletService walletService;

    @Autowired
    private JpaWalletRepository jpaWalletRepository;

    @Autowired
    private JpaTransactionRepository jpaTransactionRepository;

    @Autowired
    private JpaIdempotencyRepository jpaIdempotencyRepository;

    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        httpClient = HttpClient.newHttpClient();
        jpaTransactionRepository.deleteAll();
        jpaIdempotencyRepository.deleteAll();
        jpaWalletRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        jpaTransactionRepository.deleteAll();
        jpaIdempotencyRepository.deleteAll();
        jpaWalletRepository.deleteAll();
    }

    @Test
    void shouldReplayStoredResponseWhenSameKeyAndEquivalentJsonBodyAreSent() throws Exception {
        Wallet wallet = walletService.create(UUID.randomUUID(), "idempotent.deposit@example.com");

        HttpResponse<String> firstResponse = postDeposit(wallet.getId(), "dep-header-1", "{\"amount\":10.00}");
        HttpResponse<String> secondResponse = postDeposit(wallet.getId(), "dep-header-1", "{  \"amount\":10.00 }");

        assertEquals(200, firstResponse.statusCode());
        assertEquals(200, secondResponse.statusCode());
        assertEquals(firstResponse.body(), secondResponse.body());
        assertEquals(1L, jpaTransactionRepository.count());
        assertEquals(1L, jpaIdempotencyRepository.count());
        assertEquals(new BigDecimal("10.00"), walletService.getById(wallet.getId()).getCurrentBalance());
    }

    @Test
    void shouldRejectSameKeyWhenPayloadChanges() throws Exception {
        Wallet wallet = walletService.create(UUID.randomUUID(), "idempotent.conflict@example.com");

        HttpResponse<String> firstResponse = postDeposit(wallet.getId(), "dep-header-2", "{\"amount\":10.00}");
        HttpResponse<String> secondResponse = postDeposit(wallet.getId(), "dep-header-2", "{\"amount\":11.00}");

        assertEquals(200, firstResponse.statusCode());
        assertEquals(409, secondResponse.statusCode());
        assertTrue(secondResponse.body().contains("different request payload"));
        assertEquals(1L, jpaTransactionRepository.count());
    }

    @Test
    void shouldRequireIdempotencyKeyHeaderOnPostRequests() throws Exception {
        Wallet wallet = walletService.create(UUID.randomUUID(), "idempotent.required@example.com");

        HttpResponse<String> response = postDeposit(wallet.getId(), null, "{\"amount\":10.00}");

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("Idempotency-Key header is required"));
    }

    @Test
    void shouldExpireExistingKeyAfterTwentyFourHours() throws Exception {
        Wallet wallet = walletService.create(UUID.randomUUID(), "idempotent.ttl@example.com");

        HttpResponse<String> firstResponse = postDeposit(wallet.getId(), "dep-header-3", "{\"amount\":10.00}");
        assertEquals(200, firstResponse.statusCode());

        var record = jpaIdempotencyRepository.findByIdempotencyKey("dep-header-3").orElseThrow();
        record.setExpiresAt(Instant.now().minusSeconds(1));
        jpaIdempotencyRepository.save(record);

        HttpResponse<String> secondResponse = postDeposit(wallet.getId(), "dep-header-3", "{\"amount\":10.00}");

        assertEquals(200, secondResponse.statusCode());
        assertEquals(2L, jpaTransactionRepository.count());
        assertEquals(new BigDecimal("20.00"), walletService.getById(wallet.getId()).getCurrentBalance());
        assertEquals(1L, jpaIdempotencyRepository.count());
    }

    private HttpResponse<String> postDeposit(UUID walletId, String idempotencyKey, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/wallets/" + walletId + "/deposits"))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(body));

        if (idempotencyKey != null) {
            builder.header(IdempotencyService.IDEMPOTENCY_HEADER, idempotencyKey);
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
