package com.asbg.outboxlab.application;

import com.asbg.outboxlab.application.dto.StatusReportResult;
import com.asbg.outboxlab.application.dto.SubmitStatusReportCommand;
import com.asbg.outboxlab.application.exception.PostingNotFoundException;
import com.asbg.outboxlab.domain.outbox.FailureInjection;
import com.asbg.outboxlab.domain.outbox.OutboxEvent;
import com.asbg.outboxlab.domain.outbox.OutboxEventFactory;
import com.asbg.outboxlab.domain.outbox.OutboxEventRepository;
import com.asbg.outboxlab.domain.posting.Posting;
import com.asbg.outboxlab.domain.posting.PostingRepository;
import com.asbg.outboxlab.domain.statusreport.ReportStatus;
import com.asbg.outboxlab.domain.statusreport.StatusReport;
import com.asbg.outboxlab.domain.statusreport.StatusReportRepository;
import com.asbg.outboxlab.infrastructure.persistence.PostingCounterGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 이 세션의 핵심 메서드. "합/불 상태 등록이 15건째를 채우는 순간"을 원자적으로 가려내고,
 * 그 순간에만 SelectionAnnounced 이벤트를 같은 로컬 트랜잭션 안에서 outbox_events에
 * 함께 적재한다 (Outbox 패턴의 원자성 — StatusReport INSERT와 OutboxEvent INSERT가
 * 하나의 트랜잭션).
 *
 * 동시성의 핵심은 "15건을 채웠는지"를 애플리케이션 코드가 SELECT COUNT로 먼저 읽고
 * 판단하지 않는다는 것이다. PostingCounterGateway의 두 UPDATE(증가 → RETURNING,
 * 조건부 확정)가 각각 원자적 안전장치이고, 이 서비스는 그 결과값만 보고 분기한다.
 * "사전 조회는 동시성 안전장치가 아니다" — 여기서도 똑같이 적용된다.
 *
 * 로그는 일부러 분기마다 남겨뒀다 — 핸즈온에서 참가자들이 동시에 여러 요청을 쏴봤을 때,
 * "여러 요청이 count>=15를 동시에 봤지만 winner=true는 정확히 한 줄만 찍힌다"를
 * 로그로 직접 눈으로 확인하는 게 이 세션의 실습 포인트다.
 *
 * (레이어링 참고: PostingCounterGateway는 domain 포트 없이 infrastructure.persistence를
 * 직접 참조한다 — RETURNING을 쓰는 원자적 UPDATE는 JPA 포트로 감싸면 오히려 의미가
 * 죽기 때문에 의도적으로 둔 예외다. 나머지 저장소는 전부 domain 포트를 통해서만 접근한다.)
 */
@Service
public class StatusReportCommandService {

    private static final Logger log = LoggerFactory.getLogger(StatusReportCommandService.class);
    private static final int ANNOUNCEMENT_THRESHOLD = 15;

    private final PostingRepository postingRepository;
    private final StatusReportRepository statusReportRepository;
    private final PostingCounterGateway postingCounterGateway;
    private final OutboxEventFactory outboxEventFactory;
    private final OutboxEventRepository outboxEventRepository;

    public StatusReportCommandService(PostingRepository postingRepository,
                                       StatusReportRepository statusReportRepository,
                                       PostingCounterGateway postingCounterGateway,
                                       OutboxEventFactory outboxEventFactory,
                                       OutboxEventRepository outboxEventRepository) {
        this.postingRepository = postingRepository;
        this.statusReportRepository = statusReportRepository;
        this.postingCounterGateway = postingCounterGateway;
        this.outboxEventFactory = outboxEventFactory;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public StatusReportResult submit(SubmitStatusReportCommand command) {
        Posting posting = postingRepository.findById(command.postingId())
                .orElseThrow(() -> new PostingNotFoundException(command.postingId()));

        ReportStatus status = ReportStatus.valueOf(command.status());
        StatusReport report = StatusReport.create(
                posting.getId(), posting.getCurrentStage(), status, command.reporterId());
        StatusReport savedReport = statusReportRepository.save(report);
        log.info("상태 등록 저장: postingId={}, stage={}, status={}, reportId={}",
                posting.getId(), posting.getCurrentStage(), status, savedReport.getId());

        // PENDING은 기록만 되고, 15건 카운트에는 관여하지 않는다.
        if (!status.countsTowardThreshold()) {
            log.info("PENDING은 임계값 카운트에서 제외: postingId={}", posting.getId());
            return StatusReportResult.recorded(savedReport.getId());
        }

        int count = postingCounterGateway.incrementAndGetStageReportCount(
                posting.getId(), posting.getCurrentStage());
        log.info("누적 카운트 갱신: postingId={}, stage={}, count={}",
                posting.getId(), posting.getCurrentStage(), count);

        if (count < ANNOUNCEMENT_THRESHOLD) {
            return StatusReportResult.recorded(savedReport.getId());
        }

        // 15건을 넘긴 요청이 여러 개 동시에 들어와도, 아래 조건부 UPDATE는 딱 하나에게만
        // true를 돌려준다. 나머지는 "이미 발표됨"으로 조용히 끝난다 — 이게 이 세션의
        // "15번째 레이스"를 실제로 해결하는 지점이다.
        boolean isWinner = postingCounterGateway.tryMarkStageAnnounced(
                posting.getId(), posting.getCurrentStage());

        if (!isWinner) {
            log.info("이미 다른 요청이 발표를 확정함 — 이 요청은 조용히 종료: postingId={}, count={}",
                    posting.getId(), count);
            return StatusReportResult.recorded(savedReport.getId());
        }

        String correlationId = UUID.randomUUID().toString();
        FailureInjection failureInjection = FailureInjection.of(command.injectFailure());
        OutboxEvent event = outboxEventFactory.selectionAnnounced(posting, correlationId, failureInjection);
        OutboxEvent savedEvent = outboxEventRepository.save(event);
        log.info("15건 임계값 확정 — SelectionAnnounced 이벤트 적재: postingId={}, eventId={}, correlationId={}, injectFailure={}",
                posting.getId(), savedEvent.getEventId(), correlationId, failureInjection.isPresent());

        return StatusReportResult.announced(savedReport.getId(), savedEvent.getEventId(), correlationId);
    }
}
