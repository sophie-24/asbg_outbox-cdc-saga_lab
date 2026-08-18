package com.asbg.outboxlab.application;

import com.asbg.outboxlab.application.dto.ApplicationStatusView;
import com.asbg.outboxlab.infrastructure.persistence.ApplicationJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 쓰기(ApplicationCommandService)와 클래스를 분리한 이유: 쓰기의 관심사는
 * "트랜잭션 원자성"이고 읽기의 관심사는 "N+1 없이 얼마나 효율적으로 조회하는가"라서
 * 서로 다른 축의 책임이다. 하나의 클래스에 몰아넣으면 두 관심사가 뒤섞여 코드를
 * 읽는 사람이 "이 메서드가 왜 여기 있지"를 헷갈리게 된다.
 */
@Service
public class ApplicationQueryService {

    private final ApplicationJpaRepository repository;

    public ApplicationQueryService(ApplicationJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ApplicationStatusView> listRecent() {
        // 🚫 이렇게 짜면 N+1이 난다 (참가자 수만큼 outbox 조회 쿼리가 추가로 나간다):
        //
        //   List<Application> apps = applicationJpaRepository.findAll();
        //   return apps.stream()
        //       .map(app -> {
        //           OutboxEvent event = outboxEventJpaRepository.findByAggregateId(app.getId());
        //           return new ApplicationStatusView(...);
        //       })
        //       .toList();
        //
        // ✅ 대신 JPQL에서 한 번의 JOIN 쿼리로 DTO를 직접 조립한다 (아래 repository 참고).
        return repository.findRecentWithLatestEvent();
    }
}
