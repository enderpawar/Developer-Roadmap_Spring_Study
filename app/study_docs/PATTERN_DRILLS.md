# 패턴 드릴 — 손으로 채우는 곳

> ## 규칙 (이것만 지키면 됨)
>
> 1. **먼저 쓴다. 자료는 나중에 본다.** 막혀도 최소 1분은 버틴다. 못 떠올려 낑낑댄 시간이 기억을 만든다.
> 2. **다 쓴 뒤에** `CODE_PATTERNS.md` 또는 `src/`와 대조한다. **정답은 이 파일에 없다.**
> 3. **하루 한 묶음이면 충분하다.** 10분짜리다. 한 번에 다 하지 않는다. (묶음 1~3 = Week A, 묶음 4~7 = Week B)
> 4. 틀린 항목은 표시해두고 **다음날 그것만 다시** 쓴다.

**도메인이 예약이 아니다.** 도서 대출(`Loan`)로 낸다 — 예약 코드를 복사하면 안 되고, **구조를 옮겨 써야** 하기 때문이다.

| 대출 도메인 | 대응하는 예약 코드 |
|---|---|
| `Loan` — `bookTitle`, `borrowerName`, `returned`, `id` | `Reservation` |
| `LoanRequest` (요청 DTO) | `ReservationRequest` |
| `LoanNotFoundException` | `ReservationNotFoundException` |
| `LoanRepository` / `InMemoryLoanRepository` | `ReservationRepository` / `InMemory...` |
| `LoanService` · `LoanController` | `ReservationService` · `ReservationController` |
| `loan` 테이블 — `book_title`, `borrower_name`, `returned` | `reservation` 테이블 |
| `JdbcLoanRepository` | `JdbcReservationRepository` |
| `SpringDataLoanRepository` / `JpaLoanRepository` | `SpringDataReservationRepository` / `JpaReservationRepository` |

> 실제 파일로 만들지 않아도 된다. **종이나 빈 메모장에 쓴다.** 컴파일이 목적이 아니라 인출이 목적이다.
> (D9와 독립 과제는 실제로 코드를 만든다.)

---

# 묶음 1 — 요청을 받는 층 (D1~D3)

## D1. Controller (↔ P1)

```java
@_____________ (1)                                    // 이 클래스는 HTTP를 받고 본문을 그대로 돌려준다
public class LoanController {

    private final LoanService loanService;

    // (2) 생성자 주입 — 2줄 직접 작성
    ______________________________________
    ______________________________________
    ______________________________________

    @_____________("/loans") (3)                      // POST로 대출 생성
    public String borrow(@__________ @_______ LoanRequest request) {   // (4) JSON 본문 + 검증
        Loan loan = loanService.borrow(request._________(), request.____________());   // (5)
        return "대출 번호" + loan.getId();
    }

    @PostMapping("/loans/return/____") (6)            // 경로에서 id를 받는다
    public String giveBack(@______________ @Positive(message = "대출 번호는 1 이상이어야 합니다") Long id) {  // (7)
        ...
    }
}
```

**추가 질문:** (6)과 (7) 중 **하나만** 빠뜨리면 어느 시점에 무엇이 터지나?

---

## D2. 요청 DTO (↔ P2)

```java
public ________ LoanRequest(                          // (1) class가 아니다
        @__________(message = "책 제목은 비어있을 수 없습니다") String bookTitle,   // (2)
        @__________(message = "대출자 이름은 비어있을 수 없습니다") String borrowerName
) {}
```

**추가 질문:**
- 이 DTO에서 책 제목을 꺼내는 코드 한 줄을 쓰시오. → `request.____________`
- 위 애노테이션만 달고 Controller에 `@Valid`를 안 붙이면 어떻게 되나?

---

## D3. Domain 클래스 (↔ P3)

```java
public _______ Loan {                                 // (1) record인가 class인가? 왜?
    private _______ String bookTitle;                 // (2) 안 바뀌는 값
    private _______ String borrowerName;
    private boolean returned;
    private Long id;

    public Loan(String bookTitle, String borrowerName) {
        this.bookTitle = bookTitle;
        this.borrowerName = borrowerName;
        this.returned = _______;                      // (3) 대출 직후의 시작 상태
    }

    // (4) 상태를 바꾸는 메서드 — setter 말고 "업무 의미가 있는 이름"으로
    ______________________________________

    public void assignId(Long id) { this.id = id; }
}
```

**추가 질문:** `setReturned(boolean)`를 만들지 않는 이유를 한 문장으로.

---

# 묶음 2 — 실패를 다루는 층 (D4~D6)

## D4. 도메인 예외 (↔ P4)

```java
public class LoanNotFoundException extends _______________ {    // (1) 무엇을 상속?
    public LoanNotFoundException(Long id) {
        _______("대출 내역을 찾을 수 없습니다. (id: " + id + ")");   // (2) 부모 생성자 호출
    }
}
```

**추가 질문:** 이름은 `NotFound`인데 여기 어디에도 `404`가 없다. 404는 누가, 어디서 붙이나?

---

## D5. 전역 예외 처리 (↔ P5)

