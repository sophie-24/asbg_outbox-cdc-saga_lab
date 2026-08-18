package com.asbg.outboxlab.domain.application;

import java.util.Optional;
import java.util.UUID;

/**
 * 도메인이 바라보는 저장소 포트. Spring Data JPA를 직접 참조하지 않는다 —
 * 나중에 저장소를 바꾸더라도(테스트에서 인메모리 구현으로 교체 등) 도메인/유스케이스
 * 코드는 이 인터페이스만 알면 되도록 하기 위함.
 */
public interface ApplicationRepository {

    Application save(Application application);

    Optional<Application> findByIdempotencyKey(String idempotencyKey);

    Optional<Application> findById(UUID id);
}
