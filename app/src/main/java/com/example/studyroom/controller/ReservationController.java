package com.example.studyroom.controller;

import com.example.studyroom.domain.Reservation;
import com.example.studyroom.dto.ReservationRequest;
import com.example.studyroom.dto.ReservationSummary;
import com.example.studyroom.service.ReservationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
public class ReservationController{
    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService){
        this.reservationService = reservationService;
    }

    //@RequestBody = 클라이언트가 보낸 JSON body를 자바 객체 (record)로 자동 변환
    @PostMapping("/reservations")
    public String reserve(@RequestBody @Valid ReservationRequest request) {
        Reservation reservation = reservationService.reserve(request.roomName(),request.requesterName());

        return "예약 번호"+ reservation.getId()+"-"+reservation.getRequesterName() + "님이 " + reservation.getRoomName() + " 예약 완료 (확정: " + reservation.isConfirmed() + ")";
    }
//아마 "" 로 해버리면 @Valid 유효성 검사 들어가서 400 BadRequest 뜨지 않을까 싶은데..

    @GetMapping("/reservations")
    public List<ReservationSummary> list() {
        return reservationService.findAllSummaries();
    }

    @PostMapping("/reservations/cancel/{id}")
    public String cancel(@PathVariable @Positive(message = "예약 번호는 1 이상이어야 합니다") Long id,@RequestParam @NotBlank (message = "취소 시 사유를 남겨주세요") String cancelReason) {
        Reservation reservation = reservationService.cancel(id,cancelReason);

        return reservation.getRequesterName() + "님이" + reservation.getRoomName() + " 예약을 취소하셨습니다 (확정 : " + reservation.isConfirmed() + ")";
    }

}
