package com.asbg.outboxlab.application.exception;

import java.util.UUID;

public class PostingNotFoundException extends RuntimeException {
    public PostingNotFoundException(UUID postingId) {
        super("공고를 찾을 수 없습니다: postingId=" + postingId);
    }
}
