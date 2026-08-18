CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE postings (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title               VARCHAR(200) NOT NULL,
    current_stage       VARCHAR(20) NOT NULL DEFAULT 'DOCUMENT',
    stage_report_count  INTEGER NOT NULL DEFAULT 0,
    stage_announced_at  TIMESTAMPTZ,
    version             BIGINT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE status_reports (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    posting_id    UUID NOT NULL,
    stage         VARCHAR(20) NOT NULL,
    status        VARCHAR(20) NOT NULL,
    reporter_id   VARCHAR(100),
    reported_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- PostingCounterGateway.listWithBreakdown()의 JOIN(posting_id, stage)이 이 인덱스를 탄다.
CREATE INDEX ix_status_reports_posting_stage ON status_reports (posting_id, stage);

CREATE TABLE notification_subscriptions (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    posting_id     UUID NOT NULL,
    user_id        VARCHAR(100) NOT NULL,
    subscribed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_notification_subscriptions_posting ON notification_subscriptions (posting_id);

-- 범용 Outbox 테이블. 특정 aggregate(Posting)에 대한 FK를 걸지 않는다 —
-- 나중에 다른 도메인이 생겨도 이 테이블 하나를 계속 재사용하기 위함.
--
-- 참고: 이전(Application) 버전에는 idempotency_key UNIQUE INDEX가 동시성의 최종
-- 안전장치였다. 이번 도메인에서는 "중복 방지"가 아니라 "임계값을 넘긴 순간을 정확히
-- 한 번만 확정"하는 문제라서, 안전장치가 postings.stage_announced_at에 대한 조건부
-- UPDATE(PostingCounterGateway.tryMarkStageAnnounced)로 바뀌었다 — UNIQUE INDEX가
-- 필요 없다.
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

-- N+1 회피용 집계/조회가 이 인덱스를 탄다.
CREATE INDEX ix_outbox_events_aggregate ON outbox_events (aggregate_type, aggregate_id);
