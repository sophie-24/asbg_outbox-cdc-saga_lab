package com.asbg.outboxlab.infrastructure.persistence;

import com.asbg.outboxlab.domain.outbox.OutboxEvent;
import com.asbg.outboxlab.domain.outbox.OutboxEventRepository;
import org.springframework.stereotype.Repository;

@Repository
class OutboxEventRepositoryAdapter implements OutboxEventRepository {

    private final OutboxEventJpaRepository jpaRepository;

    OutboxEventRepositoryAdapter(OutboxEventJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public OutboxEvent save(OutboxEvent event) {
        return jpaRepository.save(event);
    }
}
