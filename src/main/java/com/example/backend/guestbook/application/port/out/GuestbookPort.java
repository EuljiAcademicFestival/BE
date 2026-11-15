package com.example.backend.guestbook.application.port.out;

import com.example.backend.guestbook.domain.model.GuestbookEntry;
import java.util.Optional;

public interface GuestbookPort {
    GuestbookEntry create(long memberId, String message);
    Optional<GuestbookEntry> lockByMemberId(long memberId);
    void updateMessage(GuestbookEntry entry);
    void updateLikeCount(long memberId, long count);
}
