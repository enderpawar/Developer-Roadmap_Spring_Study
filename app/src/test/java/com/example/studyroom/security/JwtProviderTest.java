package com.example.studyroom.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtProviderTest {

    // HS256 최소 256비트(32바이트) 요구사항을 만족하는 테스트용 secret.
    private final JwtProvider jwtProvider =
            new JwtProvider("studyroom-secret-key-must-be-at-least-32-bytes-long", 3600000L);

    @Test
    void issueReturnsNonBlankToken() {
        String token = jwtProvider.issue("jinwoo01");

        assertNotNull(token);
        assertTrue(token.contains(".")); // header.payload.signature 세 부분이 점으로 구분됨
    }

    @Test
    void parseSubjectReturnsSameSubjectUsedAtIssue() {
        String token = jwtProvider.issue("jinwoo01");

        String subject = jwtProvider.parseSubject(token);

        assertEquals("jinwoo01", subject);
    }
}
