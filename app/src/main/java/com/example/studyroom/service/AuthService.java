package com.example.studyroom.service;

import com.example.studyroom.domain.Member;
import com.example.studyroom.exception.DuplicateLoginIdException;
import com.example.studyroom.repository.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Member signup(String loginId, String rawPassword, String name) {
        if (memberRepository.existsByLoginId(loginId)) {
            throw new DuplicateLoginIdException(loginId);
        }

        // 같은 비밀번호라도 매번 다른 해시가 나온다 — BCrypt가 매 호출마다 랜덤 salt를 새로 뽑아 섞기 때문.
        String hashedPassword = passwordEncoder.encode(rawPassword);
        Member member = new Member(name, loginId, hashedPassword);
        return memberRepository.save(member);
    }
}
