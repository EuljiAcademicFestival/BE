package com.example.backend.member.adapter.out.security;

import com.example.backend.member.application.port.out.AccessTokenPort;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
public class AccessTokenAdapter implements AccessTokenPort {
    private final Clock clock;
    private final Duration ttl;
    private final SecretKey key;
    private final JwtParser parser;
    private final String issuer;

    public AccessTokenAdapter(Clock clock,
                              @Value("${security.access-token-ttl:PT1H}") Duration ttl,
                              @Value("${security.jwt-secret}") String secret,
                              @Value("${security.jwt-issuer:eulji-academic-festival}") String issuer) {
        if (ttl.compareTo(Duration.ofSeconds(1)) < 0) throw new IllegalArgumentException("Token TTL must be at least one second");
        if (issuer == null || issuer.isBlank()) throw new IllegalArgumentException("JWT issuer must not be blank");
        if (secret == null || secret.isBlank()) throw new IllegalArgumentException("JWT_SECRET must be configured");
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(secret); }
        catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("JWT_SECRET must be a Base64-encoded random key"); }
        if (bytes.length < 32) throw new IllegalArgumentException("JWT_SECRET must encode at least 32 random bytes");
        this.clock = clock;
        this.ttl = ttl;
        this.issuer = issuer;
        this.key = Keys.hmacShaKeyFor(bytes);
        this.parser = Jwts.parser().verifyWith(key)
                .sig().clear().add(Jwts.SIG.HS256).and()
                .requireIssuer(issuer).require("token_type", "access")
                .clock(() -> Date.from(clock.instant())).build();
    }
    public String issue(long memberId) {
        if (memberId <= 0) throw new IllegalArgumentException("Member ID must be positive");
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        return Jwts.builder().issuer(issuer).subject(Long.toString(memberId))
                .claim("token_type", "access").id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt)).expiration(Date.from(issuedAt.plus(ttl)))
                .signWith(key, Jwts.SIG.HS256).compact();
    }
    public Optional<Long> authenticate(String token) {
        if (token == null || token.isBlank() || token.length() > 4096) return Optional.empty();
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            Instant now = clock.instant();
            Date issuedAt = claims.getIssuedAt(), expiresAt = claims.getExpiration();
            if (issuedAt == null || expiresAt == null || issuedAt.toInstant().isAfter(now)
                    || !expiresAt.toInstant().isAfter(now) || !expiresAt.after(issuedAt)) return Optional.empty();
            long memberId = Long.parseLong(claims.getSubject());
            if (memberId <= 0 || !Long.toString(memberId).equals(claims.getSubject())) return Optional.empty();
            return Optional.of(memberId);
        } catch (JwtException | IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }
}
