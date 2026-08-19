import logging
import os

import pg8000.native

logger = logging.getLogger()
logger.setLevel(logging.INFO)


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
    AdvanceStage 다음 스텝. 이 공고를 구독한 사용자 전원에게 발표 결과를 통보한다.

    실제 이메일/SNS 발송은 워크숍 범위 밖이다 — 참가자가 관찰해야 하는 건 "알림이
    갔다는 사실"이지 실제 메일함이 아니라서, 구독자 목록을 조회해서 CloudWatch Logs에
    한 줄씩 남기는 것으로 발송을 시뮬레이션한다. 나중에 실제 SES/SNS 연동으로 바꿔도
    이 함수의 계약(구독자 조회 → 발송 → 실패 시 예외)은 그대로 유지된다.

    injectFailure == "notify_failure"면 일부러 예외를 던진다 — Step Functions의
    Catch가 이걸 잡아서 RevertStageAdvance(보상)로 넘어가는 걸 참가자가 직접 보게
    하기 위한 실습용 스위치다.
    """
    posting_id = event["postingId"]
    posting_title = event.get("postingTitle", "")
    announced_stage = event.get("announcedStage", "")
    inject_failure = event.get("injectFailure") or ""

    conn = get_connection()
    try:
        rows = conn.run(
            "SELECT user_id FROM notification_subscriptions WHERE posting_id = :posting_id",
            posting_id=posting_id,
        )
        subscriber_ids = [row[0] for row in rows]
    finally:
        conn.close()

    if inject_failure == "notify_failure":
        logger.error(
            "notify_failure 주입 — 구독자 %d명에게 발송 전 실패: postingId=%s",
            len(subscriber_ids), posting_id,
        )
        raise RuntimeError("NotifySubscribers 실패 주입")

    for user_id in subscriber_ids:
        logger.info(
            "알림 발송: userId=%s, postingId=%s, title=%s, stage=%s",
            user_id, posting_id, posting_title, announced_stage,
        )

    return {
        "postingId": posting_id,
        "notifiedCount": len(subscriber_ids),
    }
