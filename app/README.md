# app — Posting API (Spring Boot)

`POST /postings/{id}/status-reports`가 이 세션의 핵심 코드입니다.
`StatusReportCommandService.submit()` 한 메서드 안에서 상태 등록 INSERT와,
15건째를 채운 요청에 한해서만 `outbox_events` INSERT가 하나의 로컬 트랜잭션으로
묶입니다. 그 뒤(CDC → Kinesis → Step Functions Saga)는 이 애플리케이션이 전혀 모릅니다.

## 도메인 흐름

1. 공고(Posting)가 생성되면 `DOCUMENT` 단계로 시작합니다.
2. 참가자들이 자신이 아는 결과를 `PASS` / `FAIL` / `PENDING`으로 등록합니다
   (`POST /postings/{id}/status-reports`).
3. 같은 단계에서 `PASS`+`FAIL` 등록이 누적 15건을 넘기는 순간, 그 요청 하나가
   "전형 발표"를 확정하고 `SelectionAnnounced` 이벤트를 outbox에 적재합니다.
   `PENDING`은 기록만 되고 15건 카운트에는 관여하지 않습니다.
4. 이후 Saga: `AdvanceStage`(다음 단계로 전환) → `NotifySubscribers`(구독자 알림)
   → 알림이 실패하면 `RevertStageAdvance`(전환 취소, 보상 트랜잭션). 다음 단계는
   구독자 전원이 이전 결과를 통보받기 전까지는 "공식적으로 열린" 게 아니라는
   공정성 원칙 때문입니다.

## 레이어 구조

```
domain/            프레임워크에 최대한 덜 의존하는 순수 도메인 로직
  posting/          Posting 애그리거트(현재 단계, 누적 카운트), 저장소 포트
  statusreport/      StatusReport(합/불/대기중 등록), 저장소 포트
  subscription/      NotificationSubscription(알림 구독), 저장소 포트
  outbox/            OutboxEvent, 이벤트 조립(OutboxEventFactory), 실패 주입 값 객체

application/        유스케이스. 쓰기(Command)와 읽기(Query)를 클래스 단위로 분리
  dto/              요청/응답 전용 record
  exception/

infrastructure/     구현 세부사항 — 여기가 바뀌어도 domain/application은 안 바뀌어야 정상
  persistence/       Spring Data JPA 어댑터 + PostingCounterGateway(JdbcTemplate)
  web/               REST 컨트롤러, CORS/예외 매핑
```

## 이 코드에서 짚고 있는 것들

- **원자성(Outbox)**: `StatusReportCommandService.submit()`의 `@Transactional` 하나.
  상태 등록 저장과 outbox 이벤트 저장이 같은 트랜잭션 안에서만 함께 커밋됩니다.
- **동시성 (15번째 레이스)**: "15건을 넘었는지"를 SELECT COUNT로 먼저 읽고 애플리케이션이
  판단하지 않습니다. `PostingCounterGateway.incrementAndGetStageReportCount()`가
  원자적 UPDATE...RETURNING으로 증가시킨 뒤의 값을 그대로 돌려주고, 그 값이 임계값을
  넘겼을 때만 `tryMarkStageAnnounced()`의 조건부 UPDATE(`WHERE stage_announced_at
  IS NULL`)가 "발표 권한"을 정확히 하나의 트랜잭션에게만 줍니다.
  `StatusReportCommandServiceConcurrencyTest`가 이걸 동시 요청 10개로 실제로 검증합니다.
- **N+1**: `PostingCounterGateway.listWithBreakdown()`이 postings와 status_reports를
  JOIN + `FILTER` 집계 한 방으로 가져옵니다. `PostingQueryService`에 N+1이 나는
  잘못된 버전을 주석으로 남겨뒀습니다.
- **도메인 분리**: OutboxEvent는 Posting에 대한 JPA 연관관계를 갖지 않습니다.
  Outbox 테이블은 특정 도메인에 종속되지 않는 범용 테이블이어야 하기 때문입니다.
- **의도적인 레이어링 예외**: `PostingCounterGateway`는 domain 포트 없이
  `infrastructure.persistence`를 애플리케이션 서비스가 직접 참조합니다. `RETURNING`을
  쓰는 원자적 UPDATE는 JPA 포트로 감싸는 순간 오히려 의도가 흐려지기 때문에 의도적으로
  둔 예외이고, 나머지 저장소(Posting/StatusReport/NotificationSubscription)는 전부
  domain 포트를 통해서만 접근합니다.

## 실행 방법

