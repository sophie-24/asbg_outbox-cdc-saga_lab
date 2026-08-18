package com.asbg.outboxlab.infrastructure.persistence;

import com.asbg.outboxlab.domain.subscription.NotificationSubscription;
import com.asbg.outboxlab.domain.subscription.NotificationSubscriptionRepository;
import org.springframework.stereotype.Repository;

@Repository
class NotificationSubscriptionRepositoryAdapter implements NotificationSubscriptionRepository {

    private final NotificationSubscriptionJpaRepository jpaRepository;

    NotificationSubscriptionRepositoryAdapter(NotificationSubscriptionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public NotificationSubscription save(NotificationSubscription subscription) {
        return jpaRepository.save(subscription);
    }
}
