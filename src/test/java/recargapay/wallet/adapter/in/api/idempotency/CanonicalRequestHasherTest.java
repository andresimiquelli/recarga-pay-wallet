package recargapay.wallet.adapter.in.api.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CanonicalRequestHasherTest {
    private final CanonicalRequestHasher hasher = new CanonicalRequestHasher();

    @Test
    void shouldGenerateSameHashForEquivalentJsonWithDifferentFieldOrder() {
        String firstHash = hasher.hash(
                "POST",
                "/api/test",
                null,
                "{\"b\":2,\"a\":{\"d\":4,\"c\":3}}".getBytes(StandardCharsets.UTF_8));
        String secondHash = hasher.hash(
                "POST",
                "/api/test",
                null,
                "{\"a\":{\"c\":3,\"d\":4},\"b\":2}".getBytes(StandardCharsets.UTF_8));

        assertEquals(firstHash, secondHash);
    }

    @Test
    void shouldGenerateDifferentHashWhenPayloadChanges() {
        String firstHash = hasher.hash(
                "POST",
                "/api/test",
                null,
                "{\"amount\":10.00}".getBytes(StandardCharsets.UTF_8));
        String secondHash = hasher.hash(
                "POST",
                "/api/test",
                null,
                "{\"amount\":11.00}".getBytes(StandardCharsets.UTF_8));

        assertNotEquals(firstHash, secondHash);
    }
}
