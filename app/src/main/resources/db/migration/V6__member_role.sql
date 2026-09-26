-- Day24: 인가(authorization)에 쓸 권한 컬럼. 기존 로우까지 전부 만족해야 하므로 DEFAULT를 둔다.
-- (Hibernate는 INSERT마다 role 컬럼 값을 명시적으로 보내므로, 이 DEFAULT는 사실 Flyway가
--  이 마이그레이션을 실행하는 시점에 이미 있던 기존 로우에만 적용된다 — Member.java의 필드 초기값이
--  실제 애플리케이션 INSERT의 기본값을 책임진다.)
ALTER TABLE member
    ADD role VARCHAR(20) NOT NULL DEFAULT 'USER';
