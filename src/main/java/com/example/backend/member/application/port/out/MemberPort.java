package com.example.backend.member.application.port.out;

import com.example.backend.member.domain.model.Member;
import java.util.Optional;

public interface MemberPort {
    boolean existsByEmail(String email);
    Optional<Member> findByEmail(String email);
    Optional<Member> findById(long id);
    Member save(Member member);
}
