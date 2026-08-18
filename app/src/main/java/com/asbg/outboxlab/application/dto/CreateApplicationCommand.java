package com.asbg.outboxlab.application.dto;

/**
 * injectFailure, idempotencyKey는 둘 다 nullable — 워크숍 UI의 "정상 생성" 버튼은
 * 이 둘을 안 보내고, "실패 주입" 버튼만 injectFailure를 채워 보낸다.
 */
public record CreateApplicationCommand(
        String applicantName,
        String injectFailure,
        String idempotencyKey
) {}
