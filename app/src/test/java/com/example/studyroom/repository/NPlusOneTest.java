package com.example.studyroom.repository;

import com.example.studyroom.domain.Member;
import com.example.studyroom.domain.Reservation;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

// generate_statistics는 이 테스트에서만 켠다 — 다른 테스트까지 통계를 쌓으면 매 요청마다 느려진다.
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class NPlusOneTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private EntityManager entityManager;

    private Statistics statistics() {
        // Spring Boot는 org.hibernate.SessionFactory를 빈으로 등록해주지 않는다.
        // EntityManagerFactory를 통째로 SessionFactory로 unwrap해서 꺼내 쓴다.
        return entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
    }

    @Test
    @Transactional
    void findAllTriggersNPlusOneSelects() {
        String[] names = {"진우", "철수", "영희"};
        for (String name : names) {
            Member member = memberRepository.save(new Member(name));
            Reservation reservation = new Reservation("Room-" + name, name);
            reservation.assignMember(member);
            reservationRepository.save(reservation);
        }

        entityManager.flush();
        entityManager.clear();

        Statistics statistics = statistics();
        statistics.clear(); // 지금까지의 INSERT는 세지 않는다 — 여기부터가 관찰 구간

        List<Reservation> reservations = reservationRepository.findAll(); // SELECT 1번(reservation만)
        for (Reservation r : reservations) {
            r.getMember().getName(); // LAZY 프록시 초기화 — reservation마다 SELECT 1번씩(N번)
        }

        // 1(findAll) + N(멤버 3명 각각 지연 로딩) = 4
        assertEquals(1 + names.length, statistics.getPrepareStatementCount());
    }

    @Test
    @Transactional
    void findAllWithMemberUsesSingleJoinQuery() {
        String[] names = {"진우", "철수", "영희"};
        for (String name : names) {
            Member member = memberRepository.save(new Member(name));
            Reservation reservation = new Reservation("Room-" + name, name);
            reservation.assignMember(member);
            reservationRepository.save(reservation);
        }

        entityManager.flush();
        entityManager.clear();

        Statistics statistics = statistics();
        statistics.clear();

        List<Reservation> reservations = reservationRepository.findAllWithMember(); // join fetch → SELECT 1번
        for (Reservation r : reservations) {
            r.getMember().getName(); // 이미 함께 로딩됨 — 추가 SELECT 없음
        }

        assertEquals(1, statistics.getPrepareStatementCount());
    }
}