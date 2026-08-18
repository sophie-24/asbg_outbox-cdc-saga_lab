package com.asbg.outboxlab.application.dto;

import java.util.UUID;

public record SubscribeCommand(UUID postingId, String userId) {}
