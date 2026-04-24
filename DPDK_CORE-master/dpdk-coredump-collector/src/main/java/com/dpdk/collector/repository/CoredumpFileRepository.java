package com.dpdk.collector.repository;

import com.dpdk.collector.entity.CoredumpFile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CoredumpFileRepository extends JpaRepository<CoredumpFile, Long> {
    boolean existsByFileHash(String fileHash);
}

