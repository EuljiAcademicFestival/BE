package com.example.backend.guestbook.application.result;

import java.util.List;

public record EngagementSnapshot(List<Count> counts, List<Ranking> rankings) {
    public EngagementSnapshot {
        counts = List.copyOf(counts); rankings = List.copyOf(rankings);
    }
    public record Count(long memberId, long likeCount) {}
    public record Ranking(long memberId, String grade, String name, String guestbookMessage, long likeCount, int rank) {
        public static Ranking from(EntryView e) {
            return new Ranking(e.memberId(), e.grade(), e.name(), e.guestbookMessage(), e.likeCount(), e.rank());
        }
    }
}
