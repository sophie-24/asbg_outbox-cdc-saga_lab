package com.asbg.outboxlab.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 목록 조회 전용 DTO 프로젝션. Application 엔티티를 그대로 반환하지 않는 이유는
 * 엔티티를 그대로 노출하면 나중에 필드가 추가될 때마다 API 응답이 의도치 않게 바뀌기
 * 때문이기도 하고, 이 응답 자체가 "Application + 최신 OutboxEvent 요약"이라는
 * 별개의 조회 모델이라서다.
 */
public record ApplicationStatusView(
        UUID applicationId,
        String applicantName,
        Instant createdAt,
        UUID eventId,
        String correlationId
) {}
