package com.asbg.outboxlab.application.dto;

import java.util.UUID;

public record PostingResult(UUID postingId, String title, String currentStage) {}
