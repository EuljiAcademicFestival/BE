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
    // Only ranks 1..3 are exposed. Three leading rows contain every qualifying score,
    // even when ties extend beyond row three. Join scores back to include ALL tied members.
    private static final String TOP_RANKS = """
            WITH top_scores AS (
                SELECT like_count FROM guestbook_entries ORDER BY like_count DESC LIMIT 3
            ), top_ranks AS (
                SELECT like_count, MIN(global_rank) AS member_rank
                FROM (
                    SELECT like_count, RANK() OVER (ORDER BY like_count DESC) AS global_rank
                    FROM top_scores
                ) ranked_scores
                GROUP BY like_count
            )
            """;
    private static final String SELECT_ENTRY = """
            SELECT m.id AS member_id, m.character_id, m.grade, m.name,
                   e.message, e.like_count, e.created_at, r.member_rank,
                   EXISTS(SELECT 1 FROM guestbook_likes l
                          WHERE l.entry_member_id = e.member_id AND l.liker_member_id = :viewerId) AS liked_by_me
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
        return jdbc.query(TOP_RANKS + SELECT_ENTRY
                        + " FROM guestbook_entries e JOIN members m ON m.id = e.member_id"
                        + " LEFT JOIN top_ranks r ON r.like_count = e.like_count WHERE e.member_id = :memberId",
                Map.of("viewerId", 0L, "memberId", memberId), ENTRY)
                .stream().findFirst();
    }
    public List<EntryView> topRankings() {
        return jdbc.query(TOP_RANKS + SELECT_ENTRY
                        + " FROM top_ranks r JOIN guestbook_entries e ON e.like_count = r.like_count"
                        + " JOIN members m ON m.id = e.member_id"
                        + " ORDER BY r.member_rank, e.created_at DESC, e.member_id ASC",
                Map.of("viewerId", 0L), ENTRY);
    }
    public List<EntryView> page(long viewerId, GuestbookSort sort, GuestbookCursor cursor, int fetchSize) {
        var parameters = new MapSqlParameterSource().addValue("viewerId", viewerId).addValue("fetchSize", fetchSize);
        String after = "";
        String timeAfter = "(created_at < :createdAt OR (created_at = :createdAt AND member_id > :memberId))";
        if (cursor != null) {
            parameters.addValue("createdAt", LocalDateTime.ofInstant(cursor.createdAt(), ZoneOffset.UTC))
                    .addValue("memberId", cursor.memberId()).addValue("likeCount", cursor.likeCount());
            after = " WHERE " + (sort == GuestbookSort.LATEST ? timeAfter
                    : "(like_count < :likeCount OR (like_count = :likeCount AND " + timeAfter + "))");
        }
        boolean popular = sort == GuestbookSort.POPULAR;
        String order = (popular ? "like_count DESC, " : "") + "created_at DESC, member_id ASC";
        // Select only indexed columns before LIMIT. Fetch messages/member data for this page only.
        String page = ", page_entries AS (SELECT member_id, created_at" + (popular ? ", like_count" : "")
                + " FROM guestbook_entries" + after + " ORDER BY " + order + " LIMIT :fetchSize) ";
        return jdbc.query(TOP_RANKS + page + SELECT_ENTRY
                        + " FROM page_entries p JOIN guestbook_entries e ON e.member_id = p.member_id"
                        + " JOIN members m ON m.id = p.member_id LEFT JOIN top_ranks r ON r.like_count = e.like_count"
                        + " ORDER BY " + (popular ? "p.like_count DESC, " : "") + "p.created_at DESC, p.member_id ASC",
                parameters, ENTRY);
    }
}
