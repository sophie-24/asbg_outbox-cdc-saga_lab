package com.asbg.outboxlab.application.exception;

public class DuplicateRequestException extends RuntimeException {
    public DuplicateRequestException(String idempotencyKey) {
        super("이미 처리된 요청입니다: idempotencyKey=" + idempotencyKey);
    }
}
