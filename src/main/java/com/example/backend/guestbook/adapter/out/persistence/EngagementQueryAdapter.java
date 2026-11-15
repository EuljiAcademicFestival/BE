package com.example.backend.guestbook.adapter.out.persistence;

import com.example.backend.guestbook.application.port.out.EngagementQueryPort;
import com.example.backend.guestbook.application.result.EngagementSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class EngagementQueryAdapter implements EngagementQueryPort {
    private final NamedParameterJdbcTemplate jdbc;

    public EngagementSnapshot snapshot(Set<Long> ids, boolean full) {
        String predicate = full ? "1=1" : ids.isEmpty() ? "r.member_rank IS NOT NULL"
                : "(e.member_id IN (:ids) OR r.member_rank IS NOT NULL)";
        // One autocommit SELECT provides counts and the complete tied top-3 from one statement snapshot.
        var rows = jdbc.query(GuestbookQueryAdapter.TOP_RANKS + """
                SELECT e.member_id, e.like_count, r.member_rank, m.grade, m.name, e.message
                FROM guestbook_entries e JOIN members m ON m.id = e.member_id
                LEFT JOIN top_ranks r ON r.like_count = e.like_count
                WHERE
                """ + predicate + " ORDER BY r.member_rank IS NULL, r.member_rank, e.created_at DESC, e.member_id ASC",
                Map.of("ids", ids), (rs, row) -> {
                    long memberId = rs.getLong("member_id"), count = rs.getLong("like_count");
                    Number rank = (Number) rs.getObject("member_rank");
                    return new Row(new EngagementSnapshot.Count(memberId, count), rank == null ? null
                            : new EngagementSnapshot.Ranking(memberId, rs.getString("grade"), rs.getString("name"),
                                    rs.getString("message"), count, rank.intValue()));
                });
        return new EngagementSnapshot(rows.stream().map(Row::count).toList(),
                rows.stream().map(Row::ranking).filter(Objects::nonNull).toList());
    }
    private record Row(EngagementSnapshot.Count count, EngagementSnapshot.Ranking ranking) {}
}
