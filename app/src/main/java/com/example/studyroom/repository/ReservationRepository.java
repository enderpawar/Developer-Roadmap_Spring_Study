package com.example.studyroom.repository;
// ReservationRepository - 인터페이스 역할
import com.example.studyroom.domain.Reservation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
//인터페이스 = "What to do(무엇을 할 수 있는지)"만 약속. "How"는 구현체가 정한다.
// 그니깐 여기선 저장 기능, 전체 조회 기능만 구현하겠다. 이런 메서드가 있다~ 이렇게 정의해놓은거지.
public interface ReservationRepository {
    Reservation save(Reservation reservation); //이렇게
    List<Reservation> findAll();
    Optional<Reservation> findById(Long id);
    default List<Reservation> findAllWithMember(){
        throw new UnsupportedOperationException();
    };
    default List<Reservation> findAllWithMemberOrNull(){
        throw new UnsupportedOperationException();
    };

    // Day33 — 같은 방(roomName)에서 [startAt, endAt)과 겹치는, 아직 취소되지 않은(confirmed) 예약을 찾는다.
    // InMemoryReservationRepository(Unit 테스트용)는 이걸 실제로 구현하고, JdbcReservationRepository(대조군,
    // Bean으로 안 씀)는 기본값(UnsupportedOperationException)을 그대로 둔다.
    default List<Reservation> findOverlapping(String roomName, LocalDateTime startAt, LocalDateTime endAt){
        throw new UnsupportedOperationException();
    };
}

