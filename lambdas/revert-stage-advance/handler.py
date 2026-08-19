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
    NotifySubscribers가 실패했을 때만 실행되는 보상 트랜잭션. 다음 단계는 구독자 전원이
    이전 결과를 통보받기 전까지는 "공식적으로 열린" 게 아니라는 공정성 원칙 때문에,
    AdvanceStage가 이미 확정한 단계 전환을 되돌린다.

    입력은 AdvanceStage의 출력이 아니라, 맨 처음 이벤트에 실려온 announcedStage를
    그대로 다시 쓴다 — Step Functions가 중간 결과를 잘못 전달해도(혹은 이 스텝만 따로
    재시도돼도) previousStage/newStage를 스스로 다시 계산하니 흔들리지 않는다.

    되돌릴 때 채워 넣는 stage_report_count는 AdvanceStage가 지워버린 값을 추측하지
    않고, status_reports(추가 전용 원본 기록)에서 다시 집계한 실제 값을 쓴다 —
    "사전 조회는 안전장치가 아니다"의 반대 방향 원칙: 캐시된 카운터가 아니라 원본
    로그가 진실이다.

    WHERE current_stage = new_stage 조건 덕분에 이 함수도 멱등하다 — 이미 되돌려진
    상태에서 다시 호출돼도 조건에 안 걸려서 아무 일도 안 한다.
    """
    posting_id = event["postingId"]
    previous_stage = event["announcedStage"]
    new_stage = next_stage(previous_stage)

    if new_stage is None:
        logger.info("FINAL 단계는 전환이 없었으므로 되돌릴 것도 없음: postingId=%s", posting_id)
        return {"postingId": posting_id, "reverted": False}

    conn = get_connection()
    try:
        rows = conn.run(
            """
            SELECT COUNT(*) FILTER (WHERE status IN ('PASS', 'FAIL')) AS report_count
            FROM status_reports
            WHERE posting_id = :posting_id AND stage = :previous_stage
            """,
            posting_id=posting_id,
            previous_stage=previous_stage,
        )
        actual_report_count = rows[0][0]

        conn.run(
            """
            UPDATE postings
            SET current_stage = :previous_stage,
                stage_report_count = :report_count,
                stage_announced_at = NULL
            WHERE id = :posting_id AND current_stage = :new_stage
            """,
            previous_stage=previous_stage,
            report_count=actual_report_count,
            posting_id=posting_id,
            new_stage=new_stage,
        )
        reverted = conn.row_count == 1
        logger.info(
            "단계 되돌림: postingId=%s, %s -> %s (복원된 카운트=%s, reverted=%s)",
            posting_id, new_stage, previous_stage, actual_report_count, reverted,
        )
        return {
            "postingId": posting_id,
            "revertedToStage": previous_stage,
            "restoredReportCount": actual_report_count,
            "reverted": reverted,
        }
    finally:
        conn.close()
