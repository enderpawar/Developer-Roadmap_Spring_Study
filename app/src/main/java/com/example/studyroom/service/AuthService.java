package com.example.studyroom.service;

import com.example.studyroom.domain.Member;
import com.example.studyroom.exception.DuplicateLoginIdException;
import com.example.studyroom.exception.InvalidCredentialsException;
import com.example.studyroom.repository.MemberRepository;
import com.example.studyroom.security.JwtProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public AuthService(MemberRepository memberRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
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

    // Day23 — 로그인: 아이디로 회원을 찾고, matches()로 원문과 해시를 비교한다.
    // 아이디가 없거나 비밀번호가 틀려도 메시지를 똑같이("아이디 또는 비밀번호가 올바르지 않습니다")
    // 반환한다 — 둘 중 뭐가 틀렸는지 알려주면 존재하는 아이디를 공격자가 추측할 수 있다.
    public String login(String loginId, String rawPassword) {
        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(rawPassword, member.getPassword())) {
            throw new InvalidCredentialsException();
        }

        return jwtProvider.issue(member.getLoginId());
    }
}
