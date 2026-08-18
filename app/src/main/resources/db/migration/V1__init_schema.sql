CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE applications (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    applicant_name   VARCHAR(100) NOT NULL,
    idempotency_key  VARCHAR(100),
    version          BIGINT NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 동시에 같은 idempotency_key로 들어온 요청 중 단 하나만 성공하게 만드는 최종 안전장치.
-- 애플리케이션 코드의 사전 조회(findByIdempotencyKey)는 이 제약이 있어야만 진짜로 안전하다.
CREATE UNIQUE INDEX ux_applications_idempotency_key
    ON applications (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

-- 범용 Outbox 테이블. 특정 aggregate(Application)에 대한 FK를 걸지 않는다 —
-- 나중에 다른 도메인(Payment 등)이 생겨도 이 테이블 하나를 계속 재사용하기 위함.
CREATE TABLE outbox_events (
    event_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type   VARCHAR(50) NOT NULL,
    aggregate_id     UUID NOT NULL,
    event_type       VARCHAR(50) NOT NULL,
    payload          JSONB NOT NULL,
    correlation_id   VARCHAR(100) NOT NULL,
    occurred_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 워크숍 UI가 "내 이벤트 찾기"를 correlation_id로 하므로 인덱스를 걸어둔다.
CREATE INDEX ix_outbox_events_correlation_id ON outbox_events (correlation_id);

-- Part 2의 N+1 회피 쿼리(JOIN)가 이 인덱스를 탄다.
CREATE INDEX ix_outbox_events_aggregate ON outbox_events (aggregate_type, aggregate_id);
