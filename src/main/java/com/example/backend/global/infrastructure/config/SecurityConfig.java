package com.example.backend.global.infrastructure.config;

import com.example.backend.global.adapter.in.security.BearerTokenFilter;
import com.example.backend.global.domain.exception.ErrorCode;
import com.example.backend.member.application.port.in.AuthenticateMemberUseCase;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticateMemberUseCase authentication,
                                                 ObjectMapper json) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**",
                                "/v3/api-docs.yaml").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((req, res, ex) -> BearerTokenFilter.writeError(res, ErrorCode.AUTH_401_001, json))
                        .accessDeniedHandler((req, res, ex) -> BearerTokenFilter.writeError(res, ErrorCode.AUTH_401_001, json)))
                .addFilterBefore(new BearerTokenFilter(authentication, json), UsernamePasswordAuthenticationFilter.class).build();
    }
}
