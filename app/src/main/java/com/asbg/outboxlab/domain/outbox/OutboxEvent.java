package com.asbg.outboxlab.domain.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 범용 Outbox 테이블에 대응하는 엔티티.
 *
 * 의도적으로 Application에 대한 @ManyToOne/FK를 걸지 않았다. Outbox는 "어떤 aggregate든
 * 이벤트가 생기면 기록되는" 범용 테이블이어야 하고, 특정 도메인에 종속되면 나중에 다른
 * aggregate(Payment 등)가 생길 때마다 새 Outbox 테이블을 또 만들어야 한다. aggregateId는
 * 그래서 그냥 UUID 컬럼이다.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected OutboxEvent() {
        // JPA 전용
    }

    private OutboxEvent(String aggregateType, UUID aggregateId, EventType eventType,
                         String payload, String correlationId) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.correlationId = correlationId;
        this.occurredAt = Instant.now();
    }

    public static OutboxEvent of(String aggregateType, UUID aggregateId, EventType eventType,
                                  String payload, String correlationId) {
        return new OutboxEvent(aggregateType, aggregateId, eventType, payload, correlationId);
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
