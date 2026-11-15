package com.example.backend.realtime.adapter.out.notification;

import com.example.backend.member.application.port.out.MemberProfileChangedPort;
import com.example.backend.member.domain.model.Profile;
import com.example.backend.realtime.application.port.in.FestivalPresenceUseCase;
import com.example.backend.realtime.domain.PlayerIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;

@Component
@RequiredArgsConstructor
public class PresenceProfileAdapter implements MemberProfileChangedPort {
    private final FestivalPresenceUseCase presence;
    public void afterCommit(long memberId, Profile profile) {
        var identity = new PlayerIdentity(memberId, profile.characterId(), profile.name(), profile.grade());
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            presence.updateProfile(identity); return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { presence.updateProfile(identity); }
        });
    }
}
