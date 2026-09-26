package com.example.studyroom.controller;

import com.example.studyroom.domain.Member;
import com.example.studyroom.dto.LoginRequest;
import com.example.studyroom.dto.LoginResponse;
import com.example.studyroom.dto.SignupRequest;
import com.example.studyroom.dto.SignupResponse;
import com.example.studyroom.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/signup")
    public SignupResponse signup(@RequestBody @Valid SignupRequest request) {
        Member member = authService.signup(request.loginId(), request.password(), request.name());
        return new SignupResponse(member.getId(), member.getLoginId(), member.getName());
    }

    @PostMapping("/auth/login")
    public LoginResponse login(@RequestBody @Valid LoginRequest request) {
        String token = authService.login(request.loginId(), request.password());
        return new LoginResponse(token);
    }
}
