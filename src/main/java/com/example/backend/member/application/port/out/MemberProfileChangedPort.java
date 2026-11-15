package com.example.backend.member.application.port.out;

import com.example.backend.member.domain.model.Profile;

public interface MemberProfileChangedPort {
    void afterCommit(long memberId, Profile profile);
}
