package com.dpdk.ai.api;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HybridFeatureSnapshot {
    double[] ruleFeatures;
    double[] cnnEmbedding;
    double[] combinedVector;
}
