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
    List<String> rootCauseHints;
    HybridFeatureSnapshot featureSnapshot;
    List<RepairSuggestion> repairSuggestions;
}
