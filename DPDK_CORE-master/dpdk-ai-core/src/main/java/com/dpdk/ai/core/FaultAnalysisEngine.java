package com.dpdk.ai.core;

import com.dpdk.ai.api.DiagnosisResult;
import com.dpdk.ai.api.HybridFeatureSnapshot;
import com.dpdk.ai.api.RepairSuggestion;
import com.dpdk.ai.classification.FaultClassificationResult;
import com.dpdk.ai.classification.FaultClassifier;
import com.dpdk.ai.feature.HybridFeatureExtractionService;
import com.dpdk.ai.feature.HybridFeatureVector;
import com.dpdk.ai.knowledge.KnowledgeGraphService;
import com.dpdk.collector.entity.ParsedFeature;
import com.dpdk.collector.repository.ParsedFeatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FaultAnalysisEngine {

    private final ParsedFeatureRepository parsedFeatureRepository;
    private final HybridFeatureExtractionService hybridFeatureExtractionService;
    private final FaultClassifier faultClassifier;
    private final KnowledgeGraphService knowledgeGraphService;

    public Optional<DiagnosisResult> diagnose(Long fileId, String fileType) {
        Optional<ParsedFeature> pfOpt = parsedFeatureRepository.findTopByFileIdAndFileTypeOrderByCreatedAtDesc(fileId, fileType);
        if (pfOpt.isEmpty()) {
            return Optional.empty();
        }
        ParsedFeature pf = pfOpt.get();
        HybridFeatureVector vec = hybridFeatureExtractionService.extract(pf);
        FaultClassificationResult cls = faultClassifier.classify(vec, pf.getCrashSignal(), pf.getCallStack());

        List<RepairSuggestion> repairs = knowledgeGraphService.suggestRepairs(cls.getTopCategory().getCode());
        if (repairs.isEmpty() && cls.getTopCategory().getCode().equals("UNKNOWN")) {
            repairs = knowledgeGraphService.suggestRepairs("MEMORY_FAULT");
        }

        List<String> hints = new ArrayList<>();
        hints.add(cls.getTopCategory().getDescription());
        if (pf.getCrashSignal() != null) {
            hints.add("崩溃信号: " + pf.getCrashSignal());
        }
        if (pf.getCrashAddress() != null) {
            hints.add("崩溃地址: " + pf.getCrashAddress());
        }

        HybridFeatureSnapshot snap = HybridFeatureSnapshot.builder()
                .ruleFeatures(vec.getRuleFeatures())
                .cnnEmbedding(vec.getCnnEmbedding())
                .combinedVector(vec.getCombinedVector())
                .build();

        return Optional.of(DiagnosisResult.builder()
                .fileId(fileId)
                .fileType(fileType)
                .faultCode(cls.getTopCategory().getCode())
                .faultName(cls.getTopCategory().getDescription())
                .confidence(cls.getConfidence())
                .rootCauseHints(hints)
                .featureSnapshot(snap)
                .repairSuggestions(repairs)
                .build());
    }
}
