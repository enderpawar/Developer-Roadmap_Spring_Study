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

    // Day24 — 인가(authorization)에 쓰는 권한. 필드 초기값을 줘서 기존 생성자 두 개(Member(name),
    // Member(name, loginId, password)) 모두 자동으로 "USER"가 되게 한다.
    private String role = "USER";

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

    public String getRole() {
        return role;
    }

    // 별도 관리자 가입 화면 없이, 학습 범위에서는 이 메서드로 테스트용 관리자를 만든다.
    public void grantAdmin() {
        this.role = "ADMIN";
    }

    public List<Reservation> getReservations() {
        return reservations;
    }
}