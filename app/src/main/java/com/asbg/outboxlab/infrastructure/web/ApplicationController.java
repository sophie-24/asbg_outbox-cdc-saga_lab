package com.asbg.outboxlab.infrastructure.web;

import com.asbg.outboxlab.application.ApplicationCommandService;
import com.asbg.outboxlab.application.ApplicationQueryService;
import com.asbg.outboxlab.application.dto.ApplicationResult;
import com.asbg.outboxlab.application.dto.ApplicationStatusView;
import com.asbg.outboxlab.application.dto.CreateApplicationCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HTTP 매핑만 책임진다. 트랜잭션 경계도, payload 조립도, 동시성 판단도 전부
 * Service/Domain으로 위임한다 — 이 클래스에 @Transactional이 단 하나도 없는 게 정상이다.
 */
@RestController
@RequestMapping("/applications")
public class ApplicationController {

    private final ApplicationCommandService commandService;
    private final ApplicationQueryService queryService;

    public ApplicationController(ApplicationCommandService commandService,
                                  ApplicationQueryService queryService) {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResult create(@Valid @RequestBody CreateApplicationRequest request,
                                     @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return commandService.create(new CreateApplicationCommand(
                request.applicantName(), request.injectFailure(), idempotencyKey));
    }

    @GetMapping
    public List<ApplicationStatusView> list() {
        return queryService.listRecent();
    }

    public record CreateApplicationRequest(
            @NotBlank String applicantName,
            String injectFailure
    ) {}
}