```java
@___________________ (1)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(______________________________.class)   // (2) @RequestBody @Valid 실패
    public ResponseEntity<Map<String, String>> handleValidation(...) {
        ex.getBindingResult().________________()             // (3) 실패한 필드들만
          .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.status(HttpStatus.____________).body(errors);   // (4)
    }

    @ExceptionHandler(______________________________.class)   // (5) @PathVariable @Positive 실패
    ...

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        log.error("처리하지 못한 예외", ____);                  // (6) 여기에 무엇을 넘겨야 스택트레이스가 남나
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "________________________"));   // (7) 여기에 절대 넣으면 안 되는 것은?
    }
}
```

**추가 질문:** 검증 실패 핸들러가 **두 개** 필요한 이유는? 하나만 만들면 나머지 하나는 몇 번으로 나가나?

---

## D6. Repository — 인터페이스 + 저장 계약 (↔ P6)

```java
public interface LoanRepository {
    Loan save(Loan loan);
    List<Loan> findAll();
    ____________ findById(Long id);                   // (1) "없을 수도 있음"을 반환형에 드러내는 타입
}
```

```java
public class InMemoryLoanRepository implements LoanRepository {
    private final List<Loan> store = new ArrayList<>();
    private long nextId = 1;

    @Override
    public Loan save(Loan loan) {
        if (loan.getId() == ______) {                 // (2) 신규인지 판단하는 조건
            loan.assignId(nextId++);
            store.____(loan);                         // (3)
            return loan;
        }
        for (int i = 0; i < store.size(); i++) {
            if (store.get(i).getId()._________(loan.getId())) {   // (4) == 가 아닌 이유는?
                store.____(i, loan);                  // (5)
                return loan;
            }
        }
        // (6) 여기에 아무것도 안 쓰면 컴파일러가 뭐라고 하나? 무엇을 써야 하나?
        ______________________________________
    }
}
```

---

# 묶음 3 — 조립과 검증 (D7~D9)

## D7. Service (↔ P7)

```java
@________ (1)
public class LoanService {

    private final ______________ loanRepository;      // (2) 구현체 타입인가 인터페이스 타입인가? 왜?

    public LoanService(______________ loanRepository) {
        this.loanRepository = loanRepository;
    }

    public Loan giveBack(Long id) {
        Loan loan = loanRepository.findById(id)
                .________________(() -> new LoanNotFoundException(id));   // (3)
        loan.__________();                            // (4) 상태 변경 — 누가 해야 하나?
        loanRepository.save(loan);
        return loan;
    }
}
```

**추가 질문:** `giveBack`에서 `findById` 없이 `new Loan(...)`으로 시작하면 무슨 일이 나나? (Day04에서 실제로 겪음)

---

## D8. Bean 등록과 조립 (↔ P8)

빈칸이 아니라 **판정 문제**다. 각각 O/X와 **터지는 예외 이름**을 쓰시오.

| # | 상황 | 기동되나? | 예외 이름 |
|---|---|---|---|
| 1 | `LoanService`에서 `@Service`를 뗐다 | | |
| 2 | `LoanRepository`를 구현한 클래스 2개에 모두 `@Repository`가 붙어 있다 | | |
| 3 | 위 2번 상태에서 `./gradlew compileJava`만 실행 | | |
| 4 | `InMemoryLoanRepository`에서 `@Repository`만 떼고 `implements`는 남겼다 — 자바 상속 관계는 깨지나? | | |

**추가 질문:** 2번에서 스프링이 "둘 중 하나를 알아서 고르기"를 하지 않는 이유는?

---

## D9. 테스트 (↔ P9) — **이건 실제 코드로**

아래 표를 채운 뒤, **③을 실제 파일로 작성**하시오.

| 검증하고 싶은 것 | 어떤 테스트? (순수단위 / 핸들러단위 / MockMvc) | 왜 |
|---|---|---|
| ① 반납 후에도 저장소 행이 안 늘어난다 | | |
| ② 예상 밖 예외의 내부 메시지가 응답에 안 새어나간다 | | |
| ③ `POST /loans`에 빈 제목을 보내면 400이 온다 | | |
| ④ `@PathVariable`과 경로 `{id}`가 실제로 짝이 맞는다 | | |

**③ 작성 조건**
- 파일: `src/test/java/.../ReservationControllerHttpTest.java`에 **테스트 메서드 하나 추가**해도 되고, 새 클래스로 만들어도 된다
- 한글 JSON은 **텍스트 블록(`"""`)** 으로
- `./gradlew test`가 green이어야 완료

---

# 묶음 4 — 스키마와 제약 (D10~D11)

## D10. Flyway 마이그레이션 (↔ P10)

대출 테이블을 만드는 첫 마이그레이션이다. **파일 경로부터 쓰시오.**

```
src/main/resources/____________/____________________     (1) 폴더 + 파일명
```

```sql
CREATE TABLE loan (
    id BIGINT NOT NULL ________________,          (2) DB가 번호를 붙이게
    book_title VARCHAR(200) ____________,          (3) 값 부재 거부
    borrower_name VARCHAR(50) NOT NULL,
    returned BOOLEAN NOT NULL ____________ FALSE,  (4) 생략하면 채워질 값
    ____________ (id)                              (5)
);
    ____ 주석은 이 기호로                            (6) # 이 아닌 이유는?
```

**판정 문제** — O/X와 이유:

