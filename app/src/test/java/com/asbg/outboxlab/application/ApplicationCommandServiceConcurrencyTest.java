package com.asbg.outboxlab.application;

import com.asbg.outboxlab.application.dto.CreateApplicationCommand;
import com.asbg.outboxlab.application.exception.DuplicateRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "사전 조회는 동시성 안전장치가 아니다" — HAPHAP 카카오 로그인 사건과 똑같은 구조의
 * 경쟁 상태가 이 서비스에서도 재현되지 않는지 실제로 검증하는 테스트.
 *
 * 같은 idempotencyKey를 가진 요청 10개를 동시에 쏘고, 정확히 1개만 성공하고
 * 나머지 9개는 DuplicateRequestException으로 걸러지는지 확인한다.
 * (H2 대신 Testcontainers로 실제 Postgres unique 제약 동작을 그대로 검증한다.)
 */
@Testcontainers
@SpringBootTest
class ApplicationCommandServiceConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ApplicationCommandService commandService;

    @Test
    void 동일한_idempotencyKey로_동시_요청이_와도_하나만_생성된다() throws InterruptedException {
        String idempotencyKey = "duplicate-key-test";
        int threadCount = 10;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger duplicateCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    commandService.create(new CreateApplicationCommand("동시성 테스트", null, idempotencyKey));
                    successCount.incrementAndGet();
                } catch (DuplicateRequestException e) {
                    duplicateCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();               // 10개 스레드가 전부 대기선에 설 때까지 기다린 뒤
        startLatch.countDown();            // 동시에 출발시킨다 — 진짜 경쟁 상태를 만들기 위함
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(duplicateCount.get()).isEqualTo(threadCount - 1);
    }
}
