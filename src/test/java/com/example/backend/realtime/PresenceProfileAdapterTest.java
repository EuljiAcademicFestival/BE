package com.example.backend.realtime;

import com.example.backend.realtime.adapter.out.notification.PresenceProfileAdapter;
import com.example.backend.realtime.application.port.in.FestivalPresenceUseCase;
import com.example.backend.realtime.domain.PlayerIdentity;
import com.example.backend.member.domain.model.Profile;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.*;
import static org.mockito.Mockito.*;

class PresenceProfileAdapterTest {
    @Test void profilePublishesOnlyAfterCommitAndNotAfterRollback() {
        var presence=mock(FestivalPresenceUseCase.class);
        var adapter=new PresenceProfileAdapter(presence);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            adapter.afterCommit(1,new Profile(2,"새이름","게스트"));
            verifyNoInteractions(presence);
            for(var sync:TransactionSynchronizationManager.getSynchronizations())
                sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            verifyNoInteractions(presence);
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.initSynchronization();
            adapter.afterCommit(1,new Profile(2,"새이름","게스트"));
            for(var sync:TransactionSynchronizationManager.getSynchronizations()) sync.afterCommit();
            verify(presence).updateProfile(new PlayerIdentity(1,2,"새이름","게스트"));
        } finally { TransactionSynchronizationManager.clearSynchronization();TransactionSynchronizationManager.setActualTransactionActive(false); }
    }
}
