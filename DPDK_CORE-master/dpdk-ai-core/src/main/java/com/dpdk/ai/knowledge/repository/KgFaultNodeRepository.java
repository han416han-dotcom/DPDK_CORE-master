package com.dpdk.ai.knowledge.repository;

import com.dpdk.ai.knowledge.entity.KgFaultNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KgFaultNodeRepository extends JpaRepository<KgFaultNode, Long> {

    Optional<KgFaultNode> findByCode(String code);
}
