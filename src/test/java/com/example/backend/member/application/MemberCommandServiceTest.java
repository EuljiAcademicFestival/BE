package com.example.backend.member.application;

import com.example.backend.global.domain.exception.*;
import com.example.backend.guestbook.application.port.out.GuestbookPort;
import com.example.backend.member.application.command.*;
import com.example.backend.member.application.port.out.*;
import com.example.backend.member.application.service.MemberCommandService;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemberCommandServiceTest {
    @Test
    void rejectsInvalidMessageBeforeSavingAnyMember() {
        var members = mock(MemberPort.class);
        var guestbook = mock(GuestbookPort.class);
        var service = new MemberCommandService(members, guestbook, mock(PasswordHashPort.class), mock(AccessTokenPort.class));
        assertThatThrownBy(() -> service.signup(new SignupCommand("a@example.com", "pw", 1, "이름", "게스트", "x".repeat(101))))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(members, guestbook);
    }
    @Test
    void duplicateEmailDoesNotHashOrSave() {
        var members = mock(MemberPort.class);
        var guestbook = mock(GuestbookPort.class);
        var passwords = mock(PasswordHashPort.class);
        when(members.existsByEmail("a@example.com")).thenReturn(true);
        var service = new MemberCommandService(members, guestbook, passwords, mock(AccessTokenPort.class));
        assertThatThrownBy(() -> service.signup(new SignupCommand("A@example.com", "pw", 1, "이름", "게스트", "환영")))
                .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.error()).isEqualTo(ErrorCode.AUTH_DUPLICATE_MEMBER));
        verifyNoInteractions(passwords, guestbook);
        verify(members, never()).save(any());
    }
}
