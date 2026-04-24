package com.dpdk.ai.knowledge.repository;

import com.dpdk.ai.knowledge.entity.KgFaultNode;
import com.dpdk.ai.knowledge.entity.KgFaultRepairEdge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KgFaultRepairEdgeRepository extends JpaRepository<KgFaultRepairEdge, Long> {

    List<KgFaultRepairEdge> findByFaultOrderByConfidenceDesc(KgFaultNode fault);
}
