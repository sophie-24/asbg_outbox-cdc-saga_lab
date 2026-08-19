# Step Functions 상태머신 — `selection-announced.asl.json`

`SELECTION_ANNOUNCED` 이벤트 하나당 이 상태머신 실행이 하나씩 시작됩니다
(트리거는 `lambdas/event-router`). 실행 이름은 `eventId`로 고정해서, Step Functions
Standard Workflow의 "동일 이름 실행은 90일간 거부" 특성을 멱등성의 2차 방어선으로
씁니다 (1차는 event-router의 DynamoDB `consumer_inbox` 조건부 쓰기).

## 흐름

```
AdvanceStage ──성공──▶ NotifySubscribers ──성공──▶ (종료)
                              │
                            실패(Catch)
                              ▼
                       RevertStageAdvance ──▶ NotificationFailed (Fail 상태)
```

## 콘솔에서 만드는 법 (참가자용, 코드 작성 없음)

1. Step Functions 콘솔 → "상태 머신 생성" → "코드로 작성"
2. `selection-announced.asl.json` 내용을 붙여넣고, `__ADVANCE_STAGE_FUNCTION_ARN__`
   / `__NOTIFY_SUBSCRIBERS_FUNCTION_ARN__` / `__REVERT_STAGE_ADVANCE_FUNCTION_ARN__`를
   각 Lambda의 실제 ARN으로 바꿉니다.
3. 유형: **Standard** (Express 아님 — 실행 이력을 콘솔에서 시각적으로 봐야 하고,
   위에서 설명한 실행 이름 기반 중복 방지도 Standard에서만 보장됩니다).
4. 실행 역할에 `lambda:InvokeFunction` 권한을 3개 함수 ARN에 대해 부여합니다.

## 실습 포인트

- **정상 흐름**: `injectFailure`를 비운 이벤트 → AdvanceStage 성공 → NotifySubscribers
  성공 → 실행이 초록색으로 끝나는 걸 콘솔에서 확인.
- **보상 흐름**: `injectFailure=notify_failure`인 이벤트 → NotifySubscribers가 실패 →
  Catch가 잡아서 RevertStageAdvance 실행 → `postings.current_stage`가 원래대로
  돌아간 걸 DB에서 확인. 콘솔의 실행 그래프에서 Catch로 꺾이는 경로가 그대로 보입니다.
- **첫 스텝 자체가 실패하는 경우**: `injectFailure=advance_failure`면 AdvanceStage에서
  바로 예외가 나고, 되돌릴 것도 없이 실행 전체가 실패로 끝납니다 — 보상이 필요 없는
  실패와 보상이 필요한 실패의 차이를 대비해서 보여줄 수 있습니다.
