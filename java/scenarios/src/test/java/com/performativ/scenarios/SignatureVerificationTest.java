package com.performativ.scenarios;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the HMAC-SHA256 signature computation and verification round-trip.
 * No credentials needed — this tests the algorithm itself.
 */
class SignatureVerificationTest {

    private static final String HMAC_SHA256 = "HmacSHA256";

    @Test
    void signAndVerifyRoundTrip() throws Exception {
        String signingKey = "test-signing-key-12345";
        String payload = "{\"event\":\"Created\",\"entity\":\"Client\",\"data\":{\"id\":1}}";

        // Compute HMAC
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
        byte[] computed = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        String signature = HexFormat.of().formatHex(computed);

        // Signature should be a 64-char lowercase hex string
        assertEquals(64, signature.length());
        assertTrue(signature.matches("[0-9a-f]{64}"));

        // Verify: recompute and compare
        Mac verifyMac = Mac.getInstance(HMAC_SHA256);
        verifyMac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
        byte[] recomputed = verifyMac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        byte[] expected = HexFormat.of().parseHex(signature);

        assertTrue(MessageDigest.isEqual(recomputed, expected));
    }

    @Test
    void wrongKeyFailsVerification() throws Exception {
        String payload = "{\"event\":\"Created\",\"entity\":\"Client\"}";

        // Sign with key A
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(new SecretKeySpec("key-a".getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
        byte[] signedWith_A = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

        // Verify with key B
        Mac verifyMac = Mac.getInstance(HMAC_SHA256);
        verifyMac.init(new SecretKeySpec("key-b".getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
        byte[] recomputed = verifyMac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

        assertFalse(MessageDigest.isEqual(signedWith_A, recomputed));
    }

    @Test
    void tamperedPayloadFailsVerification() throws Exception {
        String signingKey = "test-key";
        String original = "{\"amount\":100}";
        String tampered = "{\"amount\":999}";

        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
        byte[] originalSig = mac.doFinal(original.getBytes(StandardCharsets.UTF_8));

        Mac verifyMac = Mac.getInstance(HMAC_SHA256);
        verifyMac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
        byte[] tamperedSig = verifyMac.doFinal(tampered.getBytes(StandardCharsets.UTF_8));

        assertFalse(MessageDigest.isEqual(originalSig, tamperedSig));
    }
}
