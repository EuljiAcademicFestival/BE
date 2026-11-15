package com.example.backend.member.adapter.out.persistence;

import com.example.backend.global.domain.exception.*;
import com.example.backend.member.application.port.out.MemberPort;
import com.example.backend.member.domain.model.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MemberPersistenceAdapter implements MemberPort {
    private final SpringDataMemberRepository repository;
    private final MemberPersistenceMapper mapper;
    public boolean existsByEmail(String email) { return repository.existsByEmail(email); }
    public Optional<Member> findByEmail(String email) { return repository.findByEmail(email).map(mapper::toDomain); }
    public Optional<Member> findById(long id) { return repository.findById(id).map(mapper::toDomain); }
    public Member save(Member member) {
        try {
            return mapper.toDomain(repository.saveAndFlush(mapper.toEntity(member)));
        } catch (DataIntegrityViolationException ex) {
            String message = ex.getMostSpecificCause().getMessage();
            if (message != null && message.contains("uq_members_email")) {
                throw new BusinessException(ErrorCode.AUTH_DUPLICATE_MEMBER);
            }
            throw ex;
        }
    }
}
