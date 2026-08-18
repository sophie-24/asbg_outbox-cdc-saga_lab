package com.asbg.outboxlab.application;

import com.asbg.outboxlab.application.dto.PostingStatusView;
import com.asbg.outboxlab.domain.statusreport.ReportStatus;
import com.asbg.outboxlab.infrastructure.persistence.PostingCounterGateway;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 읽기 전용. PostingCounterGateway.listWithBreakdown()이 이미 JOIN + FILTER 집계로
 * 한 번에 완성된 결과를 주기 때문에, 이 서비스는 그걸 그대로 통과시키는 역할만 한다.
 * (N+1이 나는 "잘못된 버전"은 이 클래스 하단에 주석으로만 남겨뒀다 — 실제로 호출되지 않는다.)
 */
@Service
public class PostingQueryService {

    private final PostingCounterGateway postingCounterGateway;

    public PostingQueryService(PostingCounterGateway postingCounterGateway) {
        this.postingCounterGateway = postingCounterGateway;
    }

    public List<PostingStatusView> listWithBreakdown() {
        return postingCounterGateway.listWithBreakdown();
    }

    // 잘못된 버전(N+1) — postings를 findAll()로 가져온 뒤, posting 하나당
    // PASS/FAIL/PENDING 개수를 매번 별도 쿼리로 세는 방식. posting이 N개면
    // 쿼리가 총 1 + 3N번 나간다.
    //
    // public List<PostingStatusView> listWithBreakdown_WRONG(PostingJpaRepository postingRepo,
    //                                                          StatusReportJpaRepository reportRepo) {
    //     return postingRepo.findAll().stream()
    //             .map(p -> {
    //                 long passCount = reportRepo.countByPostingIdAndStageAndStatus(
    //                         p.getId(), p.getCurrentStage(), ReportStatus.PASS);
    //                 long failCount = reportRepo.countByPostingIdAndStageAndStatus(
    //                         p.getId(), p.getCurrentStage(), ReportStatus.FAIL);
    //                 long pendingCount = reportRepo.countByPostingIdAndStageAndStatus(
    //                         p.getId(), p.getCurrentStage(), ReportStatus.PENDING);
    //                 return new PostingStatusView(p.getId(), p.getTitle(),
    //                         p.getCurrentStage().name(), p.getStageReportCount(),
    //                         p.getStageAnnouncedAt() != null, passCount, failCount, pendingCount);
    //             })
    //             .toList();
    // }
}
