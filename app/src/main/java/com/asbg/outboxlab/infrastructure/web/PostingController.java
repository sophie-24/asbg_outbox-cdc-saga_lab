package com.asbg.outboxlab.infrastructure.web;

import com.asbg.outboxlab.application.PostingCommandService;
import com.asbg.outboxlab.application.PostingQueryService;
import com.asbg.outboxlab.application.StatusReportCommandService;
import com.asbg.outboxlab.application.SubscriptionCommandService;
import com.asbg.outboxlab.application.dto.CreatePostingCommand;
import com.asbg.outboxlab.application.dto.PostingResult;
import com.asbg.outboxlab.application.dto.PostingStatusView;
import com.asbg.outboxlab.application.dto.StatusReportResult;
import com.asbg.outboxlab.application.dto.SubmitStatusReportCommand;
import com.asbg.outboxlab.application.dto.SubscribeCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * HTTP 매핑만 책임진다. 트랜잭션 경계, payload 조립, 동시성 판단은 전부 Service로
 * 위임한다 — 이 클래스에 @Transactional이 하나도 없는 게 정상이다.
 */
@RestController
@RequestMapping("/postings")
public class PostingController {

    private final PostingCommandService postingCommandService;
    private final StatusReportCommandService statusReportCommandService;
    private final SubscriptionCommandService subscriptionCommandService;
    private final PostingQueryService postingQueryService;

    public PostingController(PostingCommandService postingCommandService,
                              StatusReportCommandService statusReportCommandService,
                              SubscriptionCommandService subscriptionCommandService,
                              PostingQueryService postingQueryService) {
        this.postingCommandService = postingCommandService;
        this.statusReportCommandService = statusReportCommandService;
        this.subscriptionCommandService = subscriptionCommandService;
        this.postingQueryService = postingQueryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostingResult create(@Valid @RequestBody CreatePostingRequest request) {
        return postingCommandService.create(new CreatePostingCommand(request.title()));
    }

    @GetMapping
    public List<PostingStatusView> list() {
        return postingQueryService.listWithBreakdown();
    }

    @PostMapping("/{postingId}/status-reports")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusReportResult submitStatusReport(@PathVariable UUID postingId,
                                                  @Valid @RequestBody SubmitStatusReportRequest request) {
        return statusReportCommandService.submit(new SubmitStatusReportCommand(
                postingId, request.status(), request.reporterId(), request.injectFailure()));
    }

    @PostMapping("/{postingId}/subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public void subscribe(@PathVariable UUID postingId,
                           @Valid @RequestBody SubscribeRequest request) {
        subscriptionCommandService.subscribe(new SubscribeCommand(postingId, request.userId()));
    }

    public record CreatePostingRequest(@NotBlank String title) {}

    public record SubmitStatusReportRequest(
            @NotBlank String status,
            String reporterId,
            String injectFailure
    ) {}

    public record SubscribeRequest(@NotBlank String userId) {}
}
