-- Day33: 같은 방(room)의 예약 시간대가 겹치는 것을 막기 위한 시작/종료 시각.
-- V1~V6까지 만들어진 기존 예약에는 시간대 개념이 없었으므로(그때는 "확정 여부"만 있었다)
-- NULL을 허용한다 — 기존 로우가 깨지지 않는 nullable-safe 마이그레이션.
ALTER TABLE reservation
    ADD start_at TIMESTAMP;

ALTER TABLE reservation
    ADD end_at TIMESTAMP;
