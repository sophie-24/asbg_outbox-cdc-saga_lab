# app — Application API (Spring Boot)

`POST /applications`가 이 세션의 핵심 코드입니다. `ApplicationCommandService.create()`
한 메서드 안에서 `applications` INSERT와 `outbox_events` INSERT가 하나의 로컬 트랜잭션으로
묶입니다. 그 뒤(CDC → Kinesis → Step Functions Saga)는 이 애플리케이션이 전혀 모릅니다.

## 레이어 구조

```
domain/          프레임워크에 최대한 덜 의존하는 순수 도메인 로직
  application/    Application 애그리거트, 저장소 포트(인터페이스)
  outbox/         OutboxEvent, 이벤트 조립(OutboxEventFactory), 실패 주입 값 객체

application/      유스케이스. 쓰기(Command)와 읽기(Query)를 클래스 단위로 분리
  dto/            요청/응답 전용 record
  exception/

infrastructure/   구현 세부사항 — 여기가 바뀌어도 domain/application은 안 바뀌어야 정상
  persistence/    Spring Data JPA 어댑터
  web/            REST 컨트롤러, 예외 매핑
```

## 이 코드에서 짚고 있는 것들

- **원자성(Outbox)**: `ApplicationCommandService.create()`의 `@Transactional` 하나.
- **동시성**: idempotency_key 사전 조회는 성능 최적화일 뿐이고, 진짜 안전장치는
  `V1__init_schema.sql`의 `UNIQUE INDEX`. `ApplicationCommandServiceConcurrencyTest`가
  이걸 10개 스레드 동시 요청으로 실제로 검증합니다.
- **N+1**: `ApplicationJpaRepository.findRecentWithLatestEvent()`가 Application과
  OutboxEvent를 (매핑되지 않은 엔티티 간) JPQL ON 조인 한 방으로 가져옵니다.
  `ApplicationQueryService`에 N+1이 나는 잘못된 버전을 주석으로 남겨뒀습니다.
- **도메인 분리**: OutboxEvent는 Application에 대한 JPA 연관관계를 갖지 않습니다.
  Outbox 테이블은 특정 도메인에 종속되지 않는 범용 테이블이어야 하기 때문입니다.

## 로컬 실행

```bash
./gradlew build
./gradlew bootRun
```

`DB_HOST`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` 환경변수로 Aurora(또는 로컬 Postgres)를
가리키세요. 스키마는 Flyway가 기동 시 자동 적용합니다(`src/main/resources/db/migration`).