### 0. 사전 준비

- JDK 21
- Docker (로컬 Postgres 실행 + 자동화 테스트의 Testcontainers 둘 다 필요)

### 1. 로컬 Postgres 띄우기

```bash
docker run --name outboxlab-postgres \
  -e POSTGRES_DB=outboxlab -e POSTGRES_USER=outboxlab -e POSTGRES_PASSWORD=outboxlab \
  -p 5432:5432 -d postgres:16-alpine
```

`application.yml`의 기본값(`outboxlab`/`outboxlab`/`localhost:5432`)과 정확히 맞아서
별도 환경변수 없이 바로 연결됩니다. Aurora로 돌릴 때는 `DB_HOST`, `DB_NAME`,
`DB_USERNAME`, `DB_PASSWORD` 환경변수로 덮어쓰면 됩니다.

### 2. 애플리케이션 실행

Windows(PowerShell):

```powershell
cd app
.\gradlew.bat bootRun
```

Mac/Linux:

```bash
cd app
./gradlew bootRun
```

스키마는 Flyway가 기동 시 자동 적용합니다(`src/main/resources/db/migration`).

### 3. 살아있는지 확인

```bash
curl http://localhost:8080/actuator/health
```

## 테스트 방법

### 1. 자동화 테스트 (동시성 검증이 핵심)

```powershell
cd app
.\gradlew.bat test
```

`StatusReportCommandServiceConcurrencyTest`가 Testcontainers로 별도의 실제 Postgres를
띄워서, 임계값(15건) 직전까지 채운 뒤 동시 요청 10개를 쏘고 정확히 하나만
"발표"로 확정되는지 검증합니다. 1단계에서 띄운 Postgres와는 완전히 별개의
컨테이너이고 테스트가 끝나면 자동으로 정리됩니다 — Docker만 실행 중이면 됩니다.

특정 테스트만: `.\gradlew.bat test --tests "*ConcurrencyTest"`
결과 리포트: `app/build/reports/tests/test/index.html`

### 2. 수동으로 API 호출해보기 (PowerShell)

```powershell
# 공고 생성
$posting = Invoke-RestMethod http://localhost:8080/postings -Method Post -ContentType "application/json" -Body '{"title":"백엔드 신입 공채"}'
$postingId = $posting.postingId

# 14건까지는 그냥 기록됨 (announced=false)
1..14 | ForEach-Object {
    Invoke-RestMethod "http://localhost:8080/postings/$postingId/status-reports" -Method Post -ContentType "application/json" -Body (@{status="PASS"; reporterId="user-$_"} | ConvertTo-Json)
}

# 15번째 — announced=true, eventId/correlationId가 채워짐
Invoke-RestMethod "http://localhost:8080/postings/$postingId/status-reports" -Method Post -ContentType "application/json" -Body (@{status="PASS"; reporterId="user-15"} | ConvertTo-Json)

# 집계 확인
Invoke-RestMethod http://localhost:8080/postings

# 알림 구독
Invoke-RestMethod "http://localhost:8080/postings/$postingId/subscriptions" -Method Post -ContentType "application/json" -Body (@{userId="gyuri"} | ConvertTo-Json)
```

Mac/Linux에서는 `Invoke-RestMethod ... -Method Post`를 `curl -X POST ...`로 바꾸면
동일하게 동작합니다.

### 3. 동시에 여러 요청을 쏴서 레이스를 직접 재현하기 (PowerShell 7+)

```powershell
1..10 | ForEach-Object -Parallel {
    Invoke-RestMethod "http://localhost:8080/postings/$using:postingId/status-reports" -Method Post -ContentType "application/json" -Body (@{status="PASS"; reporterId="racer-$_"} | ConvertTo-Json)
} -ThrottleLimit 10
```

애플리케이션 로그를 보면, 여러 요청이 `count>=15`를 동시에 관측해도
"15건 임계값 확정 — SelectionAnnounced 이벤트 적재" 로그는 정확히 한 줄만 찍힙니다.

### 4. DB에서 직접 확인

```bash
docker exec -it outboxlab-postgres psql -U outboxlab -d outboxlab -c "select current_stage, stage_report_count, stage_announced_at from postings;"
docker exec -it outboxlab-postgres psql -U outboxlab -d outboxlab -c "select event_type, aggregate_id, correlation_id from outbox_events;"
```

동시에 15건을 넘긴 요청이 여러 개였어도 `outbox_events`에는
`SelectionAnnounced`가 정확히 1건만 있어야 합니다.
