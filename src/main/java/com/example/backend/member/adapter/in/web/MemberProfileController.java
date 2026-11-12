package com.example.backend.member.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Parameter;
import com.example.backend.global.adapter.in.security.AuthMember;
import com.example.backend.global.adapter.in.web.ApiResponse;
import com.example.backend.member.adapter.in.web.request.UpdateProfileRequest;
import com.example.backend.member.application.port.in.MemberCommandUseCase;
import com.example.backend.member.application.result.ProfileResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "회원", description = "내 프로필 관리")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
public class MemberProfileController {
    private final MemberCommandUseCase members;
    @Operation(summary = "내 정보 수정", description = "캐릭터·이름·학년·방명록을 함께 수정합니다. 기존 좋아요와 최초 작성 시각은 유지합니다.")
    @PutMapping("/api/members/me/profile")
    public ApiResponse<ProfileResult> update(@Parameter(hidden = true) @AuthenticationPrincipal AuthMember member,
                                              @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.ok("MEMBER_PROFILE_UPDATE_200", "내 정보를 수정했습니다.",
                members.updateProfile(request.toCommand(member.memberId())));
    }
}
