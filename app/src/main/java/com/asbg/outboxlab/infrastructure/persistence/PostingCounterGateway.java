package com.asbg.outboxlab.infrastructure.persistence;

import com.asbg.outboxlab.application.dto.PostingStatusView;
import com.asbg.outboxlab.domain.posting.ApplicationStage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * "15번째를 누가 채웠는가"를 원자적으로 가리는 부분만큼은 JPA 엔티티 더티체킹에
 * 맡기지 않고 JdbcTemplate으로 직접 SQL을 날린다. JPA의 @Modifying은 UPDATE ...
 * RETURNING의 반환값을 받기에 적합하지 않고, count-then-update 패턴은 이 세션이
 * 처음부터 경고해온 그 함정(사전 조회는 안전장치가 아니다)을 그대로 재현하기 때문이다.
 */
@Component
public class PostingCounterGateway {

    private final JdbcTemplate jdbcTemplate;

    public PostingCounterGateway(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * PASS/FAIL 등록 하나를 원자적으로 반영하고, 반영 직후의 누적 카운트를 그대로 반환한다.
     * current_stage 조건은 카운트를 읽는 사이 단계가 넘어가버리는 경쟁까지 막아준다.
     */
    public int incrementAndGetStageReportCount(UUID postingId, ApplicationStage stage) {
        return jdbcTemplate.queryForObject("""
                UPDATE postings
                SET stage_report_count = stage_report_count + 1
                WHERE id = ? AND current_stage = ?
                RETURNING stage_report_count
                """, Integer.class, postingId, stage.name());
    }

    /**
     * 조건부 UPDATE로 "발표 권한"을 딱 한 트랜잭션에게만 준다.
     * 영향받은 row가 1이면 내가 승자, 0이면 이미 다른 트랜잭션이 처리했다는 뜻.
     */
    public boolean tryMarkStageAnnounced(UUID postingId, ApplicationStage stage) {
        int updated = jdbcTemplate.update("""
                UPDATE postings
                SET stage_announced_at = now()
                WHERE id = ? AND current_stage = ? AND stage_announced_at IS NULL
                """, postingId, stage.name());
        return updated == 1;
    }

    /**
     * 목록 조회용 집계. postings 행 수와 무관하게 쿼리 한 번으로 PASS/FAIL/PENDING
     * 분포까지 채운다 — posting마다 별도로 count 쿼리를 날리면 N+1이 난다.
     */
    public List<PostingStatusView> listWithBreakdown() {
        return jdbcTemplate.query("""
                SELECT p.id, p.title, p.current_stage, p.stage_report_count,
                       (p.stage_announced_at IS NOT NULL) AS announced,
                       COUNT(*) FILTER (WHERE sr.status = 'PASS') AS pass_count,
                       COUNT(*) FILTER (WHERE sr.status = 'FAIL') AS fail_count,
                       COUNT(*) FILTER (WHERE sr.status = 'PENDING') AS pending_count
                FROM postings p
                LEFT JOIN status_reports sr
                       ON sr.posting_id = p.id AND sr.stage = p.current_stage
                GROUP BY p.id, p.title, p.current_stage, p.stage_report_count, p.stage_announced_at
                ORDER BY p.created_at DESC
                """, (rs, rowNum) -> new PostingStatusView(
                UUID.fromString(rs.getString("id")),
                rs.getString("title"),
                rs.getString("current_stage"),
                rs.getInt("stage_report_count"),
                rs.getBoolean("announced"),
                rs.getLong("pass_count"),
                rs.getLong("fail_count"),
                rs.getLong("pending_count")
        ));
    }
}
