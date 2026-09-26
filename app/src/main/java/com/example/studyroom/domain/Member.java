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

    // 역방향(inverse side) — 연관관계의 주인은 Reservation.member(FK를 들고 있는 쪽).
    // mappedBy = "member" → 이 필드는 DB에 컬럼을 만들지 않고, Reservation.member를 그대로 조회해서 보여주기만 한다.
    @OneToMany(mappedBy = "member", fetch = FetchType.LAZY)
    private List<Reservation> reservations = new ArrayList<>();

    protected Member() {
    } // JPA 기본 생성자 — Day15 오답재시험 항목과 같은 이유로 필요

    public Member(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<Reservation> getReservations() {
        return reservations;
    }
}