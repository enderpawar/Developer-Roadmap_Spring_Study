package com.example.studyroom.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

// Day23 — 토큰 발급(issue)·검증(parse) 담당. HS256(대칭키) 사용 — 서버 혼자만 알고 있는 secret으로
// 서명하고, 같은 secret으로만 검증할 수 있다(비대칭키 RS256과 다른 점).
@Component
public class JwtProvider {

    private final SecretKey key;
    private final long expirationMillis;

    public JwtProvider(@Value("${jwt.secret}") String secret,
                        @Value("${jwt.expiration}") long expirationMillis) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
    }

    // subject에는 loginId를 담는다 — "이 토큰은 누구 것인가"를 나타내는 표준 클레임.
    public String issue(String subject) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
                .subject(subject)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    // 서명이 위조됐거나(SignatureException) 만료됐으면(ExpiredJwtException) 여기서 예외가 던져진다.
    // 둘 다 JwtException의 하위타입이라 호출부는 JwtException 하나만 잡으면 된다.
    public String parseSubject(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}
