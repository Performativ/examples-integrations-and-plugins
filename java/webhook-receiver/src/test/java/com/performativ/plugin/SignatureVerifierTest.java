package com.performativ.plugin;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SignatureVerifier}.
 *
 * <p>Covers the seven cases a timestamped HMAC verifier must handle:
 * <ol>
 *   <li>Happy path: valid timestamp + valid signature.</li>
 *   <li>Expired timestamp.</li>
 *   <li>Timestamp in the future beyond the freshness window.</li>
 *   <li>Missing timestamp header.</li>
 *   <li>Missing signature header.</li>
 *   <li>Tampered body.</li>
 *   <li>Tampered timestamp (body and signature unchanged).</li>
 * </ol>
 */
class SignatureVerifierTest {

    private static final String KEY = "test-signing-key-32-chars-minimum-123";
    private static final byte[] BODY = "{\"event_id\":\"abc\",\"entity\":\"Client\"}"
            .getBytes(StandardCharsets.UTF_8);

    /**
     * Construct a verifier whose internal clock always returns {@code nowSeconds}.
     * Lets each test pin "now" to a known value so timestamps produced by the test
     * are compared against a deterministic reference.
     */
    private SignatureVerifier verifierAt(long nowSeconds) {
        return new SignatureVerifier(KEY, () -> nowSeconds);
    }

    @Test
    void happyPath_validTimestampAndSignature_accepts() {
        long now = 1_700_000_000L;
        SignatureVerifier verifier = verifierAt(now);
        String timestamp = Long.toString(now);
        String signature = verifier.sign(timestamp, BODY);

        assertTrue(verifier.verify(BODY, timestamp, signature));
    }

    @Test
    void expiredTimestamp_beyondFreshnessWindow_rejects() {
        long now = 1_700_000_000L;
        SignatureVerifier verifier = verifierAt(now);
        long stale = now - (SignatureVerifier.FRESHNESS_WINDOW_SECONDS + 1);
        String timestamp = Long.toString(stale);
        String signature = verifier.sign(timestamp, BODY);

        assertFalse(verifier.verify(BODY, timestamp, signature));
    }

    @Test
    void futureTimestamp_beyondFreshnessWindow_rejects() {
        long now = 1_700_000_000L;
        SignatureVerifier verifier = verifierAt(now);
        long future = now + (SignatureVerifier.FRESHNESS_WINDOW_SECONDS + 1);
        String timestamp = Long.toString(future);
        String signature = verifier.sign(timestamp, BODY);

        assertFalse(verifier.verify(BODY, timestamp, signature));
    }

    @Test
    void missingTimestampHeader_rejects() {
        long now = 1_700_000_000L;
        SignatureVerifier verifier = verifierAt(now);
        String timestamp = Long.toString(now);
        String signature = verifier.sign(timestamp, BODY);

        assertFalse(verifier.verify(BODY, null, signature));
        assertFalse(verifier.verify(BODY, "", signature));
        assertFalse(verifier.verify(BODY, "   ", signature));
    }

    @Test
    void missingSignatureHeader_rejects() {
        long now = 1_700_000_000L;
        SignatureVerifier verifier = verifierAt(now);
        String timestamp = Long.toString(now);

        assertFalse(verifier.verify(BODY, timestamp, null));
        assertFalse(verifier.verify(BODY, timestamp, ""));
        assertFalse(verifier.verify(BODY, timestamp, "  "));
    }

    @Test
    void tamperedBody_rejects() {
        long now = 1_700_000_000L;
        SignatureVerifier verifier = verifierAt(now);
        String timestamp = Long.toString(now);
        String signature = verifier.sign(timestamp, BODY);

        byte[] tamperedBody = "{\"event_id\":\"xyz\",\"entity\":\"Client\"}"
                .getBytes(StandardCharsets.UTF_8);

        assertFalse(verifier.verify(tamperedBody, timestamp, signature));
    }

    @Test
    void tamperedTimestamp_rejects() {
        long now = 1_700_000_000L;
        SignatureVerifier verifier = verifierAt(now);
        String originalTimestamp = Long.toString(now);
        String signature = verifier.sign(originalTimestamp, BODY);

        // Attacker swaps the timestamp to a different value still inside the freshness
        // window. The signature was computed for the original timestamp, so the HMAC
        // over the new (timestamp, body) pair differs and the verifier rejects.
        String tamperedTimestamp = Long.toString(now - 60);

        assertFalse(verifier.verify(BODY, tamperedTimestamp, signature));
    }

    @Test
    void nonNumericTimestamp_rejects() {
        long now = 1_700_000_000L;
        SignatureVerifier verifier = verifierAt(now);
        String signature = verifier.sign(Long.toString(now), BODY);

        assertFalse(verifier.verify(BODY, "not-a-number", signature));
    }

    @Test
    void malformedHexSignature_rejects() {
        long now = 1_700_000_000L;
        SignatureVerifier verifier = verifierAt(now);
        String timestamp = Long.toString(now);

        assertFalse(verifier.verify(BODY, timestamp, "zzzzzz"));
    }
}
