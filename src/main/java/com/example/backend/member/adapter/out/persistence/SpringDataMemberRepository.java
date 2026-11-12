package com.example.backend.member.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SpringDataMemberRepository extends JpaRepository<MemberJpaEntity, Long> {
    boolean existsByEmail(String email);
    Optional<MemberJpaEntity> findByEmail(String email);
}
