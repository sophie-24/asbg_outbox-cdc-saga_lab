package com.asbg.outboxlab.application.dto;

import java.util.UUID;

/**
 * injectFailure는 워크숍 실습에서만 채워진다. 이 값이 실제로 의미를 가지려면
 * 이 요청이 "15번째 임계값을 넘기는 그 요청"이어야 한다 — 아니면 그냥 기록되고 끝난다.
 */
public record SubmitStatusReportCommand(
        UUID postingId,
        String status,
        String reporterId,
        String injectFailure
) {}
