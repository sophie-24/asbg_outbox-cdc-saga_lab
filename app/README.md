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
  web/               REST 컨트롤러, 예외 매핑
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

## 로컬 실행

```bash
./gradlew build
./gradlew bootRun
```

`DB_HOST`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` 환경변수로 Aurora(또는 로컬 Postgres)를
가리키세요. 스키마는 Flyway가 기동 시 자동 적용합니다(`src/main/resources/db/migration`).
