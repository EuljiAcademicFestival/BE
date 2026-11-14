package com.example.backend.member.security;

import com.example.backend.member.adapter.out.security.AccessTokenAdapter;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class AccessTokenAdapterTest {
    private static final Instant NOW = Instant.parse("2026-11-15T00:00:00Z");
    private static final String ISSUER = "eulji-academic-festival";
    // Public deterministic test fixture, never used as an application default.
    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);
    private static final SecretKey KEY = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET));

    private AccessTokenAdapter adapter(Instant now) {
        return new AccessTokenAdapter(Clock.fixed(now, ZoneOffset.UTC), Duration.ofHours(1), SECRET, ISSUER);
    }
    private JwtBuilder claims() {
        return Jwts.builder().issuer(ISSUER).subject("42").claim("token_type", "access")
                .issuedAt(Date.from(NOW)).expiration(Date.from(NOW.plusSeconds(3600)));
    }
    private String sign(JwtBuilder builder) { return builder.signWith(KEY, Jwts.SIG.HS256).compact(); }

    @Test
    void issuesDistinctSignedTokensAndAcceptsThemBeforeExpiry() {
        var adapter = adapter(NOW);
        String token = adapter.issue(42);
        assertThat(adapter.authenticate(token)).contains(42L);
        assertThat(adapter.issue(42)).isNotEqualTo(token);
        var parsed = Jwts.parser().verifyWith(KEY).clock(() -> Date.from(NOW)).build().parseSignedClaims(token);
        assertThat(parsed.getHeader().getAlgorithm()).isEqualTo("HS256");
        assertThat(parsed.getPayload().getIssuer()).isEqualTo(ISSUER);
        assertThat(parsed.getPayload().get("token_type")).isEqualTo("access");
        assertThat(parsed.getPayload().getId()).isNotBlank();
        assertThat(parsed.getPayload().getExpiration().toInstant()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(adapter(NOW.plusSeconds(3599)).authenticate(token)).contains(42L);
    }

    @Test
    void rejectsExpiryAtTheExactBoundaryAndAfterwards() {
        String token = adapter(NOW).issue(42);
        assertThat(adapter(NOW.plusSeconds(3600)).authenticate(token)).isEmpty();
        assertThat(adapter(NOW.plusSeconds(3601)).authenticate(token)).isEmpty();
    }

    @Test
    void rejectsMalformedUnsignedOldAndOversizedTokens() {
        var adapter = adapter(NOW);
        assertThat(adapter.authenticate(null)).isEmpty();
        for (String token : List.of("", " ", "not-a-jwt", "A".repeat(43), "x".repeat(4097), claims().compact())) {
            assertThat(adapter.authenticate(token)).isEmpty();
        }
    }

    @Test
    void rejectsWrongSignaturesAndTamperedPayloads() {
        byte[] other = new byte[32]; Arrays.fill(other, (byte) 1);
        String token = claims().signWith(Keys.hmacShaKeyFor(other), Jwts.SIG.HS256).compact();
        assertThat(adapter(NOW).authenticate(token)).isEmpty();
        String[] parts = sign(claims()).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("\"42\"", "\"43\"");
        String tampered = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];
        assertThat(adapter(NOW).authenticate(tampered)).isEmpty();
    }

    @Test
    void rejectsOtherAlgorithmsEvenWithTheSameKey() {
        String secret = Base64.getEncoder().encodeToString(new byte[64]);
        var adapter = new AccessTokenAdapter(Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(1), secret, ISSUER);
        String token = claims().signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret)), Jwts.SIG.HS384).compact();
        assertThat(adapter.authenticate(token)).isEmpty();
    }

    @Test
    void requiresIssuerPurposeDatesAndCanonicalPositiveMemberId() {
        var adapter = adapter(NOW);
        for (JwtBuilder invalid : List.of(claims().issuer("other-project"), claims().issuer(null),
                claims().claim("token_type", "refresh"), claims().claim("token_type", null),
                claims().expiration(null), claims().issuedAt(null),
                claims().issuedAt(Date.from(NOW.plusSeconds(10))), claims().notBefore(Date.from(NOW.plusSeconds(10))),
                claims().subject(null), claims().subject("0"), claims().subject("-1"), claims().subject("+1"),
                claims().subject("01"), claims().subject("not-a-number"), claims().subject("9223372036854775808"))) {
            assertThat(adapter.authenticate(sign(invalid))).isEmpty();
        }
    }

    @Test
    void validatesConfigurationAndRejectsInvalidIssuance() {
        for (String secret : List.of("", "!invalid-base64!", Base64.getEncoder().encodeToString(new byte[8]))) {
            assertThatThrownBy(() -> new AccessTokenAdapter(Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(1), secret, ISSUER))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new AccessTokenAdapter(Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMillis(999), SECRET, ISSUER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AccessTokenAdapter(Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(1), SECRET, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adapter(NOW).issue(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
