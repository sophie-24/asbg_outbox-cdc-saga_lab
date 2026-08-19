import base64
import json
import logging
import os

import boto3
from botocore.exceptions import ClientError

logger = logging.getLogger()
logger.setLevel(logging.INFO)

dynamodb = boto3.resource("dynamodb")
stepfunctions = boto3.client("stepfunctions")

INBOX_TABLE_NAME = os.environ["INBOX_TABLE_NAME"]
STATE_MACHINE_ARN = os.environ["STATE_MACHINE_ARN"]
SUPPORTED_EVENT_TYPES = {"SELECTION_ANNOUNCED"}


def handler(event, context):
    """
    DMS가 outbox_events 테이블의 INSERT를 캡처해서 Kinesis로 흘려보내면, 그 레코드를
    받아 (1) outbox_events INSERT인지 필터링 (2) 멱등성 확인 (3) 이벤트 유효성 검증
    (4) Step Functions 실행 트리거까지 담당한다. Saga 자체의 로직은 이 Lambda가
    알 필요 없다 — 그건 상태머신(statemachine/)의 역할이다.

    한 번의 호출에 여러 Kinesis 레코드가 배치로 들어온다. 레코드 하나가 잘못됐다고
    배치 전체를 실패시키면(Kinesis는 실패한 배치를 그대로 재시도한다) 정상 레코드까지
    계속 막히므로, 레코드 단위로 try/except를 둬서 한 건의 실패가 나머지를 막지
    않게 한다.
    """
    inbox_table = dynamodb.Table(INBOX_TABLE_NAME)
    for record in event.get("Records", []):
        try:
            _process_record(record, inbox_table)
        except Exception:
            logger.exception("레코드 처리 실패 — 이 레코드만 건너뛰고 계속 진행")


def _process_record(record, inbox_table):
    payload = base64.b64decode(record["kinesis"]["data"])
    message = json.loads(payload)

    metadata = message.get("metadata", {})
    if metadata.get("table-name") != "outbox_events" or metadata.get("operation") != "insert":
        return  # outbox_events에 대한 INSERT가 아니면 이 라우터가 신경 쓸 일이 아니다

    data = message["data"]
    event_id = data["event_id"]
    event_type = data["event_type"]

    if event_type not in SUPPORTED_EVENT_TYPES:
        logger.warning("알 수 없는 event_type=%s — 건너뜀: eventId=%s", event_type, event_id)
        return

    if not _claim_event_id(event_id, inbox_table):
        logger.info("중복 전달 감지 — 이미 처리된 eventId=%s, 건너뜀", event_id)
        return

    raw_payload = data["payload"]
    business_payload = json.loads(raw_payload) if isinstance(raw_payload, str) else raw_payload

    execution_input = {
        "eventId": event_id,
        "correlationId": data["correlation_id"],
        "postingId": business_payload["postingId"],
        "postingTitle": business_payload.get("postingTitle", ""),
        "announcedStage": business_payload["announcedStage"],
        "injectFailure": business_payload.get("injectFailure") or "",
    }

    # 실행 이름을 eventId로 고정해서, DynamoDB inbox와는 별개로 Step Functions
    # Standard Workflow 자체의 중복 실행 방지(동일 이름 실행은 90일간 거부)를
    # 두 번째 방어선으로 겹쳐 쓴다.
    stepfunctions.start_execution(
        stateMachineArn=STATE_MACHINE_ARN,
        name=event_id,
        input=json.dumps(execution_input),
    )
    logger.info("Step Functions 실행 시작: eventId=%s, postingId=%s",
                event_id, execution_input["postingId"])


def _claim_event_id(event_id, inbox_table):
    """DynamoDB 조건부 PutItem으로 "이 eventId를 처음 보는 요청인가"를 원자적으로
    가린다. 여기서도 사전 조회(GetItem) 후 판단하지 않는다 — 조건부 쓰기 자체가
    안전장치다."""
    try:
        inbox_table.put_item(
            Item={"event_id": event_id},
            ConditionExpression="attribute_not_exists(event_id)",
        )
        return True
    except ClientError as e:
        if e.response["Error"]["Code"] == "ConditionalCheckFailedException":
            return False
        raise
