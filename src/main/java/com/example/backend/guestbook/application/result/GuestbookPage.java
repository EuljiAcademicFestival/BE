package com.example.backend.guestbook.application.result;

import java.util.List;

public record GuestbookPage(List<GuestbookItem> entries, String nextCursor) {}
