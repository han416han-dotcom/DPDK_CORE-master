package com.dpdk.ai.feature;

public interface CnnEmbeddingExtractor {

    double[] embed(double[] ruleFeatures, String rawFingerprint);
}
