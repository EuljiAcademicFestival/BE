package com.example.backend.realtime;

import com.example.backend.member.application.port.out.*;
import com.example.backend.member.domain.model.Member;
import com.example.backend.realtime.adapter.out.security.PresenceIdentityAdapter;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PresenceIdentityAdapterTest {
    @Test void checksProfileOnceAtJoinAndNeverQueriesDatabaseForExpiryValidation() {
        var tokens=mock(AccessTokenPort.class);var members=mock(MemberPort.class);
        var identities=new PresenceIdentityAdapter(tokens,members);
        when(tokens.authenticate("valid")).thenReturn(Optional.of(1L));
        when(members.findById(1)).thenReturn(Optional.of(new Member(1L,"test@example.com","hash",3,"이름","게스트")));
        assertThat(identities.resolve("valid")).isPresent();
        verify(members).findById(1);clearInvocations(members);
        for(int i=0;i<100;i++) assertThat(identities.valid("valid",1)).isTrue();
        verifyNoInteractions(members);
        when(tokens.authenticate("expired")).thenReturn(Optional.empty());
        assertThat(identities.resolve("expired")).isEmpty();
        assertThat(identities.valid("expired",1)).isFalse();
        verifyNoInteractions(members);
    }
}
