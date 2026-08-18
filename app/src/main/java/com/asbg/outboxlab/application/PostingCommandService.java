package com.asbg.outboxlab.application;

import com.asbg.outboxlab.application.dto.CreatePostingCommand;
import com.asbg.outboxlab.application.dto.PostingResult;
import com.asbg.outboxlab.domain.posting.Posting;
import com.asbg.outboxlab.domain.posting.PostingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공고 생성만 담당한다. 이 흐름에는 Outbox가 끼지 않는다 — 이 세션이 정합성을 다루는
 * 지점은 "상태 등록이 임계값을 넘기는 순간"이라서, 단순 생성까지 트랜잭셔널 이벤트로
 * 포장할 필요는 없다.
 */
@Service
public class PostingCommandService {

    private static final Logger log = LoggerFactory.getLogger(PostingCommandService.class);

    private final PostingRepository postingRepository;

    public PostingCommandService(PostingRepository postingRepository) {
        this.postingRepository = postingRepository;
    }

    @Transactional
    public PostingResult create(CreatePostingCommand command) {
        Posting posting = Posting.create(command.title());
        Posting saved = postingRepository.save(posting);
        log.info("공고 생성: postingId={}, title={}", saved.getId(), saved.getTitle());
        return new PostingResult(saved.getId(), saved.getTitle(), saved.getCurrentStage().name());
    }
}
