package com.example.studyroom.service;

import com.example.studyroom.domain.Reservation;
import com.example.studyroom.dto.ReservationSummary;
import com.example.studyroom.exception.ReservationNotFoundException;
import com.example.studyroom.repository.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ReservationService{

    private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

    private final ReservationRepository reservationRepository;

    public ReservationService(ReservationRepository reservationRepository){
        this.reservationRepository = reservationRepository;
    }

    public Reservation reserve(String roomName, String requesterName){
        Reservation reservation = new Reservation(roomName,requesterName);
        reservation.confirm(); // 예약 상태를 변경하고
        return reservationRepository.save(reservation); // reservationRepository의 save 메서드 인자값으로 service가 상태를 변경한 reservation 값을 넘겨준다.
    }

    @Transactional //트랜잭션 경계 : 조회 -> 상태변경 -> DB에 반영  이 작업들이 원자성을 반영하도록,
    // 전부 성공하거나 전부 취소하는 하나의 단위로 묶는 범위

    public Reservation cancel(Long id,String cancelReason){
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(id)); // 1. 예약이 있는지 없는지 조회

        // Day30 — 취소 사유 앞뒤에 공백이 붙어 들어와도(예: 프런트 입력창에서 그대로 전달) 깔끔하게 저장하자.
        reservation.cancel(sanitizeCancelReason(cancelReason)); //2. 취소 상태로 변경
        // reservationRepository.save(reservation); //3. DB에 반영 -> @Transactional 사용시 안써도 됨.
        // 조회된 엔티티가 영속 상태라 변경 감지를 처리한다.

        return reservation;
    }

    private String sanitizeCancelReason(String cancelReason) {
        String trimmed = cancelReason.trim();
        // Day30 버그 수정 — trim()은 이미 앞뒤 공백을 전부 지운다. 여기서 substring(1)로
        // 앞 글자를 한 번 더 잘라낸 것이 버그였다(day30.md 재현 기록 참고). trim() 결과를 그대로 쓴다.
        log.debug("cancelReason raw='{}' sanitized='{}'", cancelReason, trimmed);
        return trimmed;
    }

    @Transactional(readOnly = true) // 조회 전용 — 변경 감지·flush를 위한 스냅샷 비교를 생략해 가볍다
    public List<ReservationSummary> findAllSummaries() {
        // left join fetch — member가 없는 예약(현재 HTTP 경로로 만든 예약 전부)도 목록에서 빠지면 안 된다.
        return reservationRepository.findAllWithMemberOrNull().stream()
                .map(r -> new ReservationSummary(
                        r.getRoomName(),
                        r.getRequesterName(),
                        r.getMember() == null ? null : r.getMember().getName()))
                .toList();
    }
}
