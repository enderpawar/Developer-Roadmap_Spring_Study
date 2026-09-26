package com.example.studyroom.controller;

import com.example.studyroom.domain.Member;
import com.example.studyroom.repository.MemberRepository;
import com.example.studyroom.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Day33 — 예약 시간대 중복 방지를 실제 API 경로(POST /reservations)로 확인하는 통합 테스트.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReservationOverlapHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private JwtProvider jwtProvider;

    private String userToken;

    @BeforeEach
    void setUpAuthenticatedUser() {
        Member user = memberRepository.save(new Member("사용자", "overlap-test-user", "unused"));
        userToken = jwtProvider.issue(user.getLoginId());
    }

    @Test
    void secondReservationOverlappingSameRoomReturns409() throws Exception {
        mockMvc.perform(post("/reservations")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roomName": "E-301",
                                  "requesterName": "민지",
                                  "startAt": "2026-10-20T10:00:00",
                                  "endAt": "2026-10-20T12:00:00"
                                }
                                """))
                .andExpect(status().isOk());

        // 10시30분~11시30분 — 앞의 10~12시 예약과 겹친다.
        mockMvc.perform(post("/reservations")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roomName": "E-301",
                                  "requesterName": "철수",
                                  "startAt": "2026-10-20T10:30:00",
                                  "endAt": "2026-10-20T11:30:00"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("E-301")));
    }

    @Test
    void touchingIntervalsInSameRoomAreBothAccepted() throws Exception {
        mockMvc.perform(post("/reservations")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roomName": "E-302",
                                  "requesterName": "민지",
                                  "startAt": "2026-10-20T10:00:00",
                                  "endAt": "2026-10-20T11:00:00"
                                }
                                """))
                .andExpect(status().isOk());

        // 11~12시 — 앞의 예약이 끝나는 시각과 정확히 맞닿는다(경계). 겹침이 아니므로 허용돼야 한다.
        mockMvc.perform(post("/reservations")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roomName": "E-302",
                                  "requesterName": "철수",
                                  "startAt": "2026-10-20T11:00:00",
                                  "endAt": "2026-10-20T12:00:00"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void endAtBeforeStartAtReturns400() throws Exception {
        mockMvc.perform(post("/reservations")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roomName": "E-303",
                                  "requesterName": "민지",
                                  "startAt": "2026-10-20T12:00:00",
                                  "endAt": "2026-10-20T10:00:00"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("종료 시각은 시작 시각보다 나중이어야 합니다."));
    }

    @Test
    void reservationWithoutTimeSlotStillWorksLikeBefore() throws Exception {
        // Day33 이전과 똑같이, startAt/endAt을 아예 안 보내도 여전히 성공해야 한다(하위 호환).
        mockMvc.perform(post("/reservations")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roomName": "E-304",
                                  "requesterName": "민지"
                                }
                                """))
                .andExpect(status().isOk());
    }
}
