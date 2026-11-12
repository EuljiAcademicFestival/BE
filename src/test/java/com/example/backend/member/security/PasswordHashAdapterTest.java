package com.example.backend.member.security;

import com.example.backend.member.adapter.out.security.PasswordHashAdapter;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PasswordHashAdapterTest {
    @Test
    void saltsHashesAndSupportsPasswordsLongerThanBcryptLimit() {
        var adapter = new PasswordHashAdapter();
        String password = "학술제비밀번호!".repeat(20);
        String first = adapter.encode(password);
        assertThat(first).isNotEqualTo(adapter.encode(password)).doesNotContain(password);
        assertThat(adapter.matches(password, first)).isTrue();
        assertThat(adapter.matches(password + "x", first)).isFalse();
    }
}
