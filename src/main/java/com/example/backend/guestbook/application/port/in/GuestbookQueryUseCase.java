package com.example.backend.guestbook.application.port.in;

import com.example.backend.guestbook.application.result.*;
import java.util.List;

public interface GuestbookQueryUseCase {
    List<CharacterLabel> labels(List<Long> memberIds);
    EntryView detail(long memberId);
    List<EntryView> topRankings();
    GuestbookPage page(long viewerId, String sort, String cursor, int limit);
}
