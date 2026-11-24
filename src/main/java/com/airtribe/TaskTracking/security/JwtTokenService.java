package com.airtribe.TaskTracking.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JwtTokenService {
    private static final String HEADER = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long expirationMs;
    private final Map<String, Instant> revokedTokens = new ConcurrentHashMap<>();

    public JwtTokenService(
            ObjectMapper objectMapper,
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        }
        if (expirationMs <= 0) {
            throw new IllegalArgumentException("JWT expiration must be positive");
        }
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationMs = expirationMs;
    }

    public String issue(String subject) {
        long expiresAt = Instant.now().plusMillis(expirationMs).getEpochSecond();
        try {
            String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    objectMapper.writeValueAsBytes(Map.of("sub", subject, "exp", expiresAt)));
            String signedContent = HEADER + "." + payload;
            return signedContent + "." + sign(signedContent);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not create authentication token", e);
        }
    }

    public String validate(String token) {
        TokenClaims claims = readAndVerify(token);
        if (claims.expiresAt().isBefore(Instant.now())) {
            throw new JwtAuthenticationException("Authentication token has expired");
        }
        Instant revokedAt = revokedTokens.get(token);
        if (revokedAt != null) {
            if (revokedAt.isAfter(Instant.now())) {
                throw new JwtAuthenticationException("Authentication token has been revoked");
            }
            revokedTokens.remove(token, revokedAt);
        }
        return claims.subject();
    }

    public void revoke(String token) {
        TokenClaims claims = readAndVerify(token);
        Instant now = Instant.now();
        revokedTokens.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
        if (claims.expiresAt().isAfter(Instant.now())) {
            revokedTokens.put(token, claims.expiresAt());
        }
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }

    private TokenClaims readAndVerify(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3 || !HEADER.equals(parts[0])) {
                throw new JwtAuthenticationException("Invalid authentication token");
            }
            String signedContent = parts[0] + "." + parts[1];
            byte[] expected = Base64.getUrlDecoder().decode(sign(signedContent));
            byte[] provided = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(expected, provided)) {
                throw new JwtAuthenticationException("Invalid authentication token signature");
            }
            JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode subject = payload.get("sub");
            JsonNode expiration = payload.get("exp");
            if (subject == null || !subject.isTextual() || expiration == null || !expiration.canConvertToLong()) {
                throw new JwtAuthenticationException("Invalid authentication token claims");
            }
            return new TokenClaims(subject.asText(), Instant.ofEpochSecond(expiration.asLong()));
        } catch (IllegalArgumentException | JacksonException e) {
            throw new JwtAuthenticationException("Invalid authentication token");
        }
    }

    private String sign(String content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not sign authentication token", e);
        }
    }

    private record TokenClaims(String subject, Instant expiresAt) {
    }
}
