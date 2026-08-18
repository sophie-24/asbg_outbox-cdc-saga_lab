package com.asbg.outboxlab.domain.outbox;

public interface OutboxEventRepository {
    OutboxEvent save(OutboxEvent event);
}
