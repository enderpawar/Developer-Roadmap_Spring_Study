package com.example.studyroom.controller;

import com.example.studyroom.dto.ReservationSummary;
import com.example.studyroom.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Day26 — Slice 테스트: Spring MVC 계층(Controller + 관련 컴포넌트)만 띄우고, Service는 Mock으로 대체한다.
// @SpringBootTest처럼 DB·트랜잭션까지 전부 띄우지 않아서 훨씬 빠르다 — "Controller가 요청/응답을
// 올바르게 매핑하는가"만 검증하고 싶을 때 쓴다.
//
// 처음에 @AutoConfigureMockMvc(addFilters = false) 없이 돌렸더니 200을 기대한 응답이 401로 나왔다
// (day26.md에 원문 기록) — @WebMvcTest는 SecurityConfig 같은 일반 @Configuration Bean은 스캔하지
// 않지만, spring-boot-starter-security가 클래스패스에 있으면 Spring Boot가 기본 보안(모든 요청 인증
// 필요)을 자동 적용하기 때문이다. 이 슬라이스는 "MVC 매핑이 맞는가"만 보고 싶으므로 필터 자체를 끈다
// (대신 이 테스트는 인증/인가 동작은 전혀 검증하지 못한다 — 그건 Day24/25의 @SpringBootTest들의 몫).
@WebMvcTest(ReservationController.class)
@AutoConfigureMockMvc(addFilters = false)
class ReservationControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationService reservationService;

    @Test
    void listReturnsServiceResultAsJson() throws Exception {
        given(reservationService.findAllSummaries())
                .willReturn(List.of(new ReservationSummary("A-101", "민지", null)));

        mockMvc.perform(get("/reservations").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roomName").value("A-101"))
                .andExpect(jsonPath("$[0].requesterName").value("민지"));
    }
}
