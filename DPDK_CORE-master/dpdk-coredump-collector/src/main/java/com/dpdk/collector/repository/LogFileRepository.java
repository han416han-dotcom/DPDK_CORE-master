package com.dpdk.collector.repository;

import com.dpdk.collector.entity.LogFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LogFileRepository extends JpaRepository<LogFile, Long> {
    boolean existsByFileHash(String fileHash);

}
