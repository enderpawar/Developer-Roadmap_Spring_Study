package com.example.studyroom.repository;

import com.example.studyroom.domain.Member;
import com.example.studyroom.domain.Reservation;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.hibernate.collection.spi.PersistentBag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class MemberLazyProxyTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void memberFieldIsProxyBeforeAccess() {
        Member member = memberRepository.save(new Member("진우"));

        Reservation reservation = new Reservation("D-101", "진우");
        reservation.assignMember(member);
        reservationRepository.save(reservation);

        entityManager.flush();
        entityManager.clear(); // 1차 캐시 비움 — Day11에서 본 그 메서드

        Reservation found = reservationRepository.findById(reservation.getId()).orElseThrow();

        System.out.println("클래스 타입: " + found.getMember().getClass());
        // 여기서 getName()을 호출하기 전/후로 콘솔에 SELECT가 몇 번, 언제 찍히는지 볼 것
        System.out.println("이름: " + found.getMember().getName());
    }

    @Test
    @Transactional
    void memberReservationsCollectionIsLazyPersistentBag() {
        Member member = memberRepository.save(new Member("철수"));

        Reservation reservation = new Reservation("D-102", "철수");
        reservation.assignMember(member);
        reservationRepository.save(reservation);

        entityManager.flush();
        entityManager.clear();

        Member found = memberRepository.findById(member.getId()).orElseThrow();

        // Reservation.member 필드의 프록시(HibernateProxy)와 달리, 컬렉션은 로딩 즉시
        // PersistentBag "래퍼"로 감싸져 있다 — 다만 그 내용물(size, 순회)은 아직 비어 있는 채로 멈춰 있다.
        assertInstanceOf(PersistentBag.class, found.getReservations());
        assertFalse(Hibernate.isInitialized(found.getReservations()));

        int size = found.getReservations().size(); // 여기서 처음 SELECT ... FROM reservation WHERE member_id = ? 발생

        assertTrue(Hibernate.isInitialized(found.getReservations()));
        assertEquals(1, size);
    }
}