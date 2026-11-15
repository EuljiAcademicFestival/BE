package com.example.backend.realtime.application.result;

import com.example.backend.guestbook.application.result.EngagementSnapshot;
import java.util.List;

public record EngagementState(long revision, long serverTime, boolean full,
                             List<EngagementSnapshot.Count> counts, List<EngagementSnapshot.Ranking> rankings) {}
