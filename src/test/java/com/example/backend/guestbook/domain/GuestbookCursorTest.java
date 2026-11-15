package com.example.backend.guestbook.domain;

import com.example.backend.global.domain.exception.BusinessException;
import com.example.backend.guestbook.domain.model.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GuestbookCursorTest {
    @Test
    void roundTripsMicrosecondPrecisionAndBindsSort() {
        var cursor = new GuestbookCursor(GuestbookSort.POPULAR, 20, Instant.parse("2026-11-10T04:30:00.123456Z"), 12);
        assertThat(GuestbookCursor.decode(cursor.encode(), GuestbookSort.POPULAR)).isEqualTo(cursor);
        assertThatThrownBy(() -> GuestbookCursor.decode(cursor.encode(), GuestbookSort.LATEST)).isInstanceOf(BusinessException.class);
    }
    @Test
    void rejectsInvalidCursors() {
        for (String value : new String[]{"", "!", "abcdef", "a".repeat(513)}) {
            assertThatThrownBy(() -> GuestbookCursor.decode(value, GuestbookSort.POPULAR)).isInstanceOf(BusinessException.class);
        }
        assertThat(GuestbookCursor.decode(null, GuestbookSort.LATEST)).isNull();
    }
}
