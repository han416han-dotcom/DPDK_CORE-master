package com.dpdk.ai.knowledge.repository;

import com.dpdk.ai.knowledge.entity.KgRepairNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KgRepairNodeRepository extends JpaRepository<KgRepairNode, Long> {

    Optional<KgRepairNode> findByCode(String code);
}
