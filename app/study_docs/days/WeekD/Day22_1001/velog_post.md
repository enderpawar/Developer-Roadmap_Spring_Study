# [Spring Study Day 22] 비밀번호 저장 전략 — BCrypt 해싱과 회원가입 API

Day21에서 예약 목록 조회 API를 추가하며 Week C(트랜잭션·프록시·연관관계)를 마무리했다. 오늘부터 Week D — 인증이다. `Member`에 로그인 계정(아이디·비밀번호)을 추가하고 `POST /auth/signup` 회원가입 API를 만들면서, 비밀번호를 **어떤 형태로 저장할지**를 다룬다. 필터체인(`spring-boot-starter-security`)과 로그인 API는 이 글의 범위가 아니다 — `spring-security-crypto` 모듈만으로 해시 Bean을 직접 구성했다.

> Member에 `loginId`/`password` 컬럼을 nullable로 추가하고, `BCryptPasswordEncoder`를 `PasswordEncoder` Bean으로 등록해 회원가입 시 원문 대신 해시를 저장했다. 같은 원문을 두 번 해시하면 매번 다른 문자열이 나오는 것과, 해시가 `$2a$10$`로 시작하는 것을 단위 테스트로 확인했다. 이번 유닛은 컴파일·런타임 오류 없이 첫 실행부터 전체 테스트 36개가 통과했다.

> **오늘의 흐름** `Member에 로그인 계정 컬럼 추가 → BCryptPasswordEncoder Bean → POST /auth/signup → 해시 저장·중복 409·검증 400`
>
> 이전 Day: 예약 목록 조회 API와 Week C 기술부채 정리 (Day20 & Day21)
> 다음 Day: JWT 발급·검증, `POST /auth/login` (Day23)

![인코딩·암호화·해싱을 What/Why/Examples 3열로 비교한 표. 해싱 행은 "단방향 요약값, 무결성·비밀번호 검증용, MD5·SHA-256·SHA-3"로 설명되어 암호화(양방향, 키로 복호화 가능)와 구분된다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day22-web-hashing-vs-encryption.png)

