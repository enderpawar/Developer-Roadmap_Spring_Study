package com.example.studyroom.dto;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
// record = 데이터 모양만 정의. 생성자, getter, equals, hashcode 자동 생성.
// ㄴ DTO르르 만드는데 자바의 record 문법을 사용했다고 생각하면 됨. record가 DTO 그 자체다!!
// 불변(immutable) 속성을 지님. - 필드를 한 번 정하면 못 바꾼다.
//
// Day33 — startAt/endAt은 선택 항목이다(둘 다 안 보내면 기존처럼 시간대 없는 예약).
// "둘 다 있을 때 end가 start보다 나중인지"처럼 필드 두 개를 같이 보는 검증은 @NotNull 같은
// 필드별 애노테이션으로 표현하기 애매해서, ReservationService.reserve()에서 직접 검사한다.
public record ReservationRequest(
        @NotBlank(message = "방 이름은 비어있을 수 없습니다") String roomName,
        @NotBlank(message = "예약자 이름은 비어있을 수 없습니다.") String requesterName,
        LocalDateTime startAt,
        LocalDateTime endAt) {
}


