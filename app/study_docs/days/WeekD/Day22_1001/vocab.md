# Day22 (10/1, Week D D1) 용어

주제: BCrypt 비밀번호 해싱과 회원가입 API

| 용어 | 한줄뜻 | 오늘 코드와 관찰 |
|---|---|---|
| 해싱(Hashing) | 원문을 고정 길이 요약값으로 바꾸는 단방향 변환. 역산으로 원문을 복원할 수 없음 | 비밀번호는 암호화가 아니라 해싱해서 저장 |
| 암호화(Encryption)와의 구분 | 암호화는 키로 복호화가 가능한 양방향 변환, 해싱은 애초에 복호화 자체가 없음 | 로그인은 원문 복원이 아니라 일치 여부만 필요해 해싱이 맞는 선택 |
| BCrypt | Blowfish 기반 적응형(adaptive) 해시 함수. cost factor로 계산량을 조절해 하드웨어가 빨라져도 대입 공격에 계속 버티도록 설계됨 | `BCryptPasswordEncoder` |
| Salt | 해시마다 무작위로 섞는 값. 같은 원문도 salt가 다르면 다른 해시가 나옴 | `sameRawPasswordProducesDifferentHashesEachTime` |
| Strength(cost factor) | salt를 `2^strength`번 반복 적용해 계산 비용을 늘리는 값. 기본 10 | 해시가 `$2a$10$`로 시작하는 이유 |
| `PasswordEncoder` | Spring Security의 해시·검증 인터페이스. `encode()`/`matches()` 두 메서드만 노출 | `PasswordEncoderConfig`가 `BCryptPasswordEncoder`를 이 타입으로 Bean 등록 |
| `spring-security-crypto` | 필터체인(`spring-boot-starter-security`) 전체 없이 해시 기능만 쓸 수 있는 최소 의존성 | `build.gradle.kts` — 필터체인은 Day24에서 추가 |
| `existsByLoginId` | Spring Data 메서드 이름 파생 쿼리. `login_id` 존재 여부만 확인 | `AuthService.signup()`의 중복 가입 방지 |

## 핵심 호출 경로

```text
POST /auth/signup
→ AuthService.signup(loginId, rawPassword, name)
→ memberRepository.existsByLoginId(loginId) — 있으면 DuplicateLoginIdException(409)
→ passwordEncoder.encode(rawPassword) — 호출마다 새 salt로 해시
→ new Member(name, loginId, hashedPassword)
→ memberRepository.save(member)
```
