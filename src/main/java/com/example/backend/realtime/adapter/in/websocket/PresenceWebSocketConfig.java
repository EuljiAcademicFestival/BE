package com.example.backend.realtime.adapter.in.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
@EnableScheduling
@RequiredArgsConstructor
public class PresenceWebSocketConfig implements WebSocketConfigurer {
    private final FestivalWebSocketHandler handler;
    @Value("${realtime.allowed-origins:http://localhost:3000,http://localhost:5173,http://127.0.0.1:3000,http://127.0.0.1:5173}")
    private String[] allowedOrigins;
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/festival").setAllowedOrigins(allowedOrigins);
    }
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("festival-realtime-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
