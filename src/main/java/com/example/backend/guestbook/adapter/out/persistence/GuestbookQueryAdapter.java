package com.example.backend.guestbook.adapter.out.persistence;

import com.example.backend.guestbook.application.port.out.GuestbookQueryPort;
import com.example.backend.guestbook.application.result.*;
import com.example.backend.guestbook.domain.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class GuestbookQueryAdapter implements GuestbookQueryPort {
    private final NamedParameterJdbcTemplate jdbc;
    // Rank before applying pagination or member filters so every API uses the global competition rank.
    private static final String RANKED = """
            WITH ranked AS (
                SELECT member_id, message, like_count, created_at,
                       RANK() OVER (ORDER BY like_count DESC) AS global_rank
                FROM guestbook_entries
            )
            SELECT m.id AS member_id, m.character_id, m.grade, m.name,
                   r.message, r.like_count, r.created_at,
                   CASE WHEN r.global_rank <= 3 THEN r.global_rank ELSE NULL END AS member_rank,
                   EXISTS(SELECT 1 FROM guestbook_likes l
                          WHERE l.entry_member_id = r.member_id AND l.liker_member_id = :viewerId) AS liked_by_me
            FROM ranked r JOIN members m ON m.id = r.member_id
            """;
    private static final RowMapper<EntryView> ENTRY = (rs, row) -> {
        Number rank = (Number) rs.getObject("member_rank");
        return new EntryView(rs.getLong("member_id"), rs.getInt("character_id"), rs.getString("grade"),
                rs.getString("name"), rank == null ? null : rank.intValue(), rs.getString("message"),
                rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                rs.getLong("like_count"), rs.getBoolean("liked_by_me"));
    };
    public List<CharacterLabel> labels(List<Long> ids) {
        return jdbc.query("SELECT id, grade, name FROM members WHERE id IN (:ids) ORDER BY id",
                Map.of("ids", ids), (rs, row) -> new CharacterLabel(rs.getLong("id"), rs.getString("grade"), rs.getString("name")));
    }
    public Optional<EntryView> detail(long memberId) {
        return jdbc.query(RANKED + " WHERE r.member_id = :memberId", Map.of("viewerId", 0L, "memberId", memberId), ENTRY)
                .stream().findFirst();
    }
    public List<EntryView> topRankings() {
        return jdbc.query(RANKED + " WHERE r.global_rank <= 3 ORDER BY r.global_rank, r.created_at DESC, r.member_id ASC",
                Map.of("viewerId", 0L), ENTRY);
    }
    public List<EntryView> page(long viewerId, GuestbookSort sort, GuestbookCursor cursor, int fetchSize) {
        var parameters = new MapSqlParameterSource().addValue("viewerId", viewerId).addValue("fetchSize", fetchSize);
        String after = "";
        String timeAfter = "(r.created_at < :createdAt OR (r.created_at = :createdAt AND r.member_id > :memberId))";
        if (cursor != null) {
            parameters.addValue("createdAt", LocalDateTime.ofInstant(cursor.createdAt(), ZoneOffset.UTC))
                    .addValue("memberId", cursor.memberId()).addValue("likeCount", cursor.likeCount());
            after = " WHERE " + (sort == GuestbookSort.LATEST ? timeAfter
                    : "(r.like_count < :likeCount OR (r.like_count = :likeCount AND " + timeAfter + "))");
        }
        String order = sort == GuestbookSort.POPULAR ? "r.like_count DESC, " : "";
        return jdbc.query(RANKED + after + " ORDER BY " + order + "r.created_at DESC, r.member_id ASC LIMIT :fetchSize",
                parameters, ENTRY);
    }
}
