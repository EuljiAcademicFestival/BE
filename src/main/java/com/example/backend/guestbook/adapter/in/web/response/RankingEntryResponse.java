package com.example.backend.guestbook.adapter.in.web.response;

import com.example.backend.guestbook.application.result.EntryView;

public record RankingEntryResponse(long memberId, String grade, String name, String guestbookMessage,
                                   long likeCount, Integer rank) {
    public static RankingEntryResponse from(EntryView entry) {
        return new RankingEntryResponse(entry.memberId(), entry.grade(), entry.name(), entry.guestbookMessage(),
                entry.likeCount(), entry.rank());
    }
}
