package com.dpdk.ai.core;

import com.dpdk.ai.api.DiagnosisCategoryScore;
import com.dpdk.ai.api.DiagnosisResult;
import com.dpdk.ai.api.DiagnosisSignal;
import com.dpdk.ai.api.HybridFeatureSnapshot;
import com.dpdk.ai.api.RepairSuggestion;
import com.dpdk.ai.api.TroubleshootingStep;
import com.dpdk.ai.classification.FaultCategory;
import com.dpdk.ai.classification.FaultClassificationResult;
import com.dpdk.ai.classification.FaultClassifier;
import com.dpdk.ai.feature.HybridFeatureExtractionService;
import com.dpdk.ai.feature.HybridFeatureVector;
import com.dpdk.ai.knowledge.KnowledgeGraphService;
import com.dpdk.collector.entity.ParsedFeature;
import com.dpdk.collector.repository.ParsedFeatureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FaultAnalysisEngine {

    private final ParsedFeatureRepository parsedFeatureRepository;
    private final HybridFeatureExtractionService hybridFeatureExtractionService;
    private final FaultClassifier faultClassifier;
    private final KnowledgeGraphService knowledgeGraphService;
    private final DpdkCaseKnowledgeService dpdkCaseKnowledgeService;

    public Optional<DiagnosisResult> diagnose(Long fileId, String fileType) {
        Optional<ParsedFeature> pfOpt = parsedFeatureRepository.findTopByFileIdAndFileTypeOrderByCreatedAtDesc(fileId, fileType);
        if (pfOpt.isEmpty()) {
            return Optional.empty();
        }
        ParsedFeature parsedFeature = pfOpt.get();
        HybridFeatureVector vector = hybridFeatureExtractionService.extract(parsedFeature);
        FaultClassificationResult classifierResult = faultClassifier.classify(vector, parsedFeature.getCrashSignal(), parsedFeature.getCallStack());

        DpdkDiagnosisContext context = dpdkCaseKnowledgeService.buildContext(
                parsedFeature,
                classifierResult.getScores(),
                classifierResult.getTopCategory(),
                classifierResult.getConfidence());

        List<RepairSuggestion> repairs = knowledgeGraphService.suggestRepairs(context.getTopCategory().getCode());
        if (repairs.isEmpty() && context.getTopCategory() == FaultCategory.UNKNOWN) {
            repairs = knowledgeGraphService.suggestRepairs(FaultCategory.MEMORY_FAULT.getCode());
        }

        List<String> rootCauseHints = dpdkCaseKnowledgeService.buildRootCauseHints(context);
        List<DiagnosisSignal> extractedSignals = dpdkCaseKnowledgeService.buildSignals(context);
        List<TroubleshootingStep> troubleshootingSteps = dpdkCaseKnowledgeService.buildTroubleshootingSteps(context);
        List<DiagnosisCategoryScore> categoryScores = buildCategoryScores(context.getCategoryScores());

        HybridFeatureSnapshot snap = HybridFeatureSnapshot.builder()
                .ruleFeatures(vector.getRuleFeatures())
                .cnnEmbedding(vector.getCnnEmbedding())
                .combinedVector(vector.getCombinedVector())
                .build();

        return Optional.of(DiagnosisResult.builder()
                .fileId(fileId)
                .fileType(fileType)
                .faultCode(context.getTopCategory().getCode())
                .faultName(context.getTopCategory().getDescription())
                .confidence(context.getConfidence())
                .summary(dpdkCaseKnowledgeService.buildSummary(context))
                .suspectedRootCause(dpdkCaseKnowledgeService.buildSuspectedRootCause(context))
                .matchedCaseTags(context.getMatchedCaseTags())
                .rootCauseHints(rootCauseHints)
                .extractedSignals(extractedSignals)
                .categoryScores(categoryScores)
                .troubleshootingSteps(troubleshootingSteps)
                .featureSnapshot(snap)
                .repairSuggestions(repairs)
                .build());
    }

    private List<DiagnosisCategoryScore> buildCategoryScores(Map<FaultCategory, Double> scoreMap) {
        return scoreMap.entrySet().stream()
                .sorted(Map.Entry.<FaultCategory, Double>comparingByValue(Comparator.reverseOrder()))
                .map(entry -> DiagnosisCategoryScore.builder()
                        .faultCode(entry.getKey().getCode())
                        .faultName(entry.getKey().getDescription())
                        .score(entry.getValue())
                        .build())
                .toList();
    }
}
