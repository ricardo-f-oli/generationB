package com.generationb.foundation.internal;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    /** The value that used to be hardcoded in source, application-local.yml and docker-compose. */
    static final String INSECURE_DEFAULT =
            "SuperSecretKeyForSigningJWTsThatIsAtLeast256BitsLongAndVerySecure";

    private final SecretKey key;
    private final long accessTokenExpirationMs;

    public JwtUtil(@Value("${jwt.secret:}") String secretString,
                   @Value("${jwt.access-token-minutes:30}") long accessTokenMinutes,
                   Environment environment) {
        boolean isLocal = Arrays.asList(environment.getActiveProfiles()).contains("local")
                || environment.getActiveProfiles().length == 0
                || Arrays.asList(environment.getActiveProfiles()).contains("test");

        String effective = secretString;
        if (effective == null || effective.isBlank()) {
            if (!isLocal) {
                // Q-B5: fail fast rather than silently signing production tokens with a
                // secret that is published in the repository.
                throw new IllegalStateException(
                        "JWT_SECRET must be set outside the local profile. Refusing to start.");
            }
            effective = INSECURE_DEFAULT;
        }
        if (!isLocal && INSECURE_DEFAULT.equals(effective)) {
            throw new IllegalStateException(
                    "JWT_SECRET is set to the well-known development value. Refusing to start.");
        }
        if (effective.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes (256 bits).");
        }

        this.key = Keys.hmacShaKeyFor(effective.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpirationMs = accessTokenMinutes * 60 * 1000;
    }

    public String generateAccessToken(String email, UUID brandId, UUID userId, String role) {
        return Jwts.builder()
                .subject(email)
                .claim("brand_id", brandId.toString())
                .claim("user_id", userId.toString())
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpirationMs))
                .signWith(key)
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }
}
