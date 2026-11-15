package com.example.backend.guestbook.application.port.out;

import com.example.backend.guestbook.application.result.EntryView;
import java.util.List;
import java.util.function.Supplier;

public interface TopRankingCachePort {
    List<EntryView> get(Supplier<List<EntryView>> load);
}