| # | 상황 | O/X | 무엇이 터지나 |
|---|---|---|---|
| ⓐ | `V1__init.sql`을 이미 적용한 뒤 **주석 한 줄만** 추가하고 재기동 | | |
| ⓑ | 파일명을 `V1_init.sql`로 씀 (언더바 1개) | | |
| ⓒ | 마이그레이션이 실패한 뒤 SQL을 고치고 그냥 재기동 | | |
| ⓓ | ⓐ 상황에서 테스트 6개가 깨졌다 — 원인이 6개인가? | | |

---

## D11. DB 제약 vs 앱 검증 (↔ P11)

표를 채우시오.

| 입력 | `@NotBlank` (앱) | `NOT NULL` (DB) |
|---|---|---|
| `null` | | |
| `""` (빈 문자열) | | |
| `"   "` (공백만) | | |
| H2 콘솔로 직접 INSERT | | |

**추가 질문**
- 위 표에서 **둘 다 못 막는 칸**은 없다. 하지만 **각자 못 막는 칸**이 있다. 각각 무엇인가?
- `''`까지 DB에서 막으려면 무엇이 필요한가?
- PK가 1, 2, 5, 6으로 생겼다. 버그인가? `AUTO_INCREMENT`가 보장하는 것은 정확히 무엇인가?
- 소문자로 만든 테이블을 `table_name='loan'`으로 조회했더니 0행이다. 왜인가?

---

# 묶음 5 — JDBC (D12~D14)

## D12. JDBC로 저장하기 (↔ P12)

```java
@Repository
public class JdbcLoanRepository implements LoanRepository {

    private final ____________ dataSource;          // (1) 커넥션 풀을 감싼 표준 타입

    // 생성자 생략

    @Override
    public Loan save(Loan loan) {
        String sql = "INSERT INTO loan (book_title, borrower_name, returned) VALUES (_,_,_)";  // (2)

        try (Connection con = dataSource.________________();                     // (3)
             PreparedStatement ps = con.prepareStatement(sql, Statement.____________________)) {  // (4) id를 돌려받으려면

            ps.setString(__, loan.getBookTitle());   // (5) 첫 번째 자리의 번호는?
            ps.setString(__, loan.getBorrowerName());
            ps.setBoolean(__, loan.isReturned());
            ps.________________();                   // (6) INSERT 실행

            try (ResultSet keys = ps.____________________()) {   // (7)
                if (keys.next()) { loan.assignId(keys.getLong(1)); }
            }
            return loan;

        } catch (____________ e) {                   // (8) 이 예외를 안 잡으면 어떻게 되나?
            throw new IllegalStateException("대출 저장 실패", __);   // (9) 여기 빠뜨리면 뭘 잃나?
        }
    }
}
```

**추가 질문**
- (2)에서 값을 문자열로 이어붙이지 않고 `?`를 쓰는 이유를 **공격 이름까지 넣어** 한 문장으로.
- `try-with-resources`를 안 쓰고 커넥션을 반납하지 않으면 결국 무슨 일이 나나?
- 이 `save()`는 **계약의 절반만** 지키고 있다. 빠진 절반은 무엇이고, 그게 없으면 취소/반납 때 무슨 일이 나나?

---

## D13. `ResultSet` → 객체 매핑 (↔ P13)

```java
private Loan mapRow(ResultSet rs) throws SQLException {
    Loan loan = new Loan(rs.getString(________), rs.getString(__________));  // (1) 따옴표 필요한가?
    loan.assignId(rs.__________("id"));                                       // (2) id 타입에 맞는 getter
    if (rs.getBoolean("returned")) {
        loan.__________();                                                    // (3) 도메인 메서드로
    }
    return loan;
}
```

**추가 질문**
- `rs.next()`와 `rs.getBoolean("returned")`는 각각 커서에 무슨 일을 하는가?
- `mapRow` **안에서** `rs.next()`를 부르면 결과가 어떻게 망가지나? 예외는 나는가?
- `mapRow`가 왜 **3단계**로 나뉘나? 생성자 하나로 끝낼 수 없는 이유는? (힌트: D3에서 만든 캡슐화)

---

## D14. 테스트 환경 격리 (↔ P14)

설정 우선순위를 **높은 것부터** 나열하시오.

```
____________ > ____________ > application-{profile}.yml > ____________
```

**판정 문제**

| # | 상황 | 어떻게 되나 |
|---|---|---|
| ⓐ | `application.yml`엔 H2를 썼는데 OS 환경변수에 `SPRING_DATASOURCE_URL=jdbc:postgresql://운영서버/...`가 있다 | |
| ⓑ | ⓐ 상태에서 `SPRING_PROFILES_ACTIVE=prod`이고 `flyway.enabled: true`이며 **PostgreSQL 드라이버가 의존성에 있었다면?** | |
| ⓒ | `SPRING_DATASOURCE_URL`은 어떤 프로퍼티 이름으로 변환되나 | |

**추가 질문:** `build.gradle.kts`의 `test` 태스크에 환경변수를 고정하는 것이 "임시방편"이 아니라 **원래 있어야 할 것**인 이유는?

---

# 묶음 6 — Entity와 Spring Data JPA (D15~D17)

## D15. Spring Data 어댑터 (↔ P15)

기존 `LoanService`는 `LoanRepository`에 계속 의존해야 한다. 빈칸을 채워 Spring Data를 기존 경계 뒤에 연결하시오.

```java
public ____________ SpringDataLoanRepository
        ____________ JpaRepository<Loan, ____________> {
}
```

