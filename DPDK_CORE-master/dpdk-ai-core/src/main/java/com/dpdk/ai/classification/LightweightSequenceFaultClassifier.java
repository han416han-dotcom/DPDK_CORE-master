package com.dpdk.ai.classification;

import com.dpdk.ai.feature.HybridFeatureVector;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * 轻量化“序列”分类器：用调用栈行序 + 规则打分模拟 LSTM 的时序归纳能力。
 * 后续可替换为 ONNX / Python 服务导出的真实 LSTM 输出。
 */
@Component
public class LightweightSequenceFaultClassifier implements FaultClassifier {

    private static final int MAX_FRAMES = 32;

    @Override
    public FaultClassificationResult classify(HybridFeatureVector vector, String crashSignal, String callStackText) {
        EnumMap<FaultCategory, Double> scores = new EnumMap<>(FaultCategory.class);
        for (FaultCategory c : FaultCategory.values()) {
            scores.put(c, 0.0);
        }

        String stack = callStackText == null ? "" : callStackText;
        String signal = crashSignal == null ? "" : crashSignal.toUpperCase();
        String lower = stack.toLowerCase();

        bump(scores, FaultCategory.MEMORY_FAULT, signal.contains("SIGSEGV") ? 4.0 : 0);
        bump(scores, FaultCategory.MEMORY_FAULT, signal.contains("SIGBUS") ? 3.5 : 0);
        bump(scores, FaultCategory.MBUF_FAULT, countToken(lower, "mbuf") * 1.2 + countToken(lower, "rte_pktmbuf") * 1.5);
        bump(scores, FaultCategory.EAL_INIT_FAULT, countToken(lower, "rte_eal") * 1.1 + countToken(lower, "hugepage") * 1.3);
        bump(scores, FaultCategory.DRIVER_PMD_FAULT, countToken(lower, "pmd") * 1.0 + countToken(lower, "eth_dev") * 1.0);

        int frames = countLines(stack);
        bump(scores, FaultCategory.MEMORY_FAULT, frames > 0 && lower.contains("memcpy") ? 1.0 : 0);
        bump(scores, FaultCategory.CONFIG_FAULT, countToken(lower, "invalid") * 0.8 + countToken(lower, "parameter") * 0.8);

        double[] combined = vector.getCombinedVector();
        if (combined.length > 0) {
            bump(scores, FaultCategory.MEMORY_FAULT, Math.abs(combined[0]) * 0.5);
            bump(scores, FaultCategory.MBUF_FAULT, Math.abs(combined[Math.min(5, combined.length - 1)]) * 0.4);
        }

        FaultCategory top = FaultCategory.UNKNOWN;
        double best = -1;
        for (Map.Entry<FaultCategory, Double> e : scores.entrySet()) {
            if (e.getValue() > best) {
                best = e.getValue();
                top = e.getKey();
            }
        }
        if (best <= 0) {
            top = FaultCategory.UNKNOWN;
            best = 0.1;
        }

        double sum = scores.values().stream().mapToDouble(Double::doubleValue).sum();
        double confidence = sum > 0 ? Math.min(0.99, best / sum) : 0.5;

        return FaultClassificationResult.builder()
                .topCategory(top)
                .confidence(confidence)
                .scores(scores)
                .build();
    }

    private static void bump(EnumMap<FaultCategory, Double> scores, FaultCategory cat, double delta) {
        scores.merge(cat, delta, Double::sum);
    }

    private static int countToken(String haystack, String needle) {
        if (haystack.isEmpty() || needle.isEmpty()) {
            return 0;
        }
        int c = 0;
        int i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) {
            c++;
            i += needle.length();
        }
        return c;
    }

    private static int countLines(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        int n = 1;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                n++;
            }
        }
        return Math.min(n, MAX_FRAMES);
    }

    @SuppressWarnings("unused")
    private static void tokenizeFrames(String stack, java.util.function.Consumer<String> consumer) {
        StringTokenizer st = new StringTokenizer(stack, "\n");
        int n = 0;
        while (st.hasMoreTokens() && n++ < MAX_FRAMES) {
            consumer.accept(st.nextToken());
        }
    }
}
