package com.example.studyroom.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Spring 컨테이너가 필요 없다 — BCryptPasswordEncoder는 그냥 자바 객체라서 순수 단위 테스트로 충분.
class PasswordEncoderTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    void sameRawPasswordProducesDifferentHashesEachTime() {
        String rawPassword = "password1234";

        String firstHash = passwordEncoder.encode(rawPassword);
        String secondHash = passwordEncoder.encode(rawPassword);

        // 매번 새 salt를 섞기 때문에 같은 원문이어도 해시 결과가 다르다.
        assertNotEquals(firstHash, secondHash);
    }

    @Test
    void encodedHashStartsWithBCryptStrength10Prefix() {
        String hash = passwordEncoder.encode("password1234");

        // $2a$10$ — 버전($2a) + strength(10, 기본값) + salt가 이어지는 BCrypt 포맷.
        assertTrue(hash.startsWith("$2a$10$"));
    }

    @Test
    void matchesReturnsTrueForCorrectPasswordAndFalseForWrongPassword() {
        String hash = passwordEncoder.encode("password1234");

        assertTrue(passwordEncoder.matches("password1234", hash));
        assertFalse(passwordEncoder.matches("wrongPassword", hash));
    }
}
