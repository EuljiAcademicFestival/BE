package com.example.backend.guestbook.application.port.out;

public interface GuestbookLikePort {
    /** Returns true only when a new like was inserted. */
    boolean add(long memberId, long likerId);
    /** Returns true only when an existing like was deleted. */
    boolean remove(long memberId, long likerId);
}
