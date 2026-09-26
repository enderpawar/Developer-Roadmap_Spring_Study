package com.example.studyroom.exception;

import java.time.LocalDateTime;

// Day33 — 같은 방(room)에 이미 겹치는 시간대의 예약이 있을 때. 리소스 상태(이미 존재하는 예약)와
// 충돌하는 요청이라 409 CONFLICT로 매핑한다(GlobalExceptionHandler).
public class ReservationOverlapException extends RuntimeException {
    public ReservationOverlapException(String roomName, LocalDateTime startAt, LocalDateTime endAt) {
        super("해당 방은 이미 그 시간대에 예약이 있습니다. (room: " + roomName
                + ", 요청 시간: " + startAt + " ~ " + endAt + ")");
    }
}
