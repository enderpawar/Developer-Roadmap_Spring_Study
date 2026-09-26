package com.example.studyroom.dto;

// 회원가입 응답 — password(해시라도)는 절대 내려주지 않는다.
public record SignupResponse(Long id, String loginId, String name) {
}
