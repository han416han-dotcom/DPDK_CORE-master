package com.dpdk.ai.knowledge;

import com.dpdk.ai.api.RepairSuggestion;
import com.dpdk.ai.knowledge.entity.KgFaultNode;
import com.dpdk.ai.knowledge.entity.KgFaultRepairEdge;
import com.dpdk.ai.knowledge.repository.KgFaultNodeRepository;
import com.dpdk.ai.knowledge.repository.KgFaultRepairEdgeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgeGraphService {

    private final KgFaultNodeRepository faultNodeRepository;
    private final KgFaultRepairEdgeRepository edgeRepository;

    @Transactional(readOnly = true)
    public List<RepairSuggestion> suggestRepairs(String faultCode) {
        return faultNodeRepository.findByCode(faultCode)
                .map(this::mapEdges)
                .orElseGet(List::of);
    }

    private List<RepairSuggestion> mapEdges(KgFaultNode fault) {
        List<KgFaultRepairEdge> edges = edgeRepository.findByFaultOrderByConfidenceDesc(fault);
        return edges.stream().map(e -> RepairSuggestion.builder()
                        .repairCode(e.getRepair().getCode())
                        .title(e.getRepair().getTitle())
                        .steps(e.getRepair().getSteps())
                        .referenceUrl(e.getRepair().getReferenceUrl())
                        .confidence(e.getConfidence() == null ? 0 : e.getConfidence())
                        .build())
                .collect(Collectors.toList());
    }
}
