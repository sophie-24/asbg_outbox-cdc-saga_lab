package com.asbg.outboxlab.infrastructure.persistence;

import com.asbg.outboxlab.domain.subscription.NotificationSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationSubscriptionJpaRepository extends JpaRepository<NotificationSubscription, UUID> {
}
