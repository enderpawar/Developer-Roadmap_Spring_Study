-- Day22: 로그인/비밀번호 저장을 위한 컬럼 추가.
-- 기존 Member(name)만 있던 로우(테스트 픽스처 등)와 호환되도록 NULL을 허용한다.
ALTER TABLE member
    ADD login_id VARCHAR(50);

ALTER TABLE member
    ADD password VARCHAR(100);

ALTER TABLE member
    ADD CONSTRAINT uq_member_login_id UNIQUE (login_id);
