package com.example.backend.member.domain.model;

public record Member(Long id, String email, String passwordHash, int characterId, String name, String grade) {
    public Member withProfile(Profile profile) {
        return new Member(id, email, passwordHash, profile.characterId(), profile.name(), profile.grade());
    }
}
