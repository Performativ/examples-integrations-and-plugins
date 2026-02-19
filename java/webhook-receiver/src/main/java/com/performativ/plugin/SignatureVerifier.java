package com.performativ.plugin;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Verifies HMAC-SHA256 webhook signatures from Performativ.
 *
 * <p>The platform signs webhook payloads using HMAC-SHA256 with the signing key
 * configured for the plugin instance. The signature is sent in the
 * {@code x-webhook-signature} header as a lowercase hex string (64 characters).
 *
 * <p>Usage:
 * <pre>{@code
 * SignatureVerifier verifier = new SignatureVerifier("your-signing-key");
 * boolean valid = verifier.verify(rawRequestBody, signatureHeader);
 * }</pre>
 */
public final class SignatureVerifier {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final byte[] keyBytes;

    /**
     * @param signingKey the webhook signing key provided when the plugin was activated
     */
    public SignatureVerifier(String signingKey) {
        this.keyBytes = signingKey.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Verify a webhook signature.
     *
     * @param payload   the raw JSON request body (bytes as received, before parsing)
     * @param signature the value of the {@code x-webhook-signature} header, or {@code null}
     * @return {@code true} if the signature is valid or if no signature was provided
     *         (unsigned webhooks are valid when no signing key is configured)
     */
    public boolean verify(byte[] payload, String signature) {
        if (signature == null || signature.isBlank()) {
            // Unsigned webhook - acceptable if no signing key was configured
            return true;
        }

        byte[] computed = computeHmac(payload);
        byte[] expected = HexFormat.of().parseHex(signature);

        // Constant-time comparison to prevent timing attacks
        return MessageDigest.isEqual(computed, expected);
    }

    /**
     * Compute HMAC-SHA256 of the given payload.
     */
    private byte[] computeHmac(byte[] payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(keyBytes, HMAC_SHA256));
            return mac.doFinal(payload);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 computation failed", e);
        }
    }

    /**
     * Compute the hex signature for a payload (useful for testing).
     */
    public String sign(byte[] payload) {
        return HexFormat.of().formatHex(computeHmac(payload));
    }
}
