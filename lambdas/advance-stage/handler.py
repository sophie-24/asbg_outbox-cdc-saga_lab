import logging
import os

import pg8000.native

logger = logging.getLogger()
logger.setLevel(logging.INFO)

STAGE_SEQUENCE = ["DOCUMENT", "INTERVIEW", "FINAL"]


def next_stage(stage):
    idx = STAGE_SEQUENCE.index(stage)
    return STAGE_SEQUENCE[idx + 1] if idx + 1 < len(STAGE_SEQUENCE) else None


def get_connection():
    return pg8000.native.Connection(
        user=os.environ["DB_USERNAME"],
        password=os.environ["DB_PASSWORD"],
        host=os.environ["DB_HOST"],
        database=os.environ["DB_NAME"],
        port=int(os.environ.get("DB_PORT", "5432")),
    )


def handler(event, context):
    """
    Step Functions 사가의 첫 스텝. SelectionAnnounced 이벤트가 알린 단계(announcedStage)를
    기준으로 다음 단계로 전환한다.

    postings.stage_report_count / stage_announced_at은 "현재 단계"의 카운트를 담는
    단일 컬럼이라서, 다음 단계로 넘어가면서 반드시 0/NULL로 초기화해야 한다 — 안 그러면
    새 단계가 이미 "15건 채워짐"으로 보이는 버그가 생긴다. 되돌릴 때 필요한 이전 단계의
    실제 카운트는 여기서 따로 들고 다니지 않는다 — RevertStageAdvance가 status_reports
    (append-only 원본 기록)에서 다시 집계한다.

    WHERE current_stage = announcedStage 조건 덕분에 이 함수가 재시도돼도(Step Functions
    재시도, 중복 트리거 등) 두 번째 실행은 조건에 안 걸려서 아무 일도 안 한다 — 멱등하다.
    """
    posting_id = event["postingId"]
    announced_stage = event["announcedStage"]
    inject_failure = event.get("injectFailure") or ""

    if inject_failure == "advance_failure":
        logger.error("advance_failure 주입 — AdvanceStage 자체를 실패시킴: postingId=%s", posting_id)
        raise RuntimeError("AdvanceStage 실패 주입")

    new_stage = next_stage(announced_stage)

    if new_stage is None:
        logger.info("FINAL 단계 발표 — 더 전환할 단계 없음: postingId=%s", posting_id)
        return {
            "postingId": posting_id,
            "previousStage": announced_stage,
            "newStage": announced_stage,
            "advanced": False,
        }

    conn = get_connection()
    try:
        conn.run(
            """
            UPDATE postings
            SET current_stage = :new_stage, stage_report_count = 0, stage_announced_at = NULL
            WHERE id = :posting_id AND current_stage = :announced_stage
            """,
            new_stage=new_stage,
            posting_id=posting_id,
            announced_stage=announced_stage,
        )
        updated = conn.row_count
        logger.info(
            "단계 전환: postingId=%s, %s -> %s (updated=%s)",
            posting_id, announced_stage, new_stage, updated,
        )
        return {
            "postingId": posting_id,
            "previousStage": announced_stage,
            "newStage": new_stage,
            "advanced": updated == 1,
        }
    finally:
        conn.close()
