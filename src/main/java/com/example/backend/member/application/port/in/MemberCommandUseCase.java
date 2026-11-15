package com.example.backend.member.application.port.in;

import com.example.backend.member.application.command.*;
import com.example.backend.member.application.result.*;

public interface MemberCommandUseCase {
    SignupResult signup(SignupCommand command);
    LoginResult login(LoginCommand command);
    ProfileResult updateProfile(UpdateProfileCommand command);
}
