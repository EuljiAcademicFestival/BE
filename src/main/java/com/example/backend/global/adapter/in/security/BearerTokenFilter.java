package com.example.backend.global.adapter.in.security;

import com.example.backend.global.adapter.in.web.ApiResponse;
import com.example.backend.global.domain.exception.ErrorCode;
import com.example.backend.member.application.port.in.AuthenticateMemberUseCase;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;

public class BearerTokenFilter extends OncePerRequestFilter {
    private final AuthenticateMemberUseCase authentication;
    private final ObjectMapper json;
    public BearerTokenFilter(AuthenticateMemberUseCase authentication, ObjectMapper json) {
        this.authentication = authentication;
        this.json = json;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && (request.getServletPath().equals("/api/auth/login")
                || request.getServletPath().equals("/api/auth/signup"));
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            if (!header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                writeError(response, ErrorCode.AUTH_401_001, json); return;
            }
            var memberId = authentication.authenticate(header.substring(7));
            if (memberId.isEmpty()) { writeError(response, ErrorCode.AUTH_401_001, json); return; }
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(new AuthMember(memberId.get()),
                    null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }
    public static void writeError(HttpServletResponse response, ErrorCode error, ObjectMapper json) throws IOException {
        response.setStatus(error.status());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), ApiResponse.error(error));
    }
}
