package com.asbg.outboxlab.application;

import com.asbg.outboxlab.application.dto.ApplicationResult;
import com.asbg.outboxlab.application.dto.CreateApplicationCommand;
import com.asbg.outboxlab.application.exception.DuplicateRequestException;
import com.asbg.outboxlab.domain.application.Application;
import com.asbg.outboxlab.domain.application.ApplicationRepository;
import com.asbg.outboxlab.domain.outbox.FailureInjection;
import com.asbg.outboxlab.domain.outbox.OutboxEvent;
import com.asbg.outboxlab.domain.outbox.OutboxEventFactory;
import com.asbg.outboxlab.domain.outbox.OutboxEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 이 세션 전체가 증명하려는 그 문장이 코드로 존재하는 클래스.
 *
 * 이 클래스가 지는 책임은 정확히 하나: "지원 일정 저장"과 "Outbox 이벤트 기록"을
 * 하나의 로컬 트랜잭션으로 묶는 것. 캘린더 등록, 리마인더 생성, Saga 보상은
 * 이 클래스가 전혀 모른다 — 그건 CDC 건너편의 Step Functions 책임이다.
 */
@Service
public class ApplicationCommandService {

    private final ApplicationRepository applicationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventFactory outboxEventFactory;

    public ApplicationCommandService(ApplicationRepository applicationRepository,
                                      OutboxEventRepository outboxEventRepository,
                                      OutboxEventFactory outboxEventFactory) {
        this.applicationRepository = applicationRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.outboxEventFactory = outboxEventFactory;
    }

    /**
     * 기본 전파(REQUIRED)·기본 격리수준(Postgres READ_COMMITTED)을 그대로 쓴다.
     * 이 메서드 안에서는 "읽은 값을 기준으로 다시 판단"하는 게 idempotencyKey 사전 조회뿐이고,
     * 그 판단은 아래에서 보듯 DB 유니크 제약이 최종 승인권을 갖기 때문에 격리수준을 더 세게
     * 올릴 필요가 없다. 이 메서드는 반드시 Spring이 만든 프록시를 통해 호출돼야 @Transactional이
     * 걸린다 — 같은 클래스 안에서 this.create(...)처럼 자기 자신을 직접 호출하면 프록시를
     * 우회해 트랜잭션이 아예 안 걸리니 주의.
     */
    @Transactional
    public ApplicationResult create(CreateApplicationCommand command) {

        // 1) 빠른 경로: 사전 조회로 "이미 처리된 게 뻔한" 요청은 예외 비용 없이 먼저 걸러낸다.
        //    단, 이 조회는 안전장치가 아니라 그냥 성능 최적화다 — 진짜 안전장치는 2)의 DB 제약.
        if (command.idempotencyKey() != null) {
            applicationRepository.findByIdempotencyKey(command.idempotencyKey())
                    .ifPresent(existing -> {
                        throw new DuplicateRequestException(command.idempotencyKey());
                    });
        }

        Application application = Application.create(command.applicantName(), command.idempotencyKey());

        // 2) 진짜 안전장치: 동시에 같은 idempotencyKey로 들어온 두 트랜잭션 중 하나는
        //    여기서 unique 제약 위반으로 반드시 걸린다. "사전 조회는 동시성 안전장치가
        //    아니다"라는 이 세션 오프닝의 그 교훈이, 여기서도 똑같이 적용된다.
        try {
            applicationRepository.save(application);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateRequestException(command.idempotencyKey());
        }

        String correlationId = "workshop-" + UUID.randomUUID();
        FailureInjection failureInjection = FailureInjection.of(command.injectFailure());

        OutboxEvent event = outboxEventFactory.applicationCreated(application, correlationId, failureInjection);
        outboxEventRepository.save(event);

        return new ApplicationResult(application.getId(), event.getEventId(), correlationId);
    }
}
