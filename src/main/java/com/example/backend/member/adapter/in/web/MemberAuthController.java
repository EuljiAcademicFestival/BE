package com.example.backend.member.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.backend.global.adapter.in.web.ApiResponse;
import com.example.backend.member.adapter.in.web.request.*;
import com.example.backend.member.application.port.in.MemberCommandUseCase;
import com.example.backend.member.application.result.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "인증", description = "회원가입과 로그인")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class MemberAuthController {
    private final MemberCommandUseCase members;
    @Operation(summary = "회원가입", description = "회원과 방명록을 함께 저장합니다. 입력 오류 또는 저장 실패 시 모두 롤백합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "회원가입 성공")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResult>> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(201).body(new ApiResponse<>(201, "AUTH_SIGNUP_201", "회원가입에 성공했습니다.",
                members.signup(request.toCommand())));
    }
    @Operation(summary = "로그인", description = "성공 응답의 accessToken을 오른쪽 위 Authorize에 입력하세요.")
    @PostMapping("/login")
    public ApiResponse<LoginResult> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok("AUTH_LOGIN_200", "로그인에 성공했습니다.", members.login(request.toCommand()));
    }
}
