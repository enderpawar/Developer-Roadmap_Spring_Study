package com.example.studyroom.repository;

import com.example.studyroom.domain.Member;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Day26 — Slice 테스트: JPA 관련 Bean(Repository, EntityManager)만 띄운다. @SpringBootTest보다
// 가볍고, @WebMvcTest와 달리 실제 DB(Flyway로 만든 스키마)까지는 그대로 쓴다 — Repository 쿼리
// 메서드 자체("existsByLoginId·findByLoginId가 SQL을 제대로 만드는가")를 검증하고 싶을 때 쓴다.
// 기본적으로 각 테스트를 트랜잭션으로 감싸고 끝나면 롤백한다(@SpringBootTest + @Transactional과 동일 효과).
@DataJpaTest
class MemberRepositoryDataJpaTest {

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void existsByLoginIdReflectsSavedMember() {
        memberRepository.save(new Member("진우", "datajpa-test-01", "hashed"));

        assertTrue(memberRepository.existsByLoginId("datajpa-test-01"));
        assertFalse(memberRepository.existsByLoginId("no-such-login-id"));
    }

    @Test
    void findByLoginIdReturnsSavedMember() {
        memberRepository.save(new Member("철수", "datajpa-test-02", "hashed"));

        Optional<Member> found = memberRepository.findByLoginId("datajpa-test-02");

        assertTrue(found.isPresent());
        assertEquals("철수", found.get().getName());
    }

    @Test
    void findByLoginIdReturnsEmptyWhenNotFound() {
        Optional<Member> found = memberRepository.findByLoginId("no-such-login-id");

        assertTrue(found.isEmpty());
    }
}
