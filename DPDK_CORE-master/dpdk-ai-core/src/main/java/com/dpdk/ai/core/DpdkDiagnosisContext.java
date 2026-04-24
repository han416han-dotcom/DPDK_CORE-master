package com.dpdk.ai.core;

import com.dpdk.ai.classification.FaultCategory;
import com.dpdk.collector.entity.ParsedFeature;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder
public class DpdkDiagnosisContext {
    ParsedFeature parsedFeature;
    FaultCategory topCategory;
    double confidence;
    Map<FaultCategory, Double> categoryScores;
    List<String> matchedCaseTags;
    List<String> extractedSignals;
    List<String> stackFrames;
    String normalizedText;
}
