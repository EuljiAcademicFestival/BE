package com.example.backend.member.adapter.out.security;

import com.example.backend.member.application.port.out.AccessTokenPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Repository
public class AccessTokenAdapter implements AccessTokenPort {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    public AccessTokenAdapter(JdbcTemplate jdbc, Clock clock,
                              @Value("${security.access-token-ttl:PT1H}") Duration ttl) {
        if (ttl.isNegative() || ttl.isZero()) throw new IllegalArgumentException("Token TTL must be positive");
        this.jdbc = jdbc; this.clock = clock; this.ttl = ttl;
    }
    public String issue(long memberId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO access_tokens (token_hash, member_id, expires_at) VALUES (?, ?, ?)",
                digest(token), memberId, LocalDateTime.ofInstant(clock.instant().plus(ttl), ZoneOffset.UTC));
        return token;
    }
    public Optional<Long> authenticate(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return Optional.empty();
        return jdbc.query("SELECT t.member_id FROM access_tokens t JOIN members m ON m.id = t.member_id "
                        + "WHERE t.token_hash = ? AND t.expires_at > ?", (rs, row) -> rs.getLong(1),
                digest(token), LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)).stream().findFirst();
    }
    private String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
