package com.example.studyroom.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Day29 — LoggingConfig가 FilterRegistrationBean으로 등록한 RequestIdFilter가 실제 서블릿
// 필터 체인(Security 포함)에 실제로 끼워졌는지 HTTP 레벨(MockMvc)로 확인한다.
// Unit 테스트(RequestIdFilterTest)는 필터 객체 하나만 직접 돌린 것이라, "Spring 컨텍스트에
// Bean으로 등록됐을 때도 진짜 적용되는지"는 이 테스트가 아니면 확인할 수 없다.
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RequestIdHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private Logger rootLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        rootLogger.detachAppender(appender);
    }

    @Test
    void responseIncludesGeneratedRequestIdHeader() throws Exception {
        mockMvc.perform(get("/hello"))
                .andExpect(status().isOk())
                .andExpect(header().exists(RequestIdFilter.REQUEST_ID_HEADER));
    }

    @Test
    void echoesClientProvidedRequestId() throws Exception {
        mockMvc.perform(get("/hello").header(RequestIdFilter.REQUEST_ID_HEADER, "fixed-test-id"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.REQUEST_ID_HEADER, "fixed-test-id"));
    }

    @Test
    void rawPasswordNeverAppearsInLogsDuringSignupAndLogin() throws Exception {
        String rawPassword = "super-secret-pw1";

        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "logtest01",
                                  "password": "%s",
                                  "name": "로그테스트"
                                }
                                """.formatted(rawPassword)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "loginId": "logtest01",
                                  "password": "%s"
                                }
                                """.formatted(rawPassword)))
                .andExpect(status().isOk());

        List<String> logged = appender.list.stream()
                .filter(event -> event.getLevel().isGreaterOrEqual(Level.DEBUG))
                .map(ILoggingEvent::getFormattedMessage)
                .toList();

        assertFalse(logged.isEmpty()); // 이 리스트가 비어 있으면 "안 남겼다"가 아니라 "안 잡혔다"는 뜻 — 검증 자체가 무의미해진다.
        assertTrue(logged.stream().noneMatch(line -> line.contains(rawPassword)));
    }
}
