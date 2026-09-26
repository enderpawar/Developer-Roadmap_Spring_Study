package com.example.studyroom.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    // Day22 — 로그인 계정 정보. 기존 Member(name)로 만들어진 로우(연관관계 테스트 픽스처 등)는
    // 로그인 계정이 없는 "회원 정보만 있는" 상태로 남아도 되게 nullable로 둔다.
    private String loginId;

    private String password; // 항상 BCrypt 해시만 저장한다 — 평문 저장 금지.

    // 역방향(inverse side) — 연관관계의 주인은 Reservation.member(FK를 들고 있는 쪽).
    // mappedBy = "member" → 이 필드는 DB에 컬럼을 만들지 않고, Reservation.member를 그대로 조회해서 보여주기만 한다.
    @OneToMany(mappedBy = "member", fetch = FetchType.LAZY)
    private List<Reservation> reservations = new ArrayList<>();

    protected Member() {
    } // JPA 기본 생성자 — Day15 오답재시험 항목과 같은 이유로 필요

    public Member(String name) {
        this.name = name;
    }

    // Day22 — 회원가입(로그인 계정 있음) 경로 전용 생성자.
    public Member(String name, String loginId, String password) {
        this.name = name;
        this.loginId = loginId;
        this.password = password;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getPassword() {
        return password;
    }

    public List<Reservation> getReservations() {
        return reservations;
    }
}