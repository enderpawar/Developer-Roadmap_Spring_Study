package com.example.studyroom.repository;


import com.example.studyroom.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;


import java.util.List;

public interface SpringDataReservationRepository extends JpaRepository<Reservation, Long> {
    @Query("select r from Reservation r join  fetch r.member")
    List<Reservation> findAllWithMember();

    // join fetch(inner join)는 member_id가 null인 예약을 결과에서 빼버린다.
    // left join fetch로 바꾸면 member가 없어도(오른쪽이 없어도) 왼쪽(reservation)은 그대로 남는다.
    @Query("select r from Reservation r left join fetch r.member")
    List<Reservation> findAllWithMemberOrNull();
}

