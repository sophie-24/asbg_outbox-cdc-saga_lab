package com.asbg.outboxlab.infrastructure.persistence;

import com.asbg.outboxlab.domain.statusreport.StatusReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StatusReportJpaRepository extends JpaRepository<StatusReport, UUID> {
}
