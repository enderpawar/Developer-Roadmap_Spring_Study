package com.example.studyroom.service;

import com.example.studyroom.exception.InvalidReservationTimeException;
import com.example.studyroom.exception.ReservationOverlapException;
import com.example.studyroom.repository.InMemoryReservationRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Day33 — 예약 시간대 중복 방지. Spring 컨테이너 없이 InMemoryReservationRepository로
// 규칙만 빠르게 검증하는 Unit 테스트.
class ReservationOverlapTest {

    private final LocalDateTime tenAm = LocalDateTime.of(2026, 10, 20, 10, 0);
    private final LocalDateTime elevenAm = LocalDateTime.of(2026, 10, 20, 11, 0);
    private final LocalDateTime noon = LocalDateTime.of(2026, 10, 20, 12, 0);

    @Test
    void reserveThrowsWhenTimeSlotOverlapsExistingReservationInSameRoom() {
        ReservationService service = new ReservationService(new InMemoryReservationRepository());
        service.reserve("A-101", "민지", tenAm, noon); // 10~12시 선점

        // 10시 30분~11시 30분 — 앞의 예약과 정확히 겹친다.
        assertThrows(ReservationOverlapException.class,
                () -> service.reserve("A-101", "철수", tenAm.plusMinutes(30), elevenAm.plusMinutes(30)));
    }

    @Test
    void reserveAllowsTouchingIntervalsInSameRoom() {
        ReservationService service = new ReservationService(new InMemoryReservationRepository());
        service.reserve("A-101", "민지", tenAm, elevenAm); // 10~11시

        // 11~12시 — 앞의 예약과 끝나는 시각과 시작하는 시각이 정확히 맞닿는다(경계). 겹침이 아니다.
        assertDoesNotThrow(() -> service.reserve("A-101", "철수", elevenAm, noon));
    }

    @Test
    void reserveAllowsOverlappingTimeInDifferentRoom() {
        ReservationService service = new ReservationService(new InMemoryReservationRepository());
        service.reserve("A-101", "민지", tenAm, noon);

        // 시간대는 완전히 겹치지만 방이 다르다 — 막을 이유가 없다.
        assertDoesNotThrow(() -> service.reserve("B-202", "철수", tenAm, noon));
    }

    @Test
    void reserveAllowsOverlappingTimeAfterExistingReservationWasCancelled() {
        ReservationService service = new ReservationService(new InMemoryReservationRepository());
        var first = service.reserve("A-101", "민지", tenAm, noon);
        service.cancel(first.getId(), "일정 변경");

        // 취소된 예약은 더 이상 그 시간대를 막지 않는다.
        assertDoesNotThrow(() -> service.reserve("A-101", "철수", tenAm, noon));
    }

    @Test
    void reserveThrowsWhenEndAtIsNotAfterStartAt() {
        ReservationService service = new ReservationService(new InMemoryReservationRepository());

        assertThrows(InvalidReservationTimeException.class,
                () -> service.reserve("A-101", "민지", noon, tenAm)); // 종료가 시작보다 이르다
    }

    @Test
    void reserveWithoutTimeSlotSkipsOverlapCheckEvenInSameRoom() {
        // Day33 이전부터 있던 호출(2-파라미터 reserve)은 시간대가 없다 — 검증·중복검사 자체를 안 한다.
        ReservationService service = new ReservationService(new InMemoryReservationRepository());
        service.reserve("A-101", "민지"); // startAt/endAt 둘 다 null

        assertDoesNotThrow(() -> service.reserve("A-101", "철수")); // 이것도 null, null — 겹침 계산 대상이 아니다
    }
}