```java
@____________
public class JpaLoanRepository ____________ LoanRepository {

    private final ______________________________ delegate;

    public JpaLoanRepository(________________________________ delegate) {
        this.delegate = delegate;
    }

    @Override
    public Loan save(Loan loan) {
        return ______________________________;
    }

    @Override
    public Optional<Loan> findById(Long id) {
        return ______________________________;
    }

    @Override
    public List<Loan> findAll() {
        return ______________________________;
    }
}
```

**관계 화살표를 직접 그리시오.**

```text
LoanService  ______>  LoanRepository
JpaLoanRepository  ______>  LoanRepository
JpaLoanRepository  ______>  SpringDataLoanRepository
SpringDataLoanRepository  ______>  JpaRepository<Loan, Long>
```

**추가 질문**
- `SpringDataLoanRepository`가 `class`가 아니라 `interface`여야 하는 이유는?
- `LoanService`가 Spring Data 타입에 직접 의존하지 않게 어댑터를 둔 이유는?
- 두 Repository 구현체에 모두 `@Repository`를 붙이면 기동 시 무엇을 기준으로 실패하는가?

---

## D16. JPA Entity 매핑 (↔ P16)

도서 대출 Domain을 Entity로 매핑하시오. 정상 생성 규칙과 JPA 복원 경로를 둘 다 남겨야 한다.

```java
@____________
public class Loan {
    private String bookTitle;
    private String borrowerName;
    private boolean returned;

    @____________
    @____________(strategy = GenerationType.____________)
    private Long id;

    public Loan(String bookTitle, String borrowerName) {
        this.bookTitle = bookTitle;
        this.borrowerName = borrowerName;
        this.returned = false;
    }

    ____________ Loan() {
    }
}
```

**판정 문제**

| # | 상황 | 컴파일/실행 예측 | 이유 |
|---|---|---|---|
| ⓐ | `bookTitle`, `borrowerName`을 `final`로 두고 위의 빈 생성자를 유지 | | |
| ⓑ | `@Id`를 `id` 필드가 아니라 `assignId()` 위에 둠 | | |
| ⓒ | `@Id`는 필드에, 다른 매핑 애노테이션은 getter에 섞음 | | |
| ⓓ | Flyway가 테이블을 관리하는데 `ddl-auto: create`를 사용 | | |

**추가 질문:** `protected Loan()`은 누구에게는 열려 있고 누구에게는 감춰지는가? 이 가시성이 Domain 생성 규칙과 어떤 관계인가?

---

## D17. JPA 저장 계약 통합 테스트 (↔ P17)

새 대출을 저장한 뒤 같은 ID의 반납 상태를 갱신한다. **UPDATE 로그만 보는 것으로 끝내지 말고 중복 행도 검사**하시오.

```java
@____________
@____________
class JpaLoanRepositoryTest {

    @Autowired LoanRepository repository;
    @Autowired EntityManager entityManager;

    @Test
    void savingExistingLoanUpdatesWithoutAddingDuplicate() {
        int countBeforeSave = repository.____________().size();
        Loan saved = repository.____________(new Loan("운영체제", "jinwoo"));
        entityManager.____________();

        saved.returnBook();
        repository.____________(saved);
        entityManager.____________();
        entityManager.____________();

        List<Loan> all = repository.____________();
        List<Loan> sameIdRows = all.stream()
                .filter(candidate -> ____________________________________________)
                .toList();

        assertEquals(____________________, all.size());
        assertEquals(____, sameIdRows.size());
        assertTrue(________________________________);
    }
}
```

**실행 전 순서를 적으시오.**

```text
신규 save → ________ → 기존 객체 상태 변경 → 기존 save → ________ → ________ → findAll
```

**추가 질문**
- 첫 번째 `flush()`와 두 번째 `flush()`가 각각 어떤 SQL을 관찰 가능하게 만드는가?
- `clear()` 없이 조회하면 테스트가 DB가 아니라 무엇을 보고 통과할 수 있는가?
- 전체 행 수를 `1`로 고정하지 않고 저장 전 행 수를 기준으로 검사하는 이유는?

---

# 묶음 7 — 영속성 컨텍스트와 부채 상환 (D18~D21)

## D18. 1차 캐시 (↔ P18)

```java
@Test
void repeatedFindByIdWithinSameTransactionReturnsSameInstance() {
    Loan loan = new Loan("자료구조", "민지");
    Loan saved = repository.save(loan);
    entityManager.____________();                        // (1) 대기 중인 INSERT를 DB로 내보낸다
    Long id = saved.getId();

    entityManager.____________();                         // (2) 1차 캐시를 비운다

    Loan first = repository.findById(id).____________();  // (3) Optional을 풀어내는 메서드
    Loan second = repository.findById(id).orElseThrow();

    assert____________(first, second);                    // (4) 값이 아니라 참조를 비교
}
```

**판정 문제**

| # | 상황 | SELECT 횟수 | `first == second`? |
|---|---|---|---|
| ⓐ | (2)를 지우고 바로 두 번 조회 | | |
| ⓑ | 위 코드 그대로(`clear()` 있음) | | |

**추가 질문:** ⓐ에서 SELECT가 예상보다 적게 나가는 이유를, `save()`가 반환한 객체의 상태와 연결해서 설명하시오.

---

## D19. 변경 감지 — Dirty Checking (↔ P19)

