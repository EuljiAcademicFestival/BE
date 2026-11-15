package com.example.backend.realtime.application.result;

import com.example.backend.realtime.domain.*;
import java.util.List;

public record PresenceEvent(String type, Object data) {
    public record Player(PlayerIdentity identity, long sequence, PlayerPose pose) {}
    public record Position(long memberId, long sequence, PlayerPose pose) {}
    public record Welcome(long memberId, String roomId, long serverTime, List<Player> players) {}
    public record State(long revision, long serverTime, List<Position> players) {}
    public record Left(long memberId) {}
    public record Failure(String code) {}
}
