package com.asbg.outboxlab.infrastructure.persistence;

import com.asbg.outboxlab.domain.posting.Posting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PostingJpaRepository extends JpaRepository<Posting, UUID> {
}