```java
@Test
void modifyingManagedLoanWithoutExplicitSaveStillPersistsOnFlush() {
    Loan loan = new Loan("자료구조", "민지");
    loan.____________();                                  // (1) 로드 스냅샷을 무엇으로 만들지 먼저 판단해서 채우기
    Loan saved = repository.save(loan);
    entityManager.flush();
    Long id = saved.getId();
    Loan managed = repository.findById(id).orElseThrow();

    managed.____________();                               // (2) save() 호출 없이 상태만 바꾼다

    entityManager.____________();                          // (3) 이 시점에 dirty checking이 UPDATE를 만든다

    entityManager.clear();
    Loan reloaded = repository.findById(id).orElseThrow();

    assert____________(reloaded.isReturned());             // (4) (1)·(2)에 무엇을 넣었는지에 달렸다
}
```

**판정 문제** — 아래 두 시나리오는 "아무것도 증명 못 하는 테스트"다. 각각 왜인지 쓰시오.

| 시나리오 | 왜 증명이 안 되나 |
|---|---|
| ⓐ (1)을 안 부르고 저장 → (2)만 호출 | |
| ⓑ (1)과 (2)가 서로 반대 효과라 최종값이 로드 시점과 같아짐 | |

**추가 질문:** dirty checking이 비교하는 두 값은 정확히 무엇과 무엇인가?

---

## D20. `CHECK` 제약 (↔ P20)

```sql
-- V2__add_loan_check_constraints.sql
ALTER TABLE loan
    ADD CONSTRAINT book_title CHECK (____________);        -- (1) 조건식

ALTER TABLE loan
    ADD CONSTRAINT borrower_name CHECK (____________);     -- (2)
```

**판정 문제** — 아래 세 시도는 각각 왜 안 되는지 쓰시오.

| 시도 | 왜 안 되나 |
|---|---|
| `ALTER TABLE loan(ADD CONSTRAINT ... CHECK (...))` — 전체를 괄호로 묶음 | |
| `ADD CONSTRAINT book_title CHECK NOT BLANK` | |
| 조건식에 비교 대상 컬럼 없이 연산자와 빈 문자열만 씀 | |

```java
@Test
void checkConstraintRejectsEmptyBookTitle() {
    assert____________(____________________.class, () -> {   // (3) assert 메서드 + 예외 타입
        entityManager.createNativeQuery(
                "INSERT INTO loan (book_title, borrower_name, returned) VALUES ('', ?, false)")
                .setParameter(1, "민지")
                .executeUpdate();
        entityManager.flush();
    });
}
```

**추가 질문:** `ddl-auto: validate`가 이 `CHECK` 제약이 실제로 있는지까지 검사해주는가? 검사한다면 무엇을 보는지, 안 한다면 왜 못 잡는지 설명하시오.

---

## D21. 메서드 시그니처 변경의 파급 (↔ P21)

**판정 문제** — `Loan`의 `returnBook()`을 `returnBook(String condition)`(반납 시 도서 상태)으로 바꾼다고 하자. 아래 각 위치가 컴파일되는지(O/X)와, 안 된다면 어떤 메시지가 뜰지 쓰시오.

| # | 위치 | 코드 | O/X | 메시지(예상) |
|---|---|---|---|---|
| ① | `LoanService` | `loan.returnBook();` (그대로) | | |
| ② | `LoanController` | `loanService.giveBack(id);` (그대로) | | |
| ③ | 기존 테스트 | `saved.returnBook();` (그대로) | | |

```java
// LoanController — condition을 어디서 받을지 결정해서 채우시오
@PostMapping("/loans/return/{id}")
public String giveBack(@PathVariable @Positive(message = "...") Long id,
                        @____________ @____________(message = "...") String condition) {   // (1)(2)
    ...
}
```

**추가 질문**
- `condition`을 `@PathVariable`로 선언했는데 URL에 `{condition}` 자리가 없다면 무슨 일이 나는가?
- 문자열이 비어있지 않은지 검사하는 애노테이션과 숫자가 양수인지 검사하는 애노테이션을 혼동하면, 그건 컴파일 오류인가 런타임 오류인가?
- 시그니처를 바꾸는 대신 오버로드를 택했다면, 어떤 도메인 규칙일 때 그 선택이 더 나은가?

---

# 묶음 8 — 트랜잭션·프록시·Fetch 전략 (D22~D26)

## D22. 트랜잭션 경계 (↔ P22)

```java
@____________
public Loan returnBook(Long id) {
    Loan loan = loanRepository.findById(id)
            .orElseThrow(() -> new LoanNotFoundException(id));
    loan.____________();                     // (1) save() 없이 반영되려면 무엇을 호출해야 하나
    return loan;
}
```

**추가 질문:** 이 메서드에 `readOnly = true`를 붙이면 안 되는 이유는?

---

## D23. 트랜잭션 전파 (↔ P23)

**판정 문제** — `LoanService.returnThenFail()`(`@Transactional`, 저장 후 예외)이 `PenaltyService.charge()`를 호출한다. 아래 두 경우 각각 `charge()`가 만든 연체료 기록이 살아남는지(O/X) 쓰시오.

| # | `charge()`의 propagation | 살아남는가(O/X) |
|---|---|---|
| ① | 미지정(기본값) | |
| ② | `REQUIRES_NEW` | |

