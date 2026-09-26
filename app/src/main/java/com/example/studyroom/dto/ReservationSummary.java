package com.example.studyroom.dto;

// 목록 조회 전용 응답 모양. Reservation 엔티티를 그대로 반환하면 지연 로딩 필드가
// 컨트롤러 밖(Jackson 직렬화 시점)에서 초기화되려다 터질 수 있어서, 서비스 안에서 미리 값만 꺼내 담는다.
public record ReservationSummary(String roomName, String requesterName, String memberName) {
}
