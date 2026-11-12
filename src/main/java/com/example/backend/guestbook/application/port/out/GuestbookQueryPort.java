package com.example.backend.guestbook.application.port.out;

import com.example.backend.guestbook.application.result.*;
import com.example.backend.guestbook.domain.model.*;
import java.util.*;

public interface GuestbookQueryPort {
    List<CharacterLabel> labels(List<Long> memberIds);
    Optional<EntryView> detail(long memberId);
    List<EntryView> topRankings();
    List<EntryView> page(long viewerId, GuestbookSort sort, GuestbookCursor cursor, int fetchSize);
}