*출처: [Encoding, Encryption, and Hashing](https://auth0.com/blog/encoding-encryption-hashing/) — Andrea Chiarelli, Auth0(Okta) 블로그*

## 1. 개념 설명

### 1) 해싱과 암호화의 구분

> **해싱(Hashing)** = 원문을 고정 길이 요약값으로 바꾸는 단방향 변환. 같은 입력은 같은 출력을 내지만, 출력만으로 원문을 역산할 수 없다.

우리 코드에서는 대칭·비대칭 암호화 라이브러리가 등장하지 않는다는 것 자체가 판단이다. `PasswordEncoderConfig`가 등록하는 건 암호화기가 아니라 해시기다.

```java
@Bean
public PasswordEncoder passwordEncoder() {
    // strength 기본값 10 — 해시 앞부분이 "$2a$10$..."로 시작하는 이유.
    return new BCryptPasswordEncoder();
}
```

```text
회원가입 요청 → rawPassword 원문 수신
→ passwordEncoder.encode(rawPassword) → 단방향 해시 생성(복호화 불가)
→ DB에는 해시만 저장, 원문은 어디에도 남지 않음
→ 로그인 시 입력값을 다시 해시하지 않고 matches()로 저장된 해시와 직접 비교(Day23에서 사용)
```

| 구분 | 해싱 | 암호화 |
|---|---|---|
| 방향 | 단방향(복호화 불가) | 양방향(키로 복호화 가능) |
| 이 프로젝트의 용도 | 비밀번호 저장·검증 | 사용 안 함 |
| 필요한 이유 | 로그인은 "일치 여부"만 확인하면 되고 원문 복원이 필요 없음 | 통신·파일처럼 나중에 원문으로 되돌려야 하는 데이터에 씀 |

비밀번호를 암호화로 저장하면 그 키가 유출되는 순간 모든 비밀번호가 한 번에 복호화된다. 해싱은 애초에 복호화 경로 자체가 없어 이 위험이 구조적으로 없다 — 다만 이건 설계 판단이지 오늘 직접 재현해 비교한 실험은 아니다(미검증).

### 2) BCrypt의 salt와 cost factor

> **BCrypt** = Blowfish 기반 적응형(adaptive) 해시 함수. 매 호출마다 랜덤 salt를 섞고, cost factor(strength)만큼 반복 적용해 계산 비용을 의도적으로 늘린다.

우리 코드에서는 `PasswordEncoderTest`가 이 두 가지를 직접 확인한다.

```java
String firstHash = passwordEncoder.encode(rawPassword);
String secondHash = passwordEncoder.encode(rawPassword);
assertNotEquals(firstHash, secondHash);          // salt가 매번 다름
assertTrue(hash.startsWith("$2a$10$"));          // 버전 2a + strength 기본값 10
```

```text
encode(rawPassword) 호출
→ 매번 새 salt(랜덤값) 생성
→ salt + rawPassword를 cost factor(기본 10, 2^10회)만큼 반복 변환
→ "$2a$10$<salt><hash>" 형태의 문자열 1개 반환
```

![bcrypt(cost, salt, pwd) 의사코드 다이어그램. EksBlowfishSetup으로 초기 state를 만든 뒤 EncryptECB를 64회 반복하고, cost·salt·ctext를 이어붙여 최종 해시 문자열을 만드는 단계가 순서대로 표시되어 있다.](https://raw.githubusercontent.com/enderpawar/Developer-Roadmap_Spring_Study/master/app/study_docs/assets/day22-web-bcrypt-phases.png)

*출처: [Hashing in Action: Understanding bcrypt](https://auth0.com/blog/hashing-in-action-understanding-bcrypt/) — Dan Arias, Auth0(Okta) 블로그*

cost factor는 하드웨어가 빨라져도 계속 방어력을 유지하기 위한 장치다. strength 숫자 하나만 올리면(예: 10 → 12) 해시 1회 계산 시간이 늘어나 무차별 대입에 걸리는 시간도 같이 늘어난다 — "적응형"이라는 이름이 여기서 나온다. 오늘 확인한 범위는 strength 기본값(10)과 salt 무작위성뿐이고, strength를 직접 바꿔 계산 시간 차이를 측정하지는 않았다(미검증).

### 3) 용어 한줄뜻

| 용어 | 한줄뜻 |
|---|---|
| BCrypt | Blowfish 기반 적응형 해시 함수, cost factor로 계산량 조절 |
| Salt | 해시마다 무작위로 섞어 같은 원문도 다른 해시를 만드는 값 |
| Strength(cost factor) | salt를 `2^strength`번 반복 적용하는 계산 비용 값 |
| `PasswordEncoder` | Spring Security의 해시·검증 인터페이스(`encode`/`matches`) |

> **더 볼 것**
> - [Spring Security Reference — Password Storage](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html): `PasswordEncoder` 구현체 비교와 권장 사항
> - [Auth0 — Hashing in Action: Understanding bcrypt](https://auth0.com/blog/hashing-in-action-understanding-bcrypt/): bcrypt 알고리즘 내부 단계

## 2. 코드 구현

### 1) 회원가입 흐름 — PasswordEncoder Bean과 예외 처리

`AuthService.signup()`이 중복 확인 → 해시 → 저장을 한 트랜잭션으로 묶는다.

```java
@Transactional
public Member signup(String loginId, String rawPassword, String name) {
    if (memberRepository.existsByLoginId(loginId)) {
        throw new DuplicateLoginIdException(loginId);
    }
    String hashedPassword = passwordEncoder.encode(rawPassword);
    Member member = new Member(name, loginId, hashedPassword);
    return memberRepository.save(member);
}
```

**한 줄씩 보기**

- `@Transactional`: 중복 확인과 저장을 하나의 성공/취소 단위로 묶음(Week C에서 다룬 트랜잭션 경계).
- `existsByLoginId(loginId)`: Spring Data 메서드 이름 파생 쿼리 — 존재 여부만 확인.
- `passwordEncoder.encode(rawPassword)`: 여기서만 원문이 잠깐 메모리에 존재하고, 이 줄 이후로는 해시만 남는다.
- `DuplicateLoginIdException`: `GlobalExceptionHandler`에 새로 등록한 핸들러가 409로 매핑한다.

```java
@ExceptionHandler(DuplicateLoginIdException.class)
public ResponseEntity<Map<String, String>> handleDuplicateLoginId(DuplicateLoginIdException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
}
```

`SignupResponse`는 `id`·`loginId`·`name`만 돌려주고 `password`(해시라도) 필드 자체가 없다 — 응답 DTO를 Domain과 분리해 둔 덕분에(Day02 패턴) 실수로 해시를 노출할 여지가 구조적으로 없다.

### 2) 스키마 설계 판단 — nullable 컬럼

```sql
-- Day22: 로그인/비밀번호 저장을 위한 컬럼 추가.
-- 기존 Member(name)만 있던 로우(테스트 픽스처 등)와 호환되도록 NULL을 허용한다.
ALTER TABLE member ADD login_id VARCHAR(50);
ALTER TABLE member ADD password VARCHAR(100);
ALTER TABLE member ADD CONSTRAINT uq_member_login_id UNIQUE (login_id);
```

처음에는 "비밀번호 컬럼은 `NOT NULL`이어야 안전하다"고만 생각했다. 그런데 `NPlusOneTest`처럼 `new Member(name)`으로 로그인 계정 없이 저장하는 기존 테스트 픽스처가 이미 여러 곳에 있어서, `NOT NULL`을 걸면 그 테스트들이 전부 깨진다. Day19에서 `default` 메서드로 대조군 구현체를 안 건드렸던 것과 같은 방향으로, 이번엔 컬럼을 nullable로 남겨 기존 호환을 우선했다. `UNIQUE` 제약은 걸었으니 "로그인 계정이 있다면 아이디는 중복될 수 없다"는 보장은 유지된다.

### 3) 자동 검증 결과

| 검증 항목 | 방법 | 결과 |
|---|---|---|
| BCrypt 해시 동작(salt·접두어·matches) | `PasswordEncoderTest` 3건(신규) | 3/3 통과 |
| 회원가입 API(성공/중복 409/짧은 비밀번호 400) | `AuthControllerHttpTest` 3건(신규) | 3/3 통과 |
| 회귀 | `./gradlew test` | BUILD SUCCESSFUL, 36/36 |

이번 유닛은 첫 실행부터 컴파일·런타임 오류가 없었다 — 컬럼을 nullable로 설계해 기존 픽스처와 충돌하지 않은 덕분이다. 오류를 지어내지 않고, 실제로 안 났다는 사실을 그대로 남긴다.

커밋: [be3a973](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/commit/be3a973a2411663466c9b043a5aaace1ada8a52d)

## 3. 스스로 답한 질문

### 1) 로그인 계정 컬럼을 NOT NULL로 걸지 않은 이유

**질문.** 비밀번호 컬럼이 `NOT NULL`이 아니면 안전성이 떨어지는 것 아닌가?

**A1.** 처음엔 "`NOT NULL`로 걸어야 안전하다"고만 생각했다. 그런데 마이그레이션을 짜면서, 기존 픽스처가 로그인 계정 없이 저장되는 경로가 이미 코드베이스에 있다는 걸 확인했다. `NOT NULL`을 걸면 안전해지는 게 아니라 기존 테스트가 깨진다. nullable로 두고 "로그인 계정이 없는 회원"을 허용하는 절충을 택했다 — 실제 서비스라면 회원(Member)과 로그인 계정(Account)을 분리하는 설계가 더 맞겠지만, 이 5주 트랙 범위에서는 기존 테스트 호환을 우선했다.

## 4. 학습 정리와 다음 범위

### 1) 이해의 변화와 남은 것

Day15~21에서는 이미 있는 데이터를 트랜잭션·프록시·연관관계로 어떻게 다루는지를 봤다. 오늘은 처음으로 "저장하면 안 되는 값"(원문 비밀번호)을 다뤘고, `PasswordEncoder`가 그 값을 저장 가능한 형태(해시)로 바꾸는 경계라는 걸 확인했다.

**아직 남은 것**은 두 가지다. ① `SignupRequest`는 비밀번호 길이만 검사하고 `loginId` 형식(길이·허용 문자)은 검증하지 않는다 — **나중에 고칠 것**(스코프 밖으로 명시적으로 남김). ② 로그인 계정 없는 회원을 허용하는 nullable 설계는 지금은 기존 테스트 호환을 위한 절충이고, 스키마를 분리하지 않는 이상 계속 남는 한계다 — **고치지 않을 것**(이 트랙 범위 밖).

면접에서 다시 답해볼 항목을 남긴다.

- 비밀번호를 MD5·SHA-256 같은 일반 해시가 아니라 BCrypt로 저장해야 하는 이유
- salt가 없다면 레인보우 테이블 공격이 왜 가능해지는지

---

오늘 공부한 소스코드: `app/src/main/java/com/example/studyroom/config/PasswordEncoderConfig.java`, `app/src/main/java/com/example/studyroom/service/AuthService.java`, `app/src/main/java/com/example/studyroom/controller/AuthController.java`, `app/src/main/resources/db/migration/V5__member_auth.sql`, `app/src/test/java/com/example/studyroom/config/PasswordEncoderTest.java`
