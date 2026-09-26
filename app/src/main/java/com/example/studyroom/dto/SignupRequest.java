package com.example.studyroom.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "아이디는 비어있을 수 없습니다") String loginId,
        @NotBlank(message = "비밀번호는 비어있을 수 없습니다")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다") String password,
        @NotBlank(message = "이름은 비어있을 수 없습니다") String name) {
}
