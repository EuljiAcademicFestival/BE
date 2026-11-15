package com.example.backend.realtime.adapter.in.websocket;

import com.example.backend.realtime.domain.PlayerPose;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Wire precision only: round to three decimal places without padding zeros or changing stored poses. */
final class PlayerPoseJsonSerializer extends StdSerializer<PlayerPose> {
    PlayerPoseJsonSerializer() { super(PlayerPose.class); }

    @Override public void serialize(PlayerPose pose, JsonGenerator json, SerializerProvider provider) throws IOException {
        json.writeStartObject();
        number(json, "x", pose.x());
        number(json, "y", pose.y());
        number(json, "z", pose.z());
        number(json, "yaw", pose.yaw());
        json.writeEndObject();
    }

    private static void number(JsonGenerator json, String name, double value) throws IOException {
        json.writeFieldName(name);
        var rounded = BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros();
        json.writeNumber(rounded.toPlainString());
    }
}
