package com.example.backend.realtime.adapter.out.notification;

import com.example.backend.guestbook.application.port.out.GuestbookEngagementChangedPort;
import com.example.backend.realtime.application.port.in.EngagementSyncUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;

@Component
@RequiredArgsConstructor
public class GuestbookEngagementAdapter implements GuestbookEngagementChangedPort {
    private final EngagementSyncUseCase sync;
    public void afterCommit(long memberId) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) { sync.changed(memberId); return; }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { sync.changed(memberId); }
        });
    }
}
