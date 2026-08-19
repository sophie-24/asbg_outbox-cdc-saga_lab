# Lambda 4개 — Saga 실행 계층

Spring Boot(`app/`)가 쓰는 `outbox_events`부터 여기까지가 이 세션의 "그 다음"입니다.
Aurora WAL → DMS → Kinesis → 여기 4개 Lambda + Step Functions(`statemachine/`)로 이어집니다.

| Lambda | 트리거 | 역할 | DB 접근 |
|---|---|---|---|
| `event-router` | Kinesis Data Stream (DMS 출력) | 멱등성 확인(DynamoDB) + Step Functions 실행 시작 | 없음 |
| `advance-stage` | Step Functions (Task) | 다음 전형 단계로 전환 | Aurora (pg8000) |
| `notify-subscribers` | Step Functions (Task) | 구독자 조회 + 알림 발송(로그로 시뮬레이션) | Aurora (pg8000) |
| `revert-stage-advance` | Step Functions (Catch) | 알림 실패 시 단계 전환 보상 | Aurora (pg8000) |

**FastAPI 같은 HTTP 프레임워크를 안 쓴 이유**: 넷 다 HTTP 요청을 받지 않습니다.
`event-router`는 Kinesis 이벤트 소스 매핑이 배치로 직접 호출하고, 나머지 셋은
Step Functions가 `arn:aws:states:::lambda:invoke`로 JSON을 직접 넘겨서 호출합니다.
라우팅/요청검증 프레임워크가 할 일이 없어서 `def handler(event, context):` 하나로
충분합니다.

## 환경변수 (advance-stage / notify-subscribers / revert-stage-advance 공통)

- `DB_HOST`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` — `app/`의 Spring Boot와 동일한
  이름 규칙입니다. 로컬/워크숍 실습에서는 같은 Aurora(또는 Postgres)를 가리킵니다.
- `DB_PORT` (선택, 기본 5432)

## 환경변수 (event-router)

- `INBOX_TABLE_NAME` — DynamoDB 멱등성 테이블 이름
- `STATE_MACHINE_ARN` — `statemachine/selection-announced.asl.json`으로 만든 상태머신 ARN

## DynamoDB `consumer_inbox` 테이블

- 파티션 키: `event_id` (String)
- 그 외 속성 없음 — `event-router`가 `attribute_not_exists(event_id)` 조건부
  `PutItem`으로만 사용합니다. 원하면 TTL 속성(`expires_at`)을 추가해서 오래된
  레코드를 자동 정리할 수 있습니다(워크숍 규모에선 필수는 아닙니다).

## 배포 방법 (콘솔에서 zip 업로드, 참가자는 이 단계 안 함 — 발표자만 수행)

`advance-stage` / `notify-subscribers` / `revert-stage-advance`는 `pg8000`을 순수
Python으로 구현한 드라이버라서(C 확장 없음) Amazon Linux용으로 별도 컴파일할 필요
없이 그대로 패키징하면 됩니다.

```bash
cd lambdas/advance-stage
pip install -r requirements.txt -t .
zip -r ../advance-stage.zip .
```

`notify-subscribers`, `revert-stage-advance`도 동일합니다. `event-router`는
`requirements.txt`가 비어 있으므로(런타임 기본 내장) `handler.py`만 그대로 zip:

```bash
cd lambdas/event-router
zip -r ../event-router.zip handler.py
```

## 로컬 검증

DB에 접근하는 세 함수는 로컬 Postgres(`app/README.md`의 `docker run` 명령으로 띄운
것)에 대해 아래처럼 직접 호출해볼 수 있습니다 — Lambda 환경을 흉내 낼 필요 없이
그냥 파이썬 함수 호출입니다.

```bash
cd lambdas/advance-stage
DB_HOST=localhost DB_NAME=outboxlab DB_USERNAME=outboxlab DB_PASSWORD=outboxlab \
  python3 -c "
import handler
print(handler.handler({'postingId': '<postings.id>', 'announcedStage': 'DOCUMENT', 'injectFailure': ''}, None))
"
```
