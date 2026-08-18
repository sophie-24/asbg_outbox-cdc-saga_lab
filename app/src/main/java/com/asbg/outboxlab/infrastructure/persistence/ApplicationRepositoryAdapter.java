package com.asbg.outboxlab.infrastructure.persistence;

import com.asbg.outboxlab.domain.application.Application;
import com.asbg.outboxlab.domain.application.ApplicationRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 도메인 포트(ApplicationRepository)를 Spring Data JPA로 구현하는 어댑터.
 * 도메인·유스케이스 계층은 이 클래스의 존재 자체를 모른다 — 인터페이스만 주입받는다.
 */
@Repository
class ApplicationRepositoryAdapter implements ApplicationRepository {

    private final ApplicationJpaRepository jpaRepository;

    ApplicationRepositoryAdapter(ApplicationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Application save(Application application) {
        return jpaRepository.save(application);
    }

    @Override
    public Optional<Application> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public Optional<Application> findById(UUID id) {
        return jpaRepository.findById(id);
    }
}
