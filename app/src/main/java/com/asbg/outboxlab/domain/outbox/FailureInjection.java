package com.asbg.outboxlab.domain.outbox;

/**
 * 워크숍 실습용 실패 주입 값을 캡슐화한 값 객체.
 *
 * 중요: 이 값은 절대로 전역 환경변수나 서버 프로세스 단위 설정으로 두지 않는다.
 * 여러 참가자가 동시에 서로 다른 버튼(정상 생성 / 실패 주입)을 눌러도 서로의 요청에
 * 영향을 주면 안 되기 때문에, 반드시 "이 요청 하나에만" 실리는 값이어야 한다.
 * 그래서 이 값은 OutboxEvent의 payload 안에 실려서 Kinesis까지 함께 전파된다.
 */
public record FailureInjection(String scenario) {

    public static final FailureInjection NONE = new FailureInjection(null);

    public static FailureInjection of(String rawValue) {
        return (rawValue == null || rawValue.isBlank()) ? NONE : new FailureInjection(rawValue);
    }

    public boolean isPresent() {
        return scenario != null;
    }
}
