package com.dpdk.ai.incremental.repository;

import com.dpdk.ai.incremental.entity.FaultCaseRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaultCaseRecordRepository extends JpaRepository<FaultCaseRecord, Long> {
}
