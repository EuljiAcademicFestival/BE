package com.example.backend.guestbook.domain;

import com.example.backend.global.domain.exception.*;
import com.example.backend.guestbook.domain.policy.GuestbookPolicy;
import com.example.backend.member.domain.model.Profile;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GuestbookPolicyTest {
    @Test
    void countsGraphemesInsteadOfUtf16OrCodePoints() {
        for (String grapheme : new String[]{"👨‍👩‍👧‍👦", "🇰🇷", "👍🏽", "é", "한"}) {
            String message = grapheme.repeat(100);
            assertThat(GuestbookPolicy.validateMessage("  " + message + "  ", ErrorCode.AUTH_INVALID_INPUT)).isEqualTo(message);
            assertThatThrownBy(() -> GuestbookPolicy.validateMessage(message + grapheme, ErrorCode.AUTH_INVALID_INPUT))
                    .isInstanceOf(BusinessException.class);
        }
    }
    @Test
    void rejectsBlankAndMissingMessages() {
        for (String value : new String[]{null, "", "  ", "\n\t"}) {
            assertThatThrownBy(() -> GuestbookPolicy.validateMessage(value, ErrorCode.AUTH_INVALID_INPUT))
                    .isInstanceOf(BusinessException.class);
        }
    }
    @Test
    void validatesProfileWithoutFrameworkDependencies() {
        assertThat(Profile.validated(6, "  이름  ", "게스트", ErrorCode.AUTH_INVALID_INPUT).name()).isEqualTo("이름");
        assertThatThrownBy(() -> Profile.validated(7, "이름", "게스트", ErrorCode.AUTH_INVALID_INPUT)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> Profile.validated(1, "이름", "5학년", ErrorCode.AUTH_INVALID_INPUT)).isInstanceOf(BusinessException.class);
    }
}
