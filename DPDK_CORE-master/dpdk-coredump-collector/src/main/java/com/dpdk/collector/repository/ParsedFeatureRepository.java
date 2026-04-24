package com.dpdk.collector.repository;

import com.dpdk.collector.entity.ParsedFeature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ParsedFeatureRepository extends JpaRepository<ParsedFeature, Long> {

    Optional<ParsedFeature> findTopByFileIdAndFileTypeOrderByCreatedAtDesc(Long fileId, String fileType);
}
