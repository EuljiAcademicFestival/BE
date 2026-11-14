package com.example.backend.guestbook.application.service;

import com.example.backend.global.domain.exception.*;
import com.example.backend.guestbook.application.port.in.GuestbookCommandUseCase;
import com.example.backend.guestbook.application.port.out.*;
import com.example.backend.guestbook.application.result.LikeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class GuestbookCommandService implements GuestbookCommandUseCase {
    private final GuestbookPort guestbook;
    private final GuestbookLikePort likes;

    public LikeResult setLike(long memberId, long likerId, boolean liked) {
        if (memberId <= 0) throw new BusinessException(ErrorCode.GUESTBOOK_INVALID_MEMBER_ID);
        var entry = guestbook.lockByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GUESTBOOK_NOT_FOUND));
        long count = entry.likeCount();
        boolean changed = liked ? likes.add(memberId, likerId) : likes.remove(memberId, likerId);
        if (changed) {
            count += liked ? 1 : -1;
            guestbook.updateLikeCount(memberId, count);
        }
        return new LikeResult(memberId, liked, count);
    }
}
