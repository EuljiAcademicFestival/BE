package com.example.backend.guestbook.application.service;

import com.example.backend.global.domain.exception.*;
import com.example.backend.guestbook.application.port.in.GuestbookQueryUseCase;
import com.example.backend.guestbook.application.port.out.GuestbookQueryPort;
import com.example.backend.guestbook.application.result.*;
import com.example.backend.guestbook.domain.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class GuestbookQueryService implements GuestbookQueryUseCase {
    private final GuestbookQueryPort queries;
    private final Clock clock;
    public List<CharacterLabel> labels(List<Long> memberIds) {
        if (memberIds == null || memberIds.isEmpty() || memberIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(ErrorCode.CHARACTER_INVALID_INPUT);
        }
        return queries.labels(memberIds.stream().distinct().toList());
    }
    public EntryView detail(long memberId) {
        if (memberId <= 0) throw new BusinessException(ErrorCode.CHARACTER_INVALID_INPUT);
        return queries.detail(memberId).orElseThrow(() -> new BusinessException(ErrorCode.CHARACTER_NOT_FOUND));
    }
    public List<EntryView> topRankings() { return queries.topRankings(); }
    public GuestbookPage page(long viewerId, String sortValue, String cursorValue, int limit) {
        GuestbookSort sort = GuestbookSort.parse(sortValue);
        if (limit < 1 || limit > 100) throw new BusinessException(ErrorCode.GUESTBOOK_INVALID_QUERY);
        GuestbookCursor cursor = GuestbookCursor.decode(cursorValue, sort);
        Instant now = clock.instant();
        List<EntryView> fetched = queries.page(viewerId, sort, cursor, limit + 1);
        List<EntryView> entries = fetched.stream().limit(limit).toList();
        String next = null;
        if (fetched.size() > limit) {
            EntryView last = entries.get(entries.size() - 1);
            next = new GuestbookCursor(sort, last.likeCount(), last.createdAt(), last.memberId()).encode();
        }
        return new GuestbookPage(entries.stream().map(entry -> new GuestbookItem(entry.memberId(), entry.characterId(),
                entry.grade(), entry.name(), entry.rank(), entry.guestbookMessage(), entry.createdAt(),
                elapsed(entry.createdAt(), now), entry.likeCount(), entry.likedByMe())).toList(), next);
    }
    private String elapsed(Instant createdAt, Instant now) {
        long seconds = Math.max(0, Duration.between(createdAt, now).getSeconds());
        if (seconds < 60) return "방금 전";
        if (seconds < 3600) return seconds / 60 + "분 전";
        if (seconds < 86400) return seconds / 3600 + "시간 전";
        return seconds / 86400 + "일 전";
    }
}
