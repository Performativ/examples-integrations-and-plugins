package com.performativ.plugin;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Verifies HMAC-SHA256 webhook signatures from Performativ.
 *
 * <p>The platform signs webhook payloads using HMAC-SHA256 with the signing key
 * configured for the plugin instance. The signed content is the delivery
 * timestamp, a literal {@code .} separator, and the raw JSON request body, in
 * that exact order:
 *
 * <pre>
 * HMAC_SHA256(signing_key, "{x-webhook-timestamp}.{raw_body}")
 * </pre>
 *
 * <p>Two headers carry the signing data on every delivery:
 * <ul>
 *   <li>{@code x-webhook-timestamp}: Unix epoch seconds at which the delivery
 *       was enqueued.</li>
 *   <li>{@code x-webhook-signature}: hex-encoded HMAC, 64 lowercase characters.</li>
 * </ul>
 *
 * <p>The verifier also rejects deliveries whose timestamp is more than
 * {@link #FRESHNESS_WINDOW_SECONDS} seconds away from the receiver's own clock.
 * This prevents a captured {@code (timestamp, body, signature)} tuple from
 * being replayed outside the freshness window.
 *
 * <p>Usage:
 * <pre>{@code
 * SignatureVerifier verifier = new SignatureVerifier("your-signing-key");
 * boolean valid = verifier.verify(rawRequestBody, timestampHeader, signatureHeader);
 * }</pre>
 */
public final class SignatureVerifier {

    private static final String HMAC_SHA256 = "HmacSHA256";

    /**
     * Freshness window in seconds. A delivery whose {@code x-webhook-timestamp}
     * is more than this many seconds away from the receiver's clock (in either
     * direction) is rejected. Five minutes is the recommended default.
     */
    public static final long FRESHNESS_WINDOW_SECONDS = 300L;

    private final byte[] keyBytes;
    private final Clock clock;

    /**
     * @param signingKey the webhook signing key provided when the plugin was activated
     */
    public SignatureVerifier(String signingKey) {
        this(signingKey, () -> Instant.now().getEpochSecond());
    }

    /**
     * Package-private constructor for tests that need to inject a deterministic clock.
     */
    SignatureVerifier(String signingKey, Clock clock) {
        this.keyBytes = signingKey.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    /**
     * Verify a webhook signature against its raw body and delivery timestamp.
     *
     * @param rawBody           the raw JSON request body (bytes as received, before parsing)
     * @param timestampHeader   the value of the {@code x-webhook-timestamp} header, or {@code null}
     * @param signatureHeader   the value of the {@code x-webhook-signature} header, or {@code null}
     * @return {@code true} if the signature is valid AND the timestamp is fresh;
     *         {@code false} if either header is missing, the timestamp is outside the
     *         freshness window, or the signature does not match the expected HMAC
     */
    public boolean verify(byte[] rawBody, String timestampHeader, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            // A signing key is configured (this verifier exists), but the request
            // has no signature. Reject. The no-signing-key case should be handled
            // at the call site by not constructing a verifier at all.
            return false;
        }
        if (timestampHeader == null || timestampHeader.isBlank()) {
            // Timestamped signing requires the timestamp header.
            return false;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException e) {
            return false;
        }

        long now = clock.epochSecond();
        if (Math.abs(now - timestamp) > FRESHNESS_WINDOW_SECONDS) {
            // Replay protection: timestamp is outside the freshness window.
            return false;
        }

        byte[] computed = computeHmac(timestampHeader.trim(), rawBody);

        byte[] expected;
        try {
            expected = HexFormat.of().parseHex(signatureHeader);
        } catch (IllegalArgumentException e) {
            return false;
        }

        // Constant-time comparison to prevent timing attacks.
        return MessageDigest.isEqual(computed, expected);
    }

    /**
     * Compute HMAC-SHA256 over {@code "{timestamp}." + rawBody} without allocating
     * a concatenation buffer.
     */
    private byte[] computeHmac(String timestamp, byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(keyBytes, HMAC_SHA256));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            mac.update(rawBody);
            return mac.doFinal();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 computation failed", e);
        }
    }

    /**
     * Compute the hex signature for a (timestamp, body) pair. Useful for tests.
     */
    public String sign(String timestamp, byte[] rawBody) {
        return HexFormat.of().formatHex(computeHmac(timestamp, rawBody));
    }

    /**
     * Abstraction over {@link Instant#now()} so tests can inject a deterministic clock.
     */
    @FunctionalInterface
    interface Clock {
        long epochSecond();
    }
}
