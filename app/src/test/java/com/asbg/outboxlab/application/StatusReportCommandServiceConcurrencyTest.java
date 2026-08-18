package com.asbg.outboxlab.application;

import com.asbg.outboxlab.application.dto.CreatePostingCommand;
import com.asbg.outboxlab.application.dto.PostingResult;
import com.asbg.outboxlab.application.dto.StatusReportResult;
import com.asbg.outboxlab.application.dto.SubmitStatusReportCommand;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "사전 조회는 동시성 안전장치가 아니다" — 이번엔 15번째 상태 등록 경쟁에서 재현한다.
 *
 * 임계값(15건) 바로 앞(14건)까지는 순차적으로 채워두고, 그 이후 여러 요청을 동시에
 * 쏴서 "누가 15번째를 채웠는가"를 정확히 하나만 가려내는지 검증한다. 여러 스레드가
 * 동시에 count >= 15를 보게 되더라도, tryMarkStageAnnounced()의 조건부 UPDATE는
 * 그중 정확히 하나에게만 발표 권한(announced=true, outbox 이벤트 1건)을 준다.
 * (H2 대신 Testcontainers로 실제 Postgres에서의 동작을 그대로 검증한다.)
 */
@Testcontainers
@SpringBootTest
class StatusReportCommandServiceConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private PostingCommandService postingCommandService;

    @Autowired
    private StatusReportCommandService statusReportCommandService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 임계값_직전까지_채운_뒤_동시_등록이_들어와도_발표는_정확히_한번만_일어난다() throws InterruptedException {
        PostingResult posting = postingCommandService.create(new CreatePostingCommand("동시성 테스트 공고"));

        for (int i = 0; i < 14; i++) {
            statusReportCommandService.submit(
                    new SubmitStatusReportCommand(posting.postingId(), "PASS", "reporter-" + i, null));
        }

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<StatusReportResult> results = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            int index = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    results.add(statusReportCommandService.submit(new SubmitStatusReportCommand(
                            posting.postingId(), "PASS", "racer-" + index, null)));
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

        long announcedCount = results.stream().filter(StatusReportResult::announced).count();
        assertThat(announcedCount).isEqualTo(1);

        Integer outboxEventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = ?",
                Integer.class, posting.postingId());
        assertThat(outboxEventCount).isEqualTo(1);
    }
}
