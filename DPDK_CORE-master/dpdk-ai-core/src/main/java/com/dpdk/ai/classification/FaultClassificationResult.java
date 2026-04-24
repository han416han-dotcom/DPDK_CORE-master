package com.dpdk.ai.classification;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
public class FaultClassificationResult {
    FaultCategory topCategory;
    double confidence;
    Map<FaultCategory, Double> scores;
}
