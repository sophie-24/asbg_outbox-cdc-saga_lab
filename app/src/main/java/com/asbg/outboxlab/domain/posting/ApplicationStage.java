package com.asbg.outboxlab.domain.posting;

import java.util.Optional;

/**
 * 서류 > 면접 > 최종. 고정된 순서이며, 최종 단계는 다음 단계가 없다 —
 * AdvanceStage Lambda가 이 사실(다음 단계 없음)을 어떻게 처리할지는
 * Step Functions 쪽 책임이고, 이 enum은 "다음이 뭔지"만 답한다.
 */
public enum ApplicationStage {
    DOCUMENT, INTERVIEW, FINAL;

    public Optional<ApplicationStage> next() {
        return switch (this) {
            case DOCUMENT -> Optional.of(INTERVIEW);
            case INTERVIEW -> Optional.of(FINAL);
            case FINAL -> Optional.empty();
        };
    }
}
