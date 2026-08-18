package com.asbg.outboxlab.application;

import com.asbg.outboxlab.application.dto.SubscribeCommand;
import com.asbg.outboxlab.domain.subscription.NotificationSubscription;
import com.asbg.outboxlab.domain.subscription.NotificationSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 알림 받기 신청만 담당한다. 실제 알림 발송은 이 서비스가 모른다 — NotifySubscribers
 * Lambda가 SelectionAnnounced 이벤트를 받아서 이 테이블을 조회하는 방식이라서,
 * Spring 애플리케이션은 "구독 정보를 정확히 저장한다"까지만 책임진다.
 */
@Service
public class SubscriptionCommandService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionCommandService.class);

    private final NotificationSubscriptionRepository subscriptionRepository;

    public SubscriptionCommandService(NotificationSubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional
    public UUID subscribe(SubscribeCommand command) {
        NotificationSubscription subscription =
                NotificationSubscription.create(command.postingId(), command.userId());
        NotificationSubscription saved = subscriptionRepository.save(subscription);
        log.info("알림 구독 등록: postingId={}, userId={}", command.postingId(), command.userId());
        return saved.getId();
    }
}
