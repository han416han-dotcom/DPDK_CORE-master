package com.dpdk.ai.feature;

import com.dpdk.collector.entity.ParsedFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class HybridFeatureExtractionService {

    private final RuleBasedFeatureExtractor ruleBasedFeatureExtractor;
    private final CnnEmbeddingExtractor cnnEmbeddingExtractor;

    public HybridFeatureVector extract(ParsedFeature pf) {
        double[] rule = ruleBasedFeatureExtractor.extract(pf);
        String fingerprint = fingerprintOf(pf);
        double[] cnn = cnnEmbeddingExtractor.embed(rule, fingerprint);
        double[] combined = concat(rule, cnn);
        return HybridFeatureVector.builder()
                .ruleFeatures(rule)
                .cnnEmbedding(cnn)
                .combinedVector(combined)
                .build();
    }

    private static String fingerprintOf(ParsedFeature pf) {
        if (pf == null) {
            return "";
        }
        return String.valueOf(pf.getFileId())
                + "|" + pf.getFileType()
                + "|" + safe(pf.getCrashSignal())
                + "|" + safe(pf.getCallStack()).substring(0, Math.min(512, safe(pf.getCallStack()).length()));
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static double[] concat(double[] a, double[] b) {
        double[] out = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
