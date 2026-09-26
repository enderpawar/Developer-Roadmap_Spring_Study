package com.example.studyroom.exception;

// Day33 — 종료 시각이 시작 시각보다 나중이 아닌 요청. DTO 애노테이션 하나로는 "두 필드 사이의 관계"를
// 표현하기 까다로워서(record는 필드별 애노테이션이 기본이라 교차 검증이 번거롭다), 서비스 계층에서
// 직접 검사하고 이 예외로 400을 만든다.
public class InvalidReservationTimeException extends RuntimeException {
    public InvalidReservationTimeException() {
        super("종료 시각은 시작 시각보다 나중이어야 합니다.");
    }
}
