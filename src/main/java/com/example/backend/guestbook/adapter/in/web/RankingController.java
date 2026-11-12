package com.example.backend.guestbook.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.example.backend.global.adapter.in.web.ApiResponse;
import com.example.backend.guestbook.adapter.in.web.response.CharacterDetailResponse;
import com.example.backend.guestbook.application.port.in.GuestbookQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Tag(name = "순위", description = "공동 순위를 포함한 상위 순위")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
public class RankingController {
    private final GuestbookQueryUseCase queries;
    public record RankingsResponse(List<CharacterDetailResponse> rankings) {}
    @Operation(summary = "중앙의 랭커 1~3위 조회", description = "공동 순위를 포함한 1~3위 전원을 반환합니다. 동점 인원수만큼 다음 순위를 건너뜁니다.")
    @GetMapping("/api/rankings/top")
    public ApiResponse<RankingsResponse> top() {
        return ApiResponse.ok("RANKING_TOP_200", "좋아요 1~3위 조회에 성공했습니다.",
                new RankingsResponse(queries.topRankings().stream().map(CharacterDetailResponse::from).toList()));
    }
}
