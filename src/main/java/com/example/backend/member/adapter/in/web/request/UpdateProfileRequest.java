package com.example.backend.member.adapter.in.web.request;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import com.example.backend.member.application.command.UpdateProfileCommand;

public record UpdateProfileRequest(@NotNull @Schema(example = "3", minimum = "1", maximum = "6") Integer characterId, @NotBlank @Schema(example = "김을지") String name,
                                   @NotBlank @Schema(example = "게스트", allowableValues = {"1학년", "2학년", "3학년", "4학년", "졸업생", "게스트"}) String grade, @NotBlank @Schema(example = "학술제 개최를 축하합니다!", description = "앞뒤 공백 제거 후 화면에 보이는 글자 기준 1~100자") String guestbookMessage) {
    public UpdateProfileCommand toCommand(long memberId) {
        return new UpdateProfileCommand(memberId, characterId, name, grade, guestbookMessage);
    }
}
