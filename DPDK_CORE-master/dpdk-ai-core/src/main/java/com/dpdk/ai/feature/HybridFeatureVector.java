package com.dpdk.ai.feature;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class HybridFeatureVector {
    double[] ruleFeatures;
    double[] cnnEmbedding;
    double[] combinedVector;
}