**추가 질문:** ②의 경우 동시에 몇 개의 DB 커넥션이 필요하며, 풀 크기보다 동시 요청이 많아지면 무슨 일이 생기는가?

---

## D24. 연관관계 + LAZY 프록시 (↔ P24)

```java
// Loan(N) → Book(1)
@____________(fetch = FetchType.____________)
@____________(name = "____________")
private Book book;

// Book → List<Loan> (역방향)
@____________(mappedBy = "____________", fetch = FetchType.____________)
private List<Loan> loans = new ____________<>();
```

**판정 문제** — `loan.getBook().getClass()`를 재조회 직후 호출하면 정확히 `Book`으로 나오는가? `loan.getBook()`(단일 참조)와 `book.getLoans()`(컬렉션)를 각각 미초기화 상태로 조회했을 때 런타임 타입은 같은가, 다른가?

---

## D25. N+1과 fetch join (↔ P25)

```java
@Query("select l from Loan l ____________ ____________ l.book")   // (1) N+1을 없애는 키워드 두 개
List<Loan> findAllWithBook();
```

```java
@Test
void findAllWithBookUsesSingleJoinQuery() {
    Statistics statistics = entityManager.getEntityManagerFactory()
            .____________(SessionFactory.class).getStatistics();     // (2)
    statistics.clear();
    loanRepository.findAllWithBook();
    assertEquals(____________, statistics.getPrepareStatementCount()); // (3) 기대값
}
```

**추가 질문:** `findAllWithBook()`을 추가했더니 `LoanRepository`를 구현하는 대조군 클래스가 컴파일 에러를 냈다. 시그니처를 그대로 두고 컴파일만 통과시키는 방법은?

---

## D26. Inner Join Fetch vs Left Join Fetch (↔ P26)

**판정 문제** — `book_id`가 `null`인(분실 처리된) `Loan`이 있다고 하자. 아래 두 쿼리를 각각 실행하면 그 행이 결과에 포함되는가(O/X)?

| # | 쿼리 | 포함되는가(O/X) |
|---|---|---|
| ① | `select l from Loan l join fetch l.book` | |
| ② | `select l from Loan l left join fetch l.book` | |

```java
@____________(readOnly = ____________)                    // (1)(2)
public List<LoanSummary> findAllSummaries() {
    return loanRepository.____________().stream()          // (3) 어느 쿼리 메서드를 써야 하나
            .map(l -> new LoanSummary(l.getBookTitle(),
                    l.getBook() == null ? null : l.getBook().getTitle()))
            .toList();
}
```

**추가 질문:** 기존 `join fetch` 메서드를 `left join`으로 그냥 바꾸지 않고 새 메서드를 추가하는 이유는 언제 정당화되는가?

---

# 묶음 9 — 인증·테스트 전략 (D27~D32)

## D27. PasswordEncoder Bean과 가입 (↔ P27)

Loan 도메인에 `Librarian`(사서) 로그인 계정을 추가한다고 하자.

```java
@Configuration
public class LibrarianPasswordEncoderConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new ____________________();          // (1) 어떤 구현체를 쓸 것인가
    }
}

@Transactional
public Librarian signup(String loginId, String rawPassword, String name) {
    if (librarianRepository.____________________(loginId)) {   // (2) 중복 확인 메서드 이름
        throw new DuplicateLoginIdException(loginId);
    }
    String hashed = passwordEncoder.____________________(rawPassword); // (3) 해시 메서드
    Librarian librarian = new Librarian(name, loginId, hashed);
    return librarianRepository.save(librarian);
}
```

**판정 문제** — 같은 `rawPassword`로 `signup()`을 두 서로 다른 `loginId`로 두 번 호출하면, 저장된 두 `password` 컬럼 값은 같은가 다른가? 이유를 한 문장으로 쓰시오.

**추가 질문**
- `login_id`/`password` 컬럼을 `NOT NULL`로 걸면 안 되는 기존 코드 상황을 하나 드시오.
- `PasswordEncoder`를 인터페이스로 주입받는 것과 `BCryptPasswordEncoder`를 직접 타입으로 주입받는 것의 차이는?

---

## D28. JwtProvider 발급·검증 (↔ P28)

```java
public class LibrarianJwtProvider {
    private final SecretKey key;

    public LibrarianJwtProvider(String secret) {
        this.key = Keys.____________________(secret.getBytes(StandardCharsets.UTF_8)); // (1)
    }

    public String issue(String subject) {
        return Jwts.builder().____________________(subject)   // (2) 표준 클레임 이름
                .signWith(key).compact();
    }

    public String parseSubject(String token) {
        return Jwts.parser().____________________(key).build()  // (3) 검증용 메서드
                .parseSignedClaims(token).getPayload().getSubject();
    }
}
```

**판정 문제** — secret이 `"lib-secret"`(10바이트)이면 위 생성자 호출 시 무슨 일이 일어나는가? 예외 이름과, 어느 줄에서 발생하는지 쓰시오.

**추가 질문**
- 사서 `loginId`가 존재하지 않을 때와 비밀번호가 틀렸을 때, 같은 응답으로 합쳐야 하는 이유는?
- HS256 대신 RS256을 쓴다면 `key` 필드의 타입과 발급/검증 코드가 어떻게 달라지는가?

---

## D29. SecurityFilterChain과 필터/MVC 예외처리 경계 (↔ P29)

