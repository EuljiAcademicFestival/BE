package com.example.backend.member.adapter.out.persistence;

import com.example.backend.member.domain.model.Member;
import org.springframework.stereotype.Component;

@Component
public class MemberPersistenceMapper {
    public Member toDomain(MemberJpaEntity entity) {
        return new Member(entity.getId(), entity.getEmail(), entity.getPasswordHash(),
                entity.getCharacterId(), entity.getName(), entity.getGrade());
    }
    public MemberJpaEntity toEntity(Member member) {
        return new MemberJpaEntity(member.id(), member.email(), member.passwordHash(),
                (byte) member.characterId(), member.name(), member.grade());
    }
}
