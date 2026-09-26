package com.example.studyroom.repository;


import com.example.studyroom.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.time.LocalDateTime;
import java.util.List;

public interface SpringDataReservationRepository extends JpaRepository<Reservation, Long> {
    @Query("select r from Reservation r join  fetch r.member")
    List<Reservation> findAllWithMember();

    // join fetch(inner join)는 member_id가 null인 예약을 결과에서 빼버린다.
    // left join fetch로 바꾸면 member가 없어도(오른쪽이 없어도) 왼쪽(reservation)은 그대로 남는다.
    @Query("select r from Reservation r left join fetch r.member")
    List<Reservation> findAllWithMemberOrNull();

    // Day33 — 구간 겹침의 표준 공식: start < otherEnd && otherStart < end.
    // 등호(<=)가 아니라 부등호(<)라서, 한쪽 끝과 다른 쪽 시작이 정확히 맞닿는 경우(touching)는
    // 겹침으로 안 잡힌다 — "10~11시"와 "11~12시"는 허용돼야 하기 때문에 의도한 동작이다.
    // 취소된 예약(confirmed=false)은 더 이상 그 시간대를 막고 있지 않으므로 조건에서 뺀다.
    @Query("select r from Reservation r " +
            "where r.roomName = :roomName and r.confirmed = true " +
            "and r.startAt is not null and r.endAt is not null " +
            "and r.startAt < :endAt and :startAt < r.endAt")
    List<Reservation> findOverlapping(@Param("roomName") String roomName,
                                       @Param("startAt") LocalDateTime startAt,
                                       @Param("endAt") LocalDateTime endAt);
}

