package com.example.studyroom.repository;

import com.example.studyroom.domain.Reservation;
//import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

//@Repository //Spring이 관리하는 Bean, "이 클래스는 저장소 역할의 Bean이다."
// Repository 어노테이션은 Jdbc에 붙였다. 이건 스프링 없이 단위 테스트를 위해 직접 조립함. DB 없음.
public class InMemoryReservationRepository implements ReservationRepository {
    private final List<Reservation> store = new ArrayList<>();
    private long nextId = 1;

    @Override // 메서드를 여기서 override로 재정의해주는거지. 즉 How를 정의해준다!
    public Reservation save(Reservation reservation) {
        if (reservation.getId() == null){
            reservation.assignId(nextId++);
            store.add(reservation);
            return reservation;
        }

        for (int index = 0; index < store.size(); index++) {
            if (store.get(index).getId().equals(reservation.getId())) {
                store.set(index, reservation);
                return reservation;
            }
        }

        throw new IllegalArgumentException("저장소에 없는 예약 번호입니다: " + reservation.getId());
    }




    @Override
    public List<Reservation> findAll() {
        return store;
    }
    @Override
    public Optional<Reservation> findById(Long id) {
        for(Reservation r : store){
            if(r.getId().equals(id)){
                return Optional.of(r);
            }
        }
        return Optional.empty();

    }

    // Day33 — SpringDataReservationRepository.findOverlapping()의 JPQL과 같은 규칙을
    // 순수 자바 컬렉션으로 그대로 옮긴 것. Unit 테스트(ReservationOverlapTest)가 DB 없이
    // 이 규칙을 검증할 수 있는 이유가 이 메서드다.
    @Override
    public List<Reservation> findOverlapping(String roomName, LocalDateTime startAt, LocalDateTime endAt) {
        List<Reservation> result = new ArrayList<>();
        for (Reservation r : store) {
            boolean sameRoom = r.getRoomName().equals(roomName);
            boolean stillActive = r.isConfirmed();
            boolean hasTimeSlot = r.getStartAt() != null && r.getEndAt() != null;
            boolean overlaps = hasTimeSlot && r.getStartAt().isBefore(endAt) && startAt.isBefore(r.getEndAt());

            if (sameRoom && stillActive && overlaps) {
                result.add(r);
            }
        }
        return result;
    }
}
