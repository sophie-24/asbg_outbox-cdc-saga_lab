package com.asbg.outboxlab.application.dto;

import java.util.UUID;

/**
 * 목록 조회 전용 DTO. postings + status_reports 집계를 단일 쿼리로 합쳐서 채운다
 * (PostingCounterGateway.listWithBreakdown 참고) — posting 수만큼 별도 집계 쿼리를
 * 날리면 N+1이 나기 때문에, 이 DTO 자체가 "한 번의 쿼리로 완성되는 모양"으로 설계됐다.
 */
public record PostingStatusView(
        UUID postingId,
        String title,
        String currentStage,
        int stageReportCount,
        boolean announced,
        long passCount,
        long failCount,
        long pendingCount
) {}
