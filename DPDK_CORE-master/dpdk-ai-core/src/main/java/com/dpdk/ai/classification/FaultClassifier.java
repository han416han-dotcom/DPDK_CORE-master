package com.dpdk.ai.classification;

import com.dpdk.ai.feature.HybridFeatureVector;

public interface FaultClassifier {

    FaultClassificationResult classify(HybridFeatureVector vector, String crashSignal, String callStackText);
}
