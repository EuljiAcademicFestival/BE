package com.example.backend.guestbook.domain.policy;

import com.example.backend.global.domain.exception.BusinessException;
import com.example.backend.global.domain.exception.ErrorCode;
import java.util.List;

public final class CharacterLabelPolicy {
    private CharacterLabelPolicy() {}
    public static List<Long> normalizeIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(ErrorCode.CHARACTER_INVALID_INPUT);
        }
        return ids.stream().distinct().sorted().toList();
    }
}
