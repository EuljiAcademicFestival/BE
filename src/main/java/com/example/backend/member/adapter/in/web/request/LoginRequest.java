package com.example.backend.member.adapter.in.web.request;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import com.example.backend.member.application.command.LoginCommand;

public record LoginRequest(@NotBlank @Email @Size(max = 254) @Schema(example = "test01@test.com") String email, @NotBlank @Schema(example = "test01!", format = "password") String password) {
    public LoginCommand toCommand() { return new LoginCommand(email, password); }
}
