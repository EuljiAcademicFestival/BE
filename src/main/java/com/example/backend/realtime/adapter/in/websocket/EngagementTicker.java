package com.example.backend.realtime.adapter.in.websocket;

import com.example.backend.realtime.application.port.in.EngagementSyncUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "realtime.engagement.enabled", havingValue = "true", matchIfMissing = true)
public class EngagementTicker {
    private final EngagementSyncUseCase sync;
    @Scheduled(fixedDelayString = "${realtime.engagement.interval-ms:200}")
    public void flush() { sync.flush(); }
}
