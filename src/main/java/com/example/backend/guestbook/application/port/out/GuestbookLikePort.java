package com.example.backend.guestbook.application.port.out;

public interface GuestbookLikePort {
    boolean exists(long memberId, long likerId);
    void add(long memberId, long likerId);
    void remove(long memberId, long likerId);
}
