package com.example.studyroom.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional // 이 클래스만 없던 롤백 경계 — HTTP로 만든 Reservation이 커밋된 채 공유 in-memory DB(testdb)에 남아 다음 테스트 클래스까지 오염시켰다
class ReservationControllerHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reserveReturnsSuccessResponseForValidBody() throws Exception {
        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roomName": "A-101",
                                  "requesterName": "민지"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("예약 완료")));
    }

    @Test
    void reserveReturns400ForBlankBodyFields() throws Exception {
        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roomName": "",
                                  "requesterName": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.roomName").value("방 이름은 비어있을 수 없습니다"))
                .andExpect(jsonPath("$.requesterName").value("예약자 이름은 비어있을 수 없습니다."));
    }

    @Test
    void listReturnsReservationsWithMemberNameOrNull() throws Exception {
        mockMvc.perform(post("/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "roomName": "C-303",
                                  "requesterName": "하늘"
                                }
                                """))
                .andExpect(status().isOk());

        // 이 경로로 만든 예약은 member를 배정하지 않는다 — memberName은 null로 내려와야 한다.
        mockMvc.perform(get("/reservations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roomName").value("C-303"))
                .andExpect(jsonPath("$[0].requesterName").value("하늘"))
                .andExpect(jsonPath("$[0].memberName").value(nullValue()));
    }

    @Test
    void cancelReturns404WhenReservationDoesNotExist() throws Exception {
        mockMvc.perform(post("/reservations/cancel/{id}", 999_999L).param("cancelReason","테스트 사유"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error")
                        .value("예약을 찾을 수 없습니다. (id: 999999)"));
    }

    @Test
    void cancelReturns400WhenIdIsNotPositive() throws Exception {
        mockMvc.perform(post("/reservations/cancel/{id}", 0L).param("cancelReason","테스트사유2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]")
                        .value("예약 번호는 1 이상이어야 합니다"));
    }
}
