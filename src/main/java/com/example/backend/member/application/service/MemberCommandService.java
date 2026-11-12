package com.example.backend.member.application.service;

import com.example.backend.global.domain.exception.*;
import com.example.backend.guestbook.application.port.out.GuestbookPort;
import com.example.backend.guestbook.domain.model.GuestbookEntry;
import com.example.backend.guestbook.domain.policy.GuestbookPolicy;
import com.example.backend.member.application.command.*;
import com.example.backend.member.application.port.in.MemberCommandUseCase;
import com.example.backend.member.application.port.out.*;
import com.example.backend.member.application.result.*;
import com.example.backend.member.domain.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

@Service
@Transactional
@RequiredArgsConstructor
public class MemberCommandService implements MemberCommandUseCase {
    private final MemberPort members;
    private final GuestbookPort guestbook;
    private final PasswordHashPort passwords;
    private final AccessTokenPort tokens;

    @Override
    public SignupResult signup(SignupCommand command) {
        Profile profile = Profile.validated(command.characterId(), command.name(), command.grade(), ErrorCode.AUTH_INVALID_INPUT);
        String message = GuestbookPolicy.validateMessage(command.guestbookMessage(), ErrorCode.AUTH_INVALID_INPUT);
        String email = normalizeEmail(command.email());
        validatePassword(command.password());
        if (members.existsByEmail(email)) throw new BusinessException(ErrorCode.AUTH_DUPLICATE_MEMBER);
        Member member = members.save(new Member(null, email, passwords.encode(command.password()),
                profile.characterId(), profile.name(), profile.grade()));
        guestbook.create(member.id(), message);
        return new SignupResult(member.id());
    }

    @Override
    public LoginResult login(LoginCommand command) {
        String email = normalizeEmail(command.email());
        validatePassword(command.password());
        Member member = members.findByEmail(email).orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));
        if (!passwords.matches(command.password(), member.passwordHash())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        return new LoginResult(member.id(), tokens.issue(member.id()));
    }

    @Override
    public ProfileResult updateProfile(UpdateProfileCommand command) {
        Profile profile = Profile.validated(command.characterId(), command.name(), command.grade(), ErrorCode.MEMBER_PROFILE_INVALID_INPUT);
        String message = GuestbookPolicy.validateMessage(command.guestbookMessage(), ErrorCode.MEMBER_PROFILE_INVALID_INPUT);
        // All guestbook writers acquire the same row lock, preserving likes and the original timestamp.
        GuestbookEntry entry = guestbook.lockByMemberId(command.memberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_401_001));
        Member member = members.findById(command.memberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_401_001));
        members.save(member.withProfile(profile));
        guestbook.updateMessage(entry.withMessage(message));
        return new ProfileResult(member.id(), profile.characterId(), profile.name(), profile.grade(), message, entry.likeCount());
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank() || email.length() > 254) throw new BusinessException(ErrorCode.AUTH_INVALID_INPUT);
        return email.strip().toLowerCase(Locale.ROOT);
    }
    private void validatePassword(String password) {
        if (password == null || password.isBlank()) throw new BusinessException(ErrorCode.AUTH_INVALID_INPUT);
    }
}
