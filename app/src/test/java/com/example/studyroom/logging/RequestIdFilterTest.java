package com.example.studyroom.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Day29 — Spring 컨테이너 없이 필터 하나만 직접 생성해서 돌리는 Unit 테스트.
// MockHttpServletRequest/Response·MockFilterChain은 spring-boot-starter-test가 제공하는
// "가짜 서블릿" 객체다 — 실제 톰캣이 없어도 Filter 계약(doFilter)을 그대로 호출할 수 있다.
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();
    private Logger logbackLogger;
    private ListAppender<ILoggingEvent> appender;

    // Authorization 마스킹 로그를 검증하려면 실제 로그 출력을 가로채야 한다 — logback의
    // ListAppender를 이 필터의 로거에 직접 붙여서, 찍힌 로그 이벤트를 리스트로 모아둔다.
    @BeforeEach
    void attachAppender() {
        logbackLogger = (Logger) LoggerFactory.getLogger(RequestIdFilter.class);
        appender = new ListAppender<>();
        appender.start();
        logbackLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logbackLogger.detachAppender(appender);
    }

    @Test
    void generatesRequestIdWhenClientDoesNotSendOne() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/hello");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String generated = response.getHeader(RequestIdFilter.REQUEST_ID_HEADER);
        assertNotNull(generated);
        assertFalse(generated.isBlank());
    }

    @Test
    void reusesClientProvidedRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/hello");
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "client-fixed-id");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("client-fixed-id", response.getHeader(RequestIdFilter.REQUEST_ID_HEADER));
    }

    @Test
    void clearsMdcAfterRequestEvenWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/reservations");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain throwingChain = (req, res) -> {
            throw new RuntimeException("체인 도중 예외 — finally가 그래도 MDC를 지우는지 확인");
        };

        assertThrows(RuntimeException.class, () -> filter.doFilter(request, response, throwingChain));
        // 서블릿 컨테이너는 스레드를 재사용한다 — 여기서 안 지워지면 다음 요청 로그에 이번 requestId가 새어나간다.
        assertNull(MDC.get(RequestIdFilter.MDC_KEY));
    }

    @Test
    void maskAuthorizationHidesRawTokenInLogLine() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        String realToken = "Bearer eyJhbGciOiJIUzI1NiJ9.super-secret-token-body.signature";
        request.addHeader("Authorization", realToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        List<String> logged = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertTrue(logged.stream().anyMatch(line -> line.contains("Bearer ***")));
        // 토큰 원문은 절대 로그에 그대로 남으면 안 된다.
        assertTrue(logged.stream().noneMatch(line -> line.contains(realToken)));
    }

    @Test
    void maskAuthorizationReturnsDashWhenHeaderMissing() {
        assertEquals("-", RequestIdFilter.maskAuthorization(null));
        assertEquals("-", RequestIdFilter.maskAuthorization(""));
    }

    @Test
    void maskAuthorizationNeverIncludesRawHeaderValue() {
        String withPasswordLikeSuffix = "Bearer abc.def.ghi";
        assertEquals("Bearer ***", RequestIdFilter.maskAuthorization(withPasswordLikeSuffix));
    }
}
