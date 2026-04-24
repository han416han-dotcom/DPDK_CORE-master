package com.dpdk.ai.api;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class DiagnosisResult {
    Long fileId;
    String fileType;
    String faultCode;
    String faultName;
    double confidence;
    String summary;
    String suspectedRootCause;
    List<String> matchedCaseTags;
    List<String> rootCauseHints;
    List<DiagnosisSignal> extractedSignals;
    List<DiagnosisCategoryScore> categoryScores;
    List<TroubleshootingStep> troubleshootingSteps;
    HybridFeatureSnapshot featureSnapshot;
    List<RepairSuggestion> repairSuggestions;
}
