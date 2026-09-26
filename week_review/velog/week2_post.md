# [백엔드 스터디 2주차] Map 저장소에서 JPA와 MySQL로 전환하기

> 1주차에는 `Map`에 Todo를 저장하면서 Controller, Service, Repository 흐름을 익혔다. 이번 주에는 그 저장 공간을 실제 MySQL로 바꿔봤다. JPA 코드도 처음 제대로 다뤘지만, 솔직히 가장 오래 붙잡고 있었던 건 CRUD보다 MySQL 설치와 실행 설정이었다.

## 1. Map에서 MySQL로 바꾸기

2주차에는 StudyRoom 도메인으로 CRUD API를 만들었다.

```http
POST   /api/study-rooms
GET    /api/study-rooms
GET    /api/study-rooms/{id}
PATCH  /api/study-rooms/{id}
DELETE /api/study-rooms/{id}
```

1주차와 비교하면 Controller와 Service의 흐름은 거의 그대로다. 가장 크게 바뀐 부분은 Repository 뒤쪽이었다.

```text
1주차
Controller → Service → Repository → Map

2주차
Controller → Service → JpaRepository → JPA → MySQL
```

1주차에는 `save()`, `findAll()`, `findById()` 같은 메서드를 직접 구현했다. 이번에는 Repository가 아래 한 줄로 끝났다.

```java
public interface StudyRoomRepository extends JpaRepository<StudyRoom, Long> {
}
```

처음 코드를 보면 너무 짧아서 실제 저장은 어디서 하는 건지 잘 안 보인다. `JpaRepository`가 기본 CRUD 구현을 이미 가지고 있기 때문에 내가 구현체를 따로 만들지 않아도 되는 구조였다. Service는 전과 비슷하게 Repository 메서드를 부르지만, 그 뒤에서는 JPA가 SQL을 만들고 MySQL에 전달한다.

1주차에 Repository를 인터페이스로 분리했던 이유도 여기서 다시 확인했다. 저장 방식이 `Map`에서 MySQL로 바뀌어도 Controller와 Service의 책임은 크게 흔들리지 않았다.

## 2. Entity와 DTO는 같은 데이터인데 왜 나눌까

이번 주에는 `StudyRoom` Entity와 요청, 응답 DTO를 따로 만들었다.

```java
@Entity
@Table(name = "study_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StudyRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 200)
    private String location;

    @Column(nullable = false)
    private int capacity;
}
```

Entity는 MySQL의 `study_rooms` 테이블과 연결되는 객체다. 반면 `StudyRoomCreateRequest`는 클라이언트가 보내야 할 값과 검증 규칙을 담는다.

```java
public record StudyRoomCreateRequest(
        @NotBlank(message = "스터디룸 이름은 필수입니다.")
        @Size(max = 100, message = "스터디룸 이름은 100자 이하여야 합니다.")
        String name,

        @NotBlank(message = "위치는 필수입니다.")
        String location,

        @Min(value = 1, message = "수용 인원은 1명 이상이어야 합니다.")
        @Max(value = 1000, message = "수용 인원은 1000명 이하여야 합니다.")
        int capacity,

        @Size(max = 500, message = "설명은 500자 이하여야 합니다.")
        String description
) {
}
```

둘 다 이름이나 위치 같은 값을 가지고 있어서 처음에는 비슷해 보인다. 하지만 Entity는 DB에 어떻게 저장할지를 나타내고, DTO는 API가 어떤 값을 받을지 또는 내보낼지를 나타낸다.

DTO를 따로 두니 `@NotBlank`, `@Min`, `@Max` 같은 요청 검증 규칙도 Entity와 분리할 수 있었다. DB에 있는 모든 필드를 응답으로 내보낼 필요도 없고, 나중에 DB 구조가 바뀌더라도 API 응답을 그대로 유지할 여지도 생긴다.

## 3. 수정 코드에는 왜 save가 없을까