Loan 대출 승인 API에 Security를 추가한다. `/loans/**`는 인증 필요, `POST /loans/{id}/writeoff`(대손상각)는 `ROLE_MANAGER`만 가능해야 한다.

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers(HttpMethod.POST, "/loans/____________/writeoff").hasRole("____________")
        .anyRequest().authenticated())
```

**추가 질문**
- 대출 담당자 본인이 등록한 대출만 조회 가능하게 하려면, 서비스 코드에서 `AccessDeniedException`을 던지는 방식이 위험한 이유를 한 문장으로 쓰시오.

---

## D30. JWT 검증 실패 — 하위타입 전부를 한 절로 포괄 (↔ P30)

`LoanAuthenticationFilter`가 만료된 토큰을 받았을 때 던져지는 예외의 상위 타입은 ________이고, 이를 잡아서 해야 할 일은 `SecurityContextHolder.________()`이다.

**판정 문제** — 위조된 서명과 만료를 같은 `catch` 절 하나로 처리해도 되는 이유는 두 예외가 어떤 관계이기 때문인가?

**추가 질문:** 이 필터가 401을 직접 만들지 않고 흘려보내면, 실제 401은 어느 컴포넌트가 만드는가?

---

## D31. 테스트 슬라이스 분류 (↔ P31)

Loan 도메인에 `LoanController`용 슬라이스 테스트를 추가한다고 하자.

```java
@____________(LoanController.class)      // (1) Controller 슬라이스 애노테이션
@____________(addFilters = ____________)  // (2)(3) Security 필터를 끄는 옵션
class LoanControllerWebMvcTest {
    @Autowired private MockMvc mockMvc;
    @____________ private LoanService loanService; // (4) Service를 대역으로 교체
}
```

**판정 문제** — `addFilters=false`를 빼고 `GET /loans`(인증 필요 경로)를 200으로 기대하면 실제로는 몇 번이 나오는가? 그 이유를 한 문장으로 쓰시오.

**추가 질문**
- `@DataJpaTest`로 `LoanRepository`를 검증할 때, Flyway 마이그레이션이 적용되는가 안 되는가?
- Slice Test의 분류 기준이 "실제 DB 사용 여부"가 아니라 무엇인지 한 문장으로 쓰시오.

---

## D32. 오류 응답 일관성 (↔ P32)

```java
@ExceptionHandler(LoanOverdueException.class)
public ResponseEntity<Map<String, Object>> handleOverdue(LoanOverdueException ex) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("error", ex.getMessage());
    body.put("____________", "____________");   // (1)(2) 클라이언트 분기용 안정 식별자, 값
    body.put("____________", Instant.now().toString()); // (3) 발생 시각 필드명
    return ResponseEntity.status(HttpStatus.____________).body(body); // (4) 상태 코드
}
```

**판정 문제** — 기존에 `{"error": "..."}`만 반환하던 테스트의 `jsonPath("$.error")` 단언은, 위처럼 필드를 "추가"만 했을 때 깨지는가 안 깨지는가?

**추가 질문**
- 필터 단계에서 문자열을 직접 조합해 응답하는 컴포넌트와, `@RestControllerAdvice`가 처리 못 하는 이유를 한 문장으로 쓰시오.
- 오류 응답 형식을 통일할 때, "범위를 부채 항목만큼으로 좁힌다"가 실제로 막아주는 것은 무엇인가?

---

# 묶음 10 — 운영·디버깅·통합 (D33~D38)

## D33. RequestIdFilter/MDC 요청 추적 (↔ P33)

`LoanRequestIdFilter`가 `OncePerRequestFilter`를 상속해 MDC에 ________을(를) 넣고, ________ 블록에서 반드시 제거해야 하는 이유는? `FilterRegistrationBean`에 ________을(를) 지정해 Security 필터체인보다 먼저 실행되게 해야 하는 이유는?

**추가 질문:** 요청 처리를 별도 스레드(`@Async` 등)로 넘기면 MDC 값은 자동으로 따라가는가?

---

## D34. Profile 분리 + fail-fast 비밀값 관리 (↔ P34)

`LoanService`가 운영에서 쓰는 API 키를 `${LOAN_API_KEY}`로 참조할 때, 값이 없으면 ________된다. 그 이유는 ________이다.

**추가 질문:** 이 방식이 "값이 없으면 기본값으로 조용히 뜨는 것"보다 나은 이유는?

---

## D35. 가설-검증 디버깅 (↔ P35)

`LoanRepaymentService.calculateInterest()`의 반올림 버그를 의심할 때, 재현 테스트를 먼저 ________한 뒤 ________ 로그로 중간값을 비교해 원인을 ________한다. 서로 다른 두 테스트가 동일한 실패 메시지를 내면 이는 ________의 증거가 될 수 있다.

**추가 질문:** 재현 커밋과 수정 커밋을 분리하는 것이 한 커밋에 묶는 것보다 나은 이유는?

---

## D36. 멀티스테이지 Dockerfile + Compose healthcheck (↔ P36)

`loan-api` 이미지의 멀티스테이지 빌드에서 1단계는 ________만 갖고, 2단계는 ________만 남긴다. `compose.yaml`에서 `loan-db`에 healthcheck를 걸고 `loan-api`가 ________ 조건으로 대기해야 하는 이유는?

**추가 질문:** `depends_on`만 쓰고 healthcheck 조건을 주지 않으면 왜 위험한가?

---

## D37. CI Job 의존과 실제 실행 검증 (↔ P37)

`loan-ci.yml`에서 `docker` 잡이 `test` 잡의 ________을(를) 기다리게 하려면 ________ 키워드를 쓴다. 로컬 H2 호환 모드에서 통과한 테스트가 실제 ________에서 실패할 수 있는 이유는?

**추가 질문:** CI에서 실패 시 `docker compose logs`를 남기는 것이 왜 필요한가?

---

## D38. 겹침 판정 공식 (↔ P38)

같은 책(`Loan.bookTitle`)을 두 사람이 겹치는 기간(`borrowedAt`~`dueAt`) 동안 대출할 수 없게 만든다.

```java
@Query("select l from Loan l where l.bookTitle = :bookTitle and l.returned = false " +
        "and l.borrowedAt is not null and l.dueAt is not null " +
        "and ______________________ and ______________________")  // (1) 겹침 부등호 두 조건, 등호 없이
