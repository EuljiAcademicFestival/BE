package com.example.backend.guestbook.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Parameter;
import com.example.backend.global.adapter.in.security.AuthMember;
import com.example.backend.global.adapter.in.web.ApiResponse;
import com.example.backend.global.domain.exception.BusinessException;
import com.example.backend.global.domain.exception.ErrorCode;
import com.example.backend.guestbook.application.port.in.*;
import com.example.backend.guestbook.application.result.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "방명록", description = "방명록 목록과 좋아요")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/guestbook")
@RequiredArgsConstructor
public class GuestbookController {
    private final GuestbookQueryUseCase queries;
    private final GuestbookCommandUseCase commands;
    @Operation(summary = "방명록 목록 조회", description = "인기순 또는 최신순 커서 조회입니다. 정렬을 변경하면 cursor를 비워 주세요.")
    @GetMapping
    public ApiResponse<GuestbookPage> list(@Parameter(hidden = true) @AuthenticationPrincipal AuthMember member,
            @Parameter(description = "정렬 방식", schema = @Schema(allowableValues = {"popular", "latest"}, defaultValue = "popular"))
            @RequestParam(required = false) String sort, @Parameter(description = "이전 응답의 nextCursor. 첫 페이지는 비워둡니다.") @RequestParam(required = false) String cursor,
            @Parameter(description = "페이지 크기", schema = @Schema(type = "integer", minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(required = false) String limit) {
        int pageSize;
        try {
            pageSize = limit == null ? 20 : Integer.parseInt(limit);
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.GUESTBOOK_INVALID_QUERY);
        }
        return ApiResponse.ok("GUESTBOOK_LIST_200", "방명록 목록을 조회했습니다.",
                queries.page(member.memberId(), sort == null ? "popular" : sort, cursor, pageSize));
    }
    @Operation(summary = "좋아요 등록", description = "동일한 요청을 반복해도 좋아요는 한 번만 등록됩니다.")
    @PutMapping("/{memberId}/like")
    public ApiResponse<LikeResult> like(@Parameter(hidden = true) @AuthenticationPrincipal AuthMember member, @PathVariable long memberId) {
        return ApiResponse.ok("GUESTBOOK_LIKE_SET_200", "좋아요를 등록했습니다.",
                commands.setLike(memberId, member.memberId(), true));
    }
    @Operation(summary = "좋아요 취소", description = "이미 취소한 좋아요에 다시 요청해도 개수는 줄어들지 않습니다.")
    @DeleteMapping("/{memberId}/like")
    public ApiResponse<LikeResult> unlike(@Parameter(hidden = true) @AuthenticationPrincipal AuthMember member, @PathVariable long memberId) {
        return ApiResponse.ok("GUESTBOOK_LIKE_REMOVED_200", "좋아요를 취소했습니다.",
                commands.setLike(memberId, member.memberId(), false));
    }
}
