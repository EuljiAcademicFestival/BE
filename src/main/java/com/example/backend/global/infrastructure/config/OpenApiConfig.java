package com.example.backend.global.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI festivalOpenApi() {
        return new OpenAPI()
                .info(new Info().title("을지 학술제 API").version("v1")
                        .description("회원가입 → 로그인 → 응답의 accessToken을 Authorize에 입력한 뒤 API를 테스트하세요. "
                                + "Authorize에는 Bearer 접두사 없이 토큰 값만 입력합니다."))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer")
                        .description("로그인 응답의 accessToken 값만 입력하세요. 기본 만료 시간은 1시간입니다.")));
    }
}
