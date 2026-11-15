package com.example.backend.realtime.domain;

public record PlayerPose(double x, double y, double z, double yaw) {
    public static PlayerPose validated(double x, double y, double z, double yaw, double limit) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(yaw)
                || Math.abs(x) > limit || Math.abs(y) > limit || Math.abs(z) > limit) {
            throw new PresenceException("INVALID_POSITION");
        }
        return new PlayerPose(x, y, z, Math.atan2(Math.sin(yaw), Math.cos(yaw)));
    }
}
