package com.asbg.outboxlab.domain.outbox;

import com.asbg.outboxlab.domain.application.Application;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * "이벤트 payload를 어떻게 만들 것인가"라는 책임 하나만 진다.
 * ApplicationCommandService가 이 로직까지 직접 들고 있으면 그 클래스는
 * "트랜잭션 조율"과 "payload 조립" 두 가지 책임을 갖게 되므로 분리했다 (SRP).
 */
@Component
public class OutboxEventFactory {

    private final ObjectMapper objectMapper;

    public OutboxEventFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OutboxEvent applicationCreated(Application application, String correlationId,
                                           FailureInjection failureInjection) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("applicationId", application.getId().toString());
        data.put("applicantName", application.getApplicantName());
        data.put("injectFailure", failureInjection.isPresent() ? failureInjection.scenario() : "");

        return OutboxEvent.of(
                "application",
                application.getId(),
                EventType.APPLICATION_CREATED,
                writePayload(data),
                correlationId
        );
    }

    private String writePayload(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new IllegalStateException("outbox payload 직렬화 실패", e);
        }
    }
}
