package com.example.backend.global.infrastructure.config;

import com.example.backend.global.adapter.in.security.BearerTokenFilter;
import com.example.backend.global.domain.exception.ErrorCode;
import com.example.backend.member.application.port.in.AuthenticateMemberUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticateMemberUseCase authentication,
                                                 ObjectMapper json, UrlBasedCorsConfigurationSource apiCorsConfiguration) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(apiCorsConfiguration))
                .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/prometheus").permitAll()
                        // Only the upgrade is public; the socket requires JWT AUTH before joining or moving.
                        .requestMatchers(HttpMethod.GET, "/ws/festival").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**",
                                "/v3/api-docs.yaml").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((req, res, ex) -> BearerTokenFilter.writeError(res, ErrorCode.AUTH_401_001, json))
                        .accessDeniedHandler((req, res, ex) -> BearerTokenFilter.writeError(res, ErrorCode.AUTH_401_001, json)))
                .addFilterBefore(new BearerTokenFilter(authentication, json), UsernamePasswordAuthenticationFilter.class).build();
    }

    @Bean
    public UrlBasedCorsConfigurationSource apiCorsConfiguration(
            @Value("${security.cors.allowed-origins}") String[] allowedOrigins) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
