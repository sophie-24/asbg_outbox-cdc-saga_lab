package com.asbg.outboxlab.domain.posting;

import java.util.Optional;
import java.util.UUID;

public interface PostingRepository {
    Posting save(Posting posting);
    Optional<Posting> findById(UUID id);
}
