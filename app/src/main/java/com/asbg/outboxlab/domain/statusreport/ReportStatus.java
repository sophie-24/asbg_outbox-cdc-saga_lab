package com.asbg.outboxlab.domain.statusreport;

/**
 * PASS/FAIL만 15건 기준 카운트에 들어간다. PENDING은 기록은 되지만
 * "전형 발표" 판단에는 전혀 관여하지 않는다.
 */
public enum ReportStatus {
    PASS, FAIL, PENDING;

    public boolean countsTowardThreshold() {
        return this == PASS || this == FAIL;
    }
}
