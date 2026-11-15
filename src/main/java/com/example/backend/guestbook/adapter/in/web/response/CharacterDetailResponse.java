package com.example.backend.guestbook.adapter.in.web.response;

import com.example.backend.guestbook.application.result.EntryView;
import io.swagger.v3.oas.annotations.media.Schema;

public record CharacterDetailResponse(long memberId, String grade, String name, String guestbookMessage,
                                      long likeCount, Integer rank,
                                      @Schema(description = "로그인한 회원의 좋아요 선택 상태", example = "true") boolean likedByMe) {
    public static CharacterDetailResponse from(EntryView entry) {
        return new CharacterDetailResponse(entry.memberId(), entry.grade(), entry.name(), entry.guestbookMessage(),
                entry.likeCount(), entry.rank(), entry.likedByMe());
    }
}
