package com.example.studyroom.repository;

import com.example.studyroom.domain.Member;
import com.example.studyroom.domain.Reservation;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Week C D7(버퍼) — 기술부채: "findAllWithMember()의 join fetch는 inner join이라 member_id가
// null인 예약을 결과에서 뺀다"를 실제 테스트로 확인하고, left join fetch로 바꾸면 유지됨을 증명한다.
@SpringBootTest
class FetchJoinInnerVsLeftTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void innerJoinFetchDropsReservationWithoutMember() {
        Member member = memberRepository.save(new Member("진우"));
        Reservation withMember = new Reservation("A-101", "진우");
        withMember.assignMember(member);
        reservationRepository.save(withMember);

        // 현재 HTTP로 생성되는 예약은 전부 이 상태다 — member를 배정하지 않는다.
        Reservation withoutMember = new Reservation("B-202", "손님");
        reservationRepository.save(withoutMember);

        entityManager.flush();
        entityManager.clear();

        List<Reservation> result = reservationRepository.findAllWithMember();

        assertEquals(1, result.size()); // B-202가 결과에서 사라졌다
        assertTrue(result.stream().noneMatch(r -> r.getRoomName().equals("B-202")));
    }

    @Test
    @Transactional
    void leftJoinFetchKeepsReservationWithoutMember() {
        Member member = memberRepository.save(new Member("진우"));
        Reservation withMember = new Reservation("A-101", "진우");
        withMember.assignMember(member);
        reservationRepository.save(withMember);

        Reservation withoutMember = new Reservation("B-202", "손님");
        reservationRepository.save(withoutMember);

        entityManager.flush();
        entityManager.clear();

        List<Reservation> result = reservationRepository.findAllWithMemberOrNull();

        assertEquals(2, result.size()); // B-202도 남는다

        Reservation found = result.stream()
                .filter(r -> r.getRoomName().equals("B-202"))
                .findFirst()
                .orElseThrow();
        assertNull(found.getMember()); // 오른쪽(member)이 없을 뿐, 왼쪽(reservation)은 그대로
    }
}
