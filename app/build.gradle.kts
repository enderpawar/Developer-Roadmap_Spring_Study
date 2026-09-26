// 기본기 로드맵용 최소 스켈레톤.
// Week A는 웹 계층만 다루므로 web + validation 만 둔다.
// 데이터 계층(JPA·H2·Flyway)은 Week B에서, 시큐리티·JWT는 Week D에서 직접 추가한다.
// 롬복은 일부러 넣지 않는다 — 생성자 주입/DTO를 직접 눈으로 보고 쓰기 위해서다.
plugins {
	java
	id("org.springframework.boot") version "3.5.3"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
	sourceCompatibility = JavaVersion.VERSION_17
	targetCompatibility = JavaVersion.VERSION_17
}

repositories { mavenCentral() }

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	// week B D1 - 스키마를 코드로 관리한다.
	// Flyway가 마이그레이션을 실행하려면 DataSource(커넥션)이 필요하다. JPA는 D2~D3에서 추가.
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	//마이그레이션 엔진
	implementation("org.flywaydb:flyway-core")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")

	// week D D1 - 비밀번호 해시(BCrypt)만 먼저 쓴다. 필터체인 전체(starter-security)는 D3에서.
	implementation("org.springframework.security:spring-security-crypto")

	// week D D2 - JWT 발급/검증. api는 컴파일에, impl/jackson은 런타임에만 필요(리플렉션으로 로드).
	implementation("io.jsonwebtoken:jjwt-api:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

	//개발용 DB. runtimeOnly = 컴파일에는 안 쓰이고 실행할 때만 필요
	runtimeOnly("com.h2database:h2")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> { options.encoding = "UTF-8" }
tasks.withType<Test> {
	useJUnitPlatform()
	jvmArgs("-Dfile.encoding=UTF-8")
	// 테스트는 실행하는 사람의 OS 환경변수에 좌우되면 안 된다.
	// 환경변수는 application.yml보다 우선순위가 높아서, SPRING_DATASOURCE_URL이 걸려 있으면
	// 테스트가 엉뚱한(심지어 운영) DB에 붙는다. 여기서 고정해 그 경로를 끊는다.
	environment("SPRING_DATASOURCE_URL", "jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1")
	environment("SPRING_DATASOURCE_USERNAME", "sa")
	environment("SPRING_DATASOURCE_PASSWORD", "")
	environment("SPRING_PROFILES_ACTIVE", "test")
}