List<Loan> findOverlapping(String bookTitle, LocalDateTime borrowedAt, LocalDateTime dueAt);
```

- (2) 이 쿼리가 반납된(`returned = true`) 대출을 제외해야 하는 이유를 한 문장으로:
  ______________________________________________________________
- (3) 대출 A가 3/1~3/5, 대출 B가 3/5~3/10이면 겹치는가? 근거도 함께:
  ______________________________________________________________

**완료 판정**
- [ ] `InMemoryLoanRepository`에도 같은 판정을 순수 자바로 재현
- [ ] "맞닿는 기간"을 검증하는 테스트 1개 추가
- [ ] 기존 대출 테스트가 **여전히** 통과(회귀 없음)

---

# 독립 과제 — 0층부터 (자료 안 보고)

드릴을 다 채운 뒤에 한다. **실제 코드로 작성하고, 판정 기준을 통과해야 완료다.**

## 과제 A — 목록 조회 엔드포인트

`GET /reservations`로 전체 예약 목록을 반환한다.

- 관통하는 층: Controller → Service → Repository
- `findAll()`은 Repository에 이미 있다. **Service와 Controller만** 새로 뚫으면 된다
- 반환 형태는 본인이 정한다 (문자열 / DTO 리스트 / `ResponseEntity`)

**완료 판정**
- [ ] `./gradlew test` green
- [ ] 앱 실행 후 실제 `GET /reservations` 호출 → 200과 목록 확인
- [ ] Controller에 업무 규칙(`if` 판단)이 **없다**

---

## 과제 B — 새 도메인 예외를 409로

"이미 취소된 예약을 또 취소하면" 거절한다.

- `AlreadyCancelledException`(이름은 자유) 추가
- `ReservationService.cancel()`에서 `confirmed == false`면 던진다
- 전역 처리기에 핸들러를 추가해 **409 Conflict**로 매핑

**완료 판정**
- [ ] 핸들러 단위 테스트 1개 작성 → 409와 응답 본문 확인
- [ ] `./gradlew test` green
- [ ] 예외 클래스 안에 `HttpStatus`가 **없다** (도메인은 HTTP를 모른다)

---

## 과제 C — DTO에 제약 하나 추가

`ReservationRequest.roomName`에 길이 제한을 건다 (예: 최대 20자).

- 적절한 Bean Validation 애노테이션을 **직접 찾아서** 적용
- 메시지도 한글로

**완료 판정**
- [ ] MockMvc 테스트 1개 추가 → 21자 이상 전송 시 400 + 해당 메시지
- [ ] `./gradlew test` green
- [ ] 기존 테스트 4개가 **여전히** 통과 (회귀 없음)

---

# 채점 기록

틀린 것만 표시하고, **다음날 그것만 다시** 푼다.

| 묶음 | 범위 | 날짜 | 틀린 항목 | 재시험일(+1) | 통과 |
|---|---|---|---|---|---|
| 1 | Week A · D1~D3 (Controller·DTO·Domain) | | | | |
| 2 | Week A · D4~D6 (예외·전역처리·Repository) | | | | |
| 3 | Week A · D7~D9 (Service·DI·테스트) | | | | |
| 4 | Week B · D10~D11 (Flyway·제약) | | | | |
| 5 | Week B · D12~D14 (JDBC·매핑·환경격리) | | | | |
| 6 | Week B · D15~D17 (JPA 어댑터·Entity·통합 테스트) | | | | |
| 7 | Week B · D18~D21 (1차 캐시·변경 감지·CHECK·시그니처 파급) | | | | |
| 8 | Week C · D22~D26 (트랜잭션·전파·LAZY 프록시·N+1·fetch join) | | | | |
| 9 | Week D · D27~D32 (해싱·JWT·필터체인·테스트 슬라이스·오류 응답) | | | | |
| 10 | Week E · D33~D38 (로깅·설정·디버깅·Docker·CI·겹침 검사) | | | | |
| 과제 A | 목록 조회 0층부터 | | | | |
| 과제 B | 새 도메인 예외 → 409 | | | | |
| 과제 C | DTO 제약 추가 | | | | |

> 3회 연속 틀린 항목은 **설명형이 아니라 판정형**으로 바꿔 낸다 (`final` 문항에서 효과를 본 방식).
