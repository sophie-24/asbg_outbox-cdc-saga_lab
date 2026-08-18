package com.asbg.outboxlab.domain.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * 애그리거트 루트. 이 데모 규모에서는 도메인 엔티티와 JPA 엔티티를 완전히 분리하지 않고
 * (엄격한 헥사고날 아키텍처라면 분리한다) 실용적으로 하나로 합쳤다 — 대신 아래 두 원칙은 지킨다.
 *   1) public setter를 두지 않는다. 상태 변화는 반드시 도메인 메서드(create 등)를 통해서만.
 *   2) 생성 규칙(빈 이름 금지 등)은 서비스가 아니라 이 클래스가 스스로 검증한다.
 */
@Entity
@Table(name = "applications")
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "applicant_name", nullable = false, length = 100)
    private String applicantName;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    /**
     * HAPHAP 배치 Lost Update 콜백: 이 데모 흐름(생성만)에는 수정 경로가 없어서 지금 당장
     * 쓰이진 않지만, 나중에 "지원 일정 수정" 기능이 붙는 순간 동시 수정 유실을 막아주는
     * 낙관적 락 자리를 미리 비워두지 않는 건 태만이라고 판단해서 처음부터 넣어둔다.
     */
    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Application() {
        // JPA 전용
    }

    private Application(String applicantName, String idempotencyKey) {
        this.applicantName = applicantName;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = Instant.now();
    }

    public static Application create(String applicantName, String idempotencyKey) {
        if (applicantName == null || applicantName.isBlank()) {
            throw new IllegalArgumentException("applicantName은 비어 있을 수 없습니다");
        }
        return new Application(applicantName, idempotencyKey);
    }

    public UUID getId() {
        return id;
    }

    public String getApplicantName() {
        return applicantName;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
