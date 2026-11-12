package com.example.backend.guestbook.application;

import com.example.backend.guestbook.application.port.out.GuestbookQueryPort;
import com.example.backend.guestbook.application.result.*;
import com.example.backend.guestbook.application.service.GuestbookQueryService;
import com.example.backend.guestbook.domain.model.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GuestbookQueryServiceTest {
    @Test
    void computesElapsedTextAtOneRequestTimeAndReturnsNextCursor() {
        Instant now = Instant.parse("2026-11-13T00:00:00Z");
        var queries = mock(GuestbookQueryPort.class);
        long[] ages = {0, 59, 60, 3599, 3600, 86399, 86400, 172801, 200000};
        List<EntryView> entries = new ArrayList<>();
        for (int i = 0; i < ages.length; i++) {
            entries.add(new EntryView(i + 1, 1, "게스트", "이름", null, "환영", now.minusSeconds(ages[i]), 0, false));
        }
        when(queries.page(1, GuestbookSort.LATEST, null, 9)).thenReturn(entries);
        var service = new GuestbookQueryService(queries, Clock.fixed(now, ZoneOffset.UTC));
        var page = service.page(1, "latest", null, 8);
        assertThat(page.entries()).extracting(GuestbookItem::elapsedTimeText)
                .containsExactly("방금 전", "방금 전", "1분 전", "59분 전", "1시간 전", "23시간 전", "1일 전", "2일 전");
        assertThat(GuestbookCursor.decode(page.nextCursor(), GuestbookSort.LATEST).memberId()).isEqualTo(8);
    }
}
