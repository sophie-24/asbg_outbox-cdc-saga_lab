package com.asbg.outboxlab.infrastructure.persistence;

import com.asbg.outboxlab.domain.outbox.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OutboxEventJpaRepository extends JpaRepository<OutboxEvent, UUID> {
}
