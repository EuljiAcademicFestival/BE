package com.example.backend.guestbook.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.example.backend.global.adapter.in.web.ApiResponse;
import com.example.backend.guestbook.adapter.in.web.response.CharacterDetailResponse;
import com.example.backend.guestbook.application.port.in.GuestbookQueryUseCase;
import com.example.backend.guestbook.application.result.CharacterLabel;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.util.*;

@Tag(name = "캐릭터", description = "캐릭터 이름표와 근접 상세")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/characters")
@RequiredArgsConstructor
public class CharacterController {
    private final GuestbookQueryUseCase queries;
    public record LabelsResponse(List<CharacterLabel> characters) {}
    @Operation(summary = "화면 내 캐릭터 이름표 조회")
    @GetMapping
    public ApiResponse<LabelsResponse> labels(@Parameter(description = "쉼표로 구분한 회원 ID 목록", example = "1,2", required = true) @RequestParam String memberIds) {
        try {
            List<Long> ids = Arrays.stream(memberIds.split(",", -1)).map(String::strip).map(Long::parseLong).toList();
            if (ids.isEmpty() || ids.stream().anyMatch(id -> id <= 0)) throw new NumberFormatException();
            return ApiResponse.ok("CHARACTER_LABELS_200", "화면 내 캐릭터 이름표 조회에 성공했습니다.",
                    new LabelsResponse(queries.labels(ids)));
        } catch (NumberFormatException ex) {
            throw new MethodArgumentTypeMismatchException(memberIds, List.class, "memberIds", null, ex);
        }
    }
    @Operation(summary = "근접 캐릭터 상세 조회", description = "전역 좋아요 순위가 1~3위인 경우 순위를 반환하며, 그 외는 null입니다.")
    @GetMapping("/{memberId}")
    public ApiResponse<CharacterDetailResponse> detail(@PathVariable long memberId) {
        return ApiResponse.ok("CHARACTER_DETAIL_200", "근접 캐릭터 상세 조회에 성공했습니다.",
                CharacterDetailResponse.from(queries.detail(memberId)));
    }
}
