package com.asbg.outboxlab.application.dto;

import java.util.UUID;

public record StatusReportResult(
        UUID reportId,
        boolean announced,
        UUID eventId,
        String correlationId
) {
    public static StatusReportResult recorded(UUID reportId) {
        return new StatusReportResult(reportId, false, null, null);
    }

    public static StatusReportResult announced(UUID reportId, UUID eventId, String correlationId) {
        return new StatusReportResult(reportId, true, eventId, correlationId);
    }
}
