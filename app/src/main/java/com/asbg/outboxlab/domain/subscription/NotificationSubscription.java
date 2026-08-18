package com.asbg.outboxlab.domain.subscription;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_subscriptions")
public class NotificationSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "posting_id", nullable = false)
    private UUID postingId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "subscribed_at", nullable = false)
    private Instant subscribedAt;

    protected NotificationSubscription() {
        // JPA 전용
    }

    private NotificationSubscription(UUID postingId, String userId) {
        this.postingId = postingId;
        this.userId = userId;
        this.subscribedAt = Instant.now();
    }

    public static NotificationSubscription create(UUID postingId, String userId) {
        return new NotificationSubscription(postingId, userId);
    }

    public UUID getId() {
        return id;
    }
}
