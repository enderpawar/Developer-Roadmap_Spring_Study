package com.example.studyroom.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

// Day22 — 아직 spring-boot-starter-security 전체(필터체인)는 없다. spring-security-crypto만으로
// BCryptPasswordEncoder를 직접 Bean으로 등록한다. 필터체인은 Day24(SecurityConfig)에서 추가한다.
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // strength 기본값 10 — 해시 앞부분이 "$2a$10$..."로 시작하는 이유.
        return new BCryptPasswordEncoder();
    }
}
