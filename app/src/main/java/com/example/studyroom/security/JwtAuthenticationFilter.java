package com.example.studyroom.security;

import com.example.studyroom.domain.Member;
import com.example.studyroom.repository.MemberRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// Day24 — 매 요청마다 한 번씩(OncePerRequestFilter) Authorization 헤더의 JWT를 읽어서
// SecurityContext에 인증 정보를 채워 넣는다. 여기서 인증을 못 채우면(토큰 없음/위조/만료),
// 뒤따르는 인가 단계(.anyRequest().authenticated())에서 걸려 AuthenticationEntryPoint가 401을 낸다.
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final MemberRepository memberRepository;

    public JwtAuthenticationFilter(JwtProvider jwtProvider, MemberRepository memberRepository) {
        this.jwtProvider = jwtProvider;
        this.memberRepository = memberRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                String loginId = jwtProvider.parseSubject(token);
                memberRepository.findByLoginId(loginId).ifPresent(this::authenticate);
            } catch (JwtException e) {
                // 위조된 서명(SignatureException)·만료(ExpiredJwtException)·형식 오류(MalformedJwtException)
                // 전부 JwtException 하위타입이다. 여기서 401을 직접 만들지 않고 인증 없는 상태로 흘려보낸다 —
                // 이후 .anyRequest().authenticated()가 401을 만들어준다.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(Member member) {
        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole()));
        var authentication = new UsernamePasswordAuthenticationToken(member.getLoginId(), null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
