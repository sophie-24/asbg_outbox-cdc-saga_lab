package com.asbg.outboxlab.domain.posting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * 애그리거트 루트. stageReportCount / stageAnnouncedAt는 이 엔티티의 필드로 "읽기용"으로만
 * 매핑해뒀다 — 실제 증가·확정은 PostingCounterGateway가 원자적 UPDATE로 직접 DB를 건드리고,
 * 이 엔티티를 통해 다시 save()하지 않는다. JPA 더티체킹과 원자적 UPDATE가 같은 트랜잭션에서
 * 서로 덮어쓰는 걸 막기 위한 의도적인 경계다.
 */
@Entity
@Table(name = "postings")
public class Posting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_stage", nullable = false, length = 20)
    private ApplicationStage currentStage;

    @Column(name = "stage_report_count", nullable = false)
    private int stageReportCount;

    @Column(name = "stage_announced_at")
    private Instant stageAnnouncedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Posting() {
        // JPA 전용
    }

    private Posting(String title) {
        this.title = title;
        this.currentStage = ApplicationStage.DOCUMENT;
        this.stageReportCount = 0;
        this.createdAt = Instant.now();
    }

    public static Posting create(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title은 비어 있을 수 없습니다");
        }
        return new Posting(title);
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public ApplicationStage getCurrentStage() {
        return currentStage;
    }
}