Service에는 클래스 단위로 읽기 전용 트랜잭션을 붙였다.

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudyRoomService {
}
```

조회 메서드는 이 설정을 그대로 사용하고, 데이터를 바꾸는 생성, 수정, 삭제 메서드에만 `@Transactional`을 다시 붙였다.

```java
@Transactional
public StudyRoomResponse update(Long id, StudyRoomUpdateRequest request) {
    StudyRoom studyRoom = getStudyRoom(id);
    studyRoom.update(
            request.name(),
            request.location(),
            request.capacity(),
            request.description()
    );
    return StudyRoomResponse.from(studyRoom);
}
```

여기서 눈에 들어온 건 수정 뒤에 `save()`가 없다는 점이었다. 1주차 방식으로 생각하면 값을 바꾼 다음 다시 저장해야 할 것 같았다.

JPA에서는 트랜잭션 안에서 조회한 Entity를 영속성 컨텍스트가 관리한다. 관리 중인 Entity의 값이 달라지면 트랜잭션이 끝날 때 JPA가 변경을 확인하고 `UPDATE` SQL을 실행한다. 그래서 위 코드에서는 `studyRoom.update()`만 호출해도 DB에 수정 내용이 반영된다.

이번 주에 배운 개념 중에서는 이 변경 감지가 가장 JPA다운 부분으로 느껴졌다. 코드에 SQL도 없고 `save()`도 없는데 DB 값이 바뀐다는 점이 처음에는 낯설었다.

## 4. CRUD보다 오래 걸린 MySQL 실행

MySQL만 설치하면 바로 `bootRun`이 될 줄 알았는데 생각보다 단계가 많았다.

Chocolatey로 설치한 MySQL에는 실행 파일은 있었지만 데이터 디렉터리와 Windows 서비스가 준비되지 않았다. 데이터 디렉터리를 초기화하고 `MySQL96` 서비스를 따로 등록해야 했다.

처음에는 서비스 등록 명령이 제대로 해석되지 않았고, 직접 등록한 서비스도 시작되지 않았다. 로그를 따라가 보니 MySQL이 서비스 이름을 일반 실행 인수로 받아들이고 있었다. 결국 `my.ini`를 만들고 MySQL의 서비스 설치 명령으로 다시 등록했다.

```text
Removing the incorrectly registered MySQL96 service...
Registering the MySQL96 service with MySQL...
Service successfully installed.
Starting the MySQL96 service...
MySQL is ready.
```

현재는 `MySQL96` 서비스가 자동 시작되도록 설정돼 있다.

```text
Service: MySQL96
Status: Running
StartType: Automatic
```

서버가 뜬 뒤에는 이번 주에 사용할 DB를 만들었다.

```sql
CREATE DATABASE study_room
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
```

여기까지 했으면 끝이라고 생각했는데, Spring Boot 실행에서 한 번 더 막혔다.

## 5. application.yml은 MySQL인데 왜 PostgreSQL로 연결될까

`bootRun` 로그에는 이런 오류가 나왔다.

```text
Driver com.mysql.cj.jdbc.Driver claims to not accept jdbcUrl,
jdbc:postgresql://...neon.tech/...
```

MySQL 드라이버가 PostgreSQL 주소를 받았다는 내용이었다. 그런데 프로젝트의 설정은 분명 MySQL이었다.

```yaml
spring:
  datasource:
    url: ${DB_URL:jdbc:mysql://localhost:3306/study_room?serverTimezone=Asia/Seoul&characterEncoding=UTF-8}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

원인은 PowerShell에 남아 있던 배포용 환경변수였다. `SPRING_PROFILES_ACTIVE`에는 `prod`가 들어 있었고, `SPRING_DATASOURCE_URL`은 Neon PostgreSQL을 가리키고 있었다. 이 값들이 `application.yml`의 기본 MySQL 주소보다 먼저 적용되고 있었다.

현재 PowerShell에서 변수를 제거하고 다시 실행하니 MySQL에 정상 연결됐다.

```powershell
Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
Remove-Item Env:SPRING_DATASOURCE_URL -ErrorAction SilentlyContinue
Remove-Item Env:SPRING_DATASOURCE_USERNAME -ErrorAction SilentlyContinue
Remove-Item Env:SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue

.\gradlew.bat bootRun
```

같은 오류를 반복하지 않으려고 로컬 MySQL 설정으로 실행하는 `run-local.bat`도 만들었다.

이번 오류를 겪고 나서 `application.yml`만 맞다고 끝나는 게 아니라는 걸 알았다. Spring Boot가 실행될 때는 설정 파일뿐 아니라 현재 프로필과 환경변수도 함께 봐야 했다. 다음에 DB 연결 오류가 나면 주소, 드라이버, 환경변수를 같이 확인할 생각이다.

## 6. 실제로 DB에 저장되는지 확인

서버가 정상 실행된 뒤 `requests.http`로 StudyRoom을 생성하고 전체 조회를 호출했다.

```http
GET /api/study-rooms
```

```json
[
  {
    "id": 1,
    "name": "Spring 스터디룸",
    "location": "부산광역시 부산진구",
    "capacity": 6,
    "description": "화이트보드와 콘센트가 있는 공간"
  }
]
```

HTTP `200` 응답이 왔고, `study_room` DB에도 데이터가 남아 있는 것을 확인했다. 서버가 꺼지면 같이 사라지던 1주차의 `Map`과 가장 확실하게 달라진 부분이었다.

테스트에서는 매번 로컬 MySQL이 필요하지 않도록 H2를 MySQL 호환 모드로 사용했다. 애플리케이션 실행 테스트 1개와 CRUD, 검증을 확인하는 통합 테스트 5개가 모두 통과했다.

```text
Tests: 6
Failures: 0
Errors: 0
```

## 7. 스스로 묻고 답한 질문들

### Q. Entity와 DTO는 왜 분리하는가?

Entity는 DB 테이블과 연결되는 객체이고, DTO는 API에서 주고받을 데이터의 모양이다. 둘을 분리하면 DB 구조를 응답에 그대로 노출하지 않아도 되고, 요청 검증 규칙도 DTO에 따로 둘 수 있다.

### Q. JpaRepository가 해주는 일은 무엇인가?

기본 CRUD 구현과 SQL 실행을 맡는다. `JpaRepository<StudyRoom, Long>`을 상속하면 `save()`, `findAll()`, `findById()`, `delete()` 같은 메서드를 바로 사용할 수 있다.

### Q. `@Transactional`을 왜 Service에 붙였는가?

Service가 하나의 기능 흐름을 조립하는 곳이기 때문이다. 여러 DB 작업을 하나의 단위로 묶어 모두 반영하거나, 문제가 생기면 함께 취소할 수 있다. 이번 코드에서는 조회는 읽기 전용으로 두고, 생성, 수정, 삭제에만 쓰기 트랜잭션을 적용했다.

### Q. 영속성 컨텍스트는 무엇인가?

트랜잭션 안에서 Entity를 관리하는 공간이다. 조회한 Entity의 상태 변화를 추적하고, 트랜잭션이 끝날 때 변경된 값을 DB에 반영한다. 수정 메서드에서 `save()`를 다시 호출하지 않은 이유도 여기에 있다.

### Q. InMemory Repository와 JPA Repository는 무엇이 다른가?

InMemory Repository는 `Map`에 데이터를 보관해서 서버가 꺼지면 데이터가 사라진다. JPA Repository는 Entity를 DB 테이블과 연결해 MySQL에 저장한다. 데이터가 남는 대신 DB 연결과 Entity 매핑, 트랜잭션을 함께 생각해야 한다.

## 정리하며

2주차는 단순히 Repository를 JPA로 바꾸는 주라고 생각했는데, 실제로는 Entity와 DTO의 역할, 트랜잭션, 변경 감지, DB 실행 환경까지 같이 다루게 됐다.

코드만 놓고 보면 `JpaRepository` 덕분에 오히려 짧아진 부분도 많았다. 대신 짧아진 코드 뒤에서 JPA가 어떤 일을 하는지 이해하는 게 더 중요했다. MySQL 연결 오류를 해결하면서는 로그에서 실제 JDBC 주소를 확인하는 습관도 생겼다. 다음에 비슷한 문제가 생기면 설정 파일 하나만 보지 않고 실행 프로필과 환경변수부터 같이 확인할 것이다.

---

#Spring #SpringBoot #JPA #MySQL #DTO #Transactional #Backend #TIL #백엔드스터디

오늘 공부한 소스코드: [Developer-Roadmap_Spring_Study/week2](https://github.com/enderpawar/Developer-Roadmap_Spring_Study/tree/master/week2)
