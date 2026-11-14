package com.example.backend.guestbook.application.port.out;

import com.example.backend.guestbook.application.result.CharacterLabel;
import java.util.List;
import java.util.function.Function;

public interface CharacterLabelCachePort {
    List<CharacterLabel> getAll(List<Long> ids, Function<List<Long>, List<CharacterLabel>> loadMissing);
    void evictAfterCommit(long memberId);
}
