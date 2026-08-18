package com.asbg.outboxlab.domain.statusreport;

import com.asbg.outboxlab.domain.posting.ApplicationStage;
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
 * 등록 시점의 stage를 그대로 박제해둔다. 나중에 posting.currentStage가 넘어가도
 * "이 사람은 서류 단계에 합격을 눌렀다"는 사실 자체는 안 바뀌어야 하기 때문.
 */
@Entity
@Table(name = "status_reports")
public class StatusReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "posting_id", nullable = false)
    private UUID postingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStage stage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    @Column(name = "reporter_id", length = 100)
    private String reporterId;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    protected StatusReport() {
        // JPA 전용
    }

    private StatusReport(UUID postingId, ApplicationStage stage, ReportStatus status, String reporterId) {
        this.postingId = postingId;
        this.stage = stage;
        this.status = status;
        this.reporterId = reporterId;
        this.reportedAt = Instant.now();
    }

    public static StatusReport create(UUID postingId, ApplicationStage stage, ReportStatus status, String reporterId) {
        return new StatusReport(postingId, stage, status, reporterId);
    }

    public UUID getId() {
        return id;
    }

    public ReportStatus getStatus() {
        return status;
    }
}
