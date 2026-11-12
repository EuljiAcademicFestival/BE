package com.example.backend.guestbook.adapter.out.persistence;

import com.example.backend.guestbook.application.port.out.*;
import com.example.backend.guestbook.domain.model.GuestbookEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class GuestbookPersistenceAdapter implements GuestbookPort, GuestbookLikePort {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public GuestbookEntry create(long memberId, String message) {
        var now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        jdbc.update("INSERT INTO guestbook_entries (member_id, message, created_at) VALUES (?, ?, ?)",
                memberId, message, LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        return new GuestbookEntry(memberId, message, 0, now);
    }
    public Optional<GuestbookEntry> lockByMemberId(long memberId) {
        return jdbc.query("SELECT member_id, message, like_count, created_at FROM guestbook_entries "
                        + "WHERE member_id = ? FOR UPDATE", (rs, row) -> new GuestbookEntry(rs.getLong("member_id"),
                        rs.getString("message"), rs.getLong("like_count"),
                        rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)),
                memberId).stream().findFirst();
    }
    public void updateMessage(GuestbookEntry entry) {
        jdbc.update("UPDATE guestbook_entries SET message = ? WHERE member_id = ?", entry.message(), entry.memberId());
    }
    public void updateLikeCount(long memberId, long count) {
        jdbc.update("UPDATE guestbook_entries SET like_count = ? WHERE member_id = ?", count, memberId);
    }
    public boolean exists(long memberId, long likerId) {
        // A locking read avoids stale snapshots even when called from an existing transaction.
        return !jdbc.query("SELECT liker_member_id FROM guestbook_likes "
                + "WHERE entry_member_id = ? AND liker_member_id = ? FOR UPDATE",
                (rs, row) -> rs.getLong(1), memberId, likerId).isEmpty();
    }
    public void add(long memberId, long likerId) {
        jdbc.update("INSERT INTO guestbook_likes (entry_member_id, liker_member_id) VALUES (?, ?)", memberId, likerId);
    }
    public void remove(long memberId, long likerId) {
        jdbc.update("DELETE FROM guestbook_likes WHERE entry_member_id = ? AND liker_member_id = ?", memberId, likerId);
    }
}
