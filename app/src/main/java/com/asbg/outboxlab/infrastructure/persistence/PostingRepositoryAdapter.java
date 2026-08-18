package com.asbg.outboxlab.infrastructure.persistence;

import com.asbg.outboxlab.domain.posting.Posting;
import com.asbg.outboxlab.domain.posting.PostingRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class PostingRepositoryAdapter implements PostingRepository {

    private final PostingJpaRepository jpaRepository;

    PostingRepositoryAdapter(PostingJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Posting save(Posting posting) {
        return jpaRepository.save(posting);
    }

    @Override
    public Optional<Posting> findById(UUID id) {
        return jpaRepository.findById(id);
    }
}
