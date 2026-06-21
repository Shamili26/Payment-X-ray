package com.paymentapp.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Hashes session tokens for storage/lookup in the {@code user_sessions} table.
 *
 * <p>Uses SHA-256 rather than MD5: MD5 is cryptographically broken and must not
 * be used for any security-sensitive digest. Tokens are high-entropy JWTs, so a
 * fast one-way hash (SHA-256) is appropriate here — a slow KDF like BCrypt is
 * only needed for low-entropy secrets such as user passwords.
 */
public final class TokenHasher {

    private TokenHasher() {
    }

    /** Returns the lowercase hex-encoded SHA-256 digest of the given token. */
    public static String sha256Hex(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be present on every JVM.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}