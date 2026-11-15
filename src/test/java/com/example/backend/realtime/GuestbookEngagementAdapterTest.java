package com.example.backend.realtime;

import com.example.backend.realtime.adapter.out.notification.GuestbookEngagementAdapter;
import com.example.backend.realtime.application.port.in.EngagementSyncUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.*;
import static org.mockito.Mockito.*;

class GuestbookEngagementAdapterTest {
    @Test void changesAreMarkedAfterCommitAndNeverOnRollback() {
        var sync=mock(EngagementSyncUseCase.class);var adapter=new GuestbookEngagementAdapter(sync);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            adapter.afterCommit(1);verifyNoInteractions(sync);
            for(var callback:TransactionSynchronizationManager.getSynchronizations())
                callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            verifyNoInteractions(sync);
            TransactionSynchronizationManager.clearSynchronization();TransactionSynchronizationManager.initSynchronization();
            adapter.afterCommit(2);
            for(var callback:TransactionSynchronizationManager.getSynchronizations()) callback.afterCommit();
            verify(sync).changed(2);verifyNoMoreInteractions(sync);
        } finally { TransactionSynchronizationManager.clearSynchronization();TransactionSynchronizationManager.setActualTransactionActive(false); }
    }
}
