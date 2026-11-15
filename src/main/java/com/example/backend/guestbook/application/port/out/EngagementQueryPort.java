package com.example.backend.guestbook.application.port.out;

import com.example.backend.guestbook.application.result.EngagementSnapshot;
import java.util.Set;

public interface EngagementQueryPort {
    EngagementSnapshot snapshot(Set<Long> changedMemberIds, boolean full);
}
