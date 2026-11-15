package com.example.backend.member.domain.model;

import com.example.backend.global.domain.exception.BusinessException;
import com.example.backend.global.domain.exception.ErrorCode;
import java.util.Set;

public record Profile(int characterId, String name, String grade) {
    private static final Set<String> GRADES = Set.of("1학년", "2학년", "3학년", "4학년", "졸업생", "게스트");

    public static Profile validated(Integer characterId, String name, String grade, ErrorCode error) {
        if (characterId == null || characterId < 1 || characterId > 6 || name == null || name.isBlank()
                || name.strip().codePointCount(0, name.strip().length()) > 255
                || grade == null || !GRADES.contains(grade)) {
            throw new BusinessException(error);
        }
        return new Profile(characterId, name.strip(), grade);
    }
}
