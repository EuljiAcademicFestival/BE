package com.example.backend.realtime.application.port.in;

public interface EngagementSyncUseCase {
    void changed(long memberId);
    void requestFullSync(String connectionId);
    void flush();
}
