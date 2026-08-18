package com.asbg.outboxlab.domain.outbox;

import com.asbg.outboxlab.domain.posting.Posting;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * "이벤트 payload를 어떻게 만들 것인가"라는 책임 하나만 진다.
 */
@Component
public class OutboxEventFactory {

    private final ObjectMapper objectMapper;

    public OutboxEventFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OutboxEvent selectionAnnounced(Posting posting, String correlationId, FailureInjection failureInjection) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("postingId", posting.getId().toString());
        data.put("postingTitle", posting.getTitle());
        data.put("announcedStage", posting.getCurrentStage().name());
        data.put("injectFailure", failureInjection.isPresent() ? failureInjection.scenario() : "");

        return OutboxEvent.of(
                "posting",
                posting.getId(),
                EventType.SELECTION_ANNOUNCED,
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
