package com.asbg.outboxlab.infrastructure.persistence;

import com.asbg.outboxlab.domain.statusreport.StatusReport;
import com.asbg.outboxlab.domain.statusreport.StatusReportRepository;
import org.springframework.stereotype.Repository;

@Repository
class StatusReportRepositoryAdapter implements StatusReportRepository {

    private final StatusReportJpaRepository jpaRepository;

    StatusReportRepositoryAdapter(StatusReportJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public StatusReport save(StatusReport report) {
        return jpaRepository.save(report);
    }
}
