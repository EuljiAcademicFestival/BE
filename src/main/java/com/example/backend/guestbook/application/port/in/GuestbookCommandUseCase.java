package com.example.backend.guestbook.application.port.in;

import com.example.backend.guestbook.application.result.LikeResult;

public interface GuestbookCommandUseCase {
    LikeResult setLike(long memberId, long likerId, boolean liked);
}
