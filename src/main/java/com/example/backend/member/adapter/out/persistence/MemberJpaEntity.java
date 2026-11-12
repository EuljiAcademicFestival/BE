package com.example.backend.member.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MemberJpaEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 254)
    private String email;
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
    @Column(name = "character_id", nullable = false, columnDefinition = "TINYINT UNSIGNED")
    private Byte characterId;
    @Column(nullable = false, length = 255)
    private String name;
    @Column(nullable = false, length = 16)
    private String grade;
}
