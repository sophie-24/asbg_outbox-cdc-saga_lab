package com.asbg.outboxlab.infrastructure.persistence;

import com.asbg.outboxlab.application.dto.ApplicationStatusView;
import com.asbg.outboxlab.domain.application.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface ApplicationJpaRepository extends JpaRepository<Application, UUID> {

    Optional<Application> findByIdempotencyKey(String idempotencyKey);

    /**
     * OutboxEvent와 Application 사이에는 JPA 연관관계(@OneToMany/@ManyToOne)가 없다
     * (Outbox를 범용 테이블로 유지하기 위해 의도적으로 뺐다 — OutboxEvent 참고).
     * 그래서 이 조회는 매핑을 타고 가는 게 아니라, ON 절로 aggregate_id를 직접 매칭하는
     * JPQL 세타 조인을 쓴다. Hibernate 6부터 매핑되지 않은 엔티티 간 ON 조인을 지원한다.
     */
    @Query("""
            SELECT new com.asbg.outboxlab.application.dto.ApplicationStatusView(
                a.id, a.applicantName, a.createdAt, o.eventId, o.correlationId)
            FROM Application a
            LEFT JOIN OutboxEvent o ON o.aggregateId = a.id
            ORDER BY a.createdAt DESC
            """)
    java.util.List<ApplicationStatusView> findRecentWithLatestEvent();
}
