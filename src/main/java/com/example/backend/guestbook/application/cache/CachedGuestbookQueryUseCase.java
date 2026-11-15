package com.example.backend.guestbook.application.cache;

import com.example.backend.guestbook.application.port.in.GuestbookQueryUseCase;
import com.example.backend.guestbook.application.port.out.CharacterLabelCachePort;
import com.example.backend.guestbook.application.port.out.TopRankingCachePort;
import com.example.backend.guestbook.application.result.*;
import com.example.backend.guestbook.domain.policy.CharacterLabelPolicy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import java.util.List;

/** Cache hits stop here, before entering the transactional query service. */
@Component
@Primary
public class CachedGuestbookQueryUseCase implements GuestbookQueryUseCase {
    private final GuestbookQueryUseCase delegate;
    private final CharacterLabelCachePort cache;
    private final TopRankingCachePort rankings;
    public CachedGuestbookQueryUseCase(@Qualifier("guestbookQueryService") GuestbookQueryUseCase delegate,
                                       CharacterLabelCachePort cache, TopRankingCachePort rankings) {
        this.delegate = delegate;
        this.cache = cache;
        this.rankings = rankings;
    }
    public List<CharacterLabel> labels(List<Long> ids) {
        return cache.getAll(CharacterLabelPolicy.normalizeIds(ids), delegate::labels);
    }
    public EntryView detail(long viewerId, long memberId) { return delegate.detail(viewerId, memberId); }
    public List<EntryView> topRankings() { return rankings.get(delegate::topRankings); }
    public GuestbookPage page(long viewerId, String sort, String cursor, int limit) {
        return delegate.page(viewerId, sort, cursor, limit);
    }
}
