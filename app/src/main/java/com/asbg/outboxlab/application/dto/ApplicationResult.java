package com.asbg.outboxlab.application.dto;

import java.util.UUID;

public record ApplicationResult(UUID applicationId, UUID eventId, String correlationId) {}
