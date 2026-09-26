package com.example.studyroom.security;

import com.example.studyroom.domain.Member;
import com.example.studyroom.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Day25 — 인증 실패 케이스를 하나씩 모아둔다. 전부 401(누구인지조차 확인 안 됨)이어야 한다.
// 403(valid user without role)은 이미 Day24 ReservationControllerHttpTest.cancelReturns403WhenCallerIsNotAdmin에서
// 다뤘다 — "인증됨 + 권한 부족"이라 이 클래스의 "인증 자체 실패" 케이스들과는 성격이 달라서 분리해뒀다.
@SpringBootTest(properties = "jwt.expiration=1") // 1ms — 발급 직후 곧바로 만료시키기 위한 짧은 만료시간
@AutoConfigureMockMvc
@Transactional
class JwtAuthenticationFailureTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    void missingAuthorizationHeaderReturns401() throws Exception {
        mockMvc.perform(get("/reservations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("인증이 필요합니다."));
    }

    @Test
    void malformedBearerPrefixReturns401() throws Exception {
        Member member = memberRepository.save(new Member("진우", "jwt-fail-malformed", "unused"));
        String token = jwtProvider.issue(member.getLoginId());

        // "Bearer " 없이 토큰만 보냄 — 필터가 접두사를 못 찾아 인증 정보를 아예 채우지 않는다.
        mockMvc.perform(get("/reservations").header("Authorization", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("인증이 필요합니다."));
    }

    @Test
    void tamperedSignatureReturns401() throws Exception {
        Member member = memberRepository.save(new Member("철수", "jwt-fail-tampered", "unused"));
        String token = jwtProvider.issue(member.getLoginId());

        // 서명(마지막 세그먼트)을 한 글자 건드려서 위조한다 — parseSubject()가 SignatureException을 던져야 한다.
        String tamperedToken = token.substring(0, token.length() - 1)
                + (token.charAt(token.length() - 1) == 'A' ? 'B' : 'A');

        mockMvc.perform(get("/reservations").header("Authorization", "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("인증이 필요합니다."));
    }

    @Test
    void expiredTokenReturns401() throws Exception {
        Member member = memberRepository.save(new Member("영희", "jwt-fail-expired", "unused"));
        String token = jwtProvider.issue(member.getLoginId()); // 만료 1ms — 곧바로 지난다.

        Thread.sleep(20); // 만료 시각을 확실히 넘기기 위한 최소 대기

        mockMvc.perform(get("/reservations").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("인증이 필요합니다."));
    }
}
