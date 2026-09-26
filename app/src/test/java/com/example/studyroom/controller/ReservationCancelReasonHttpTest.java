package com.example.studyroom.controller;

import com.example.studyroom.domain.Member;
import com.example.studyroom.domain.Reservation;
import com.example.studyroom.repository.MemberRepository;
import com.example.studyroom.repository.ReservationRepository;
import com.example.studyroom.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Day30 디버깅 실습 — 취소 사유 앞뒤 공백을 트리밍하는 기능을 추가하면서 실제로 겪은 버그를
// 그대로 재현하는 테스트. 실제 API 경로(POST /reservations/cancel/{id})를 그대로 타므로
// RequestIdFilter(Day29)가 남기는 요청 로그와 ReservationService의 디버그 로그가 같은
// requestId로 묶여서 찍힌다 — day30.md에 그 원문을 남겼다.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReservationCancelReasonHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JwtProvider jwtProvider;

    private String adminToken;

    @BeforeEach
    void setUpAdmin() {
        Member admin = memberRepository.save(new Member("관리자", "cancel-reason-admin", "unused"));
        admin.grantAdmin();
        memberRepository.save(admin);
        adminToken = jwtProvider.issue(admin.getLoginId());
    }

    @Test
    void cancelReasonKeepsFirstCharacterAfterTrimming() throws Exception {
        Reservation reservation = reservationRepository.save(new Reservation("D-101", "현우"));
        reservation.confirm();
        reservationRepository.save(reservation);

        mockMvc.perform(post("/reservations/cancel/{id}", reservation.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .param("cancelReason", "  일정 변경  "))
                .andExpect(status().isOk());

        Reservation updated = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertEquals("일정 변경", updated.getCancelReason());
    }
}
