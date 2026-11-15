package com.example.backend.guestbook.application.port.out;

public interface GuestbookEngagementChangedPort {
    void afterCommit(long memberId);
}
