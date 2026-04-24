package com.dpdk.ai.feature;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * 占位 CNN 嵌入：对规则向量 + 文本指纹做确定性非线性映射，便于联调。
 * 接入真实模型时实现同名接口并注册为 @Primary 即可。
 */
@Component
public class DeterministicCnnEmbeddingExtractor implements CnnEmbeddingExtractor {

    private final int embeddingDim;

    public DeterministicCnnEmbeddingExtractor(@Value("${dpdk.ai.cnn-embedding-dim:32}") int embeddingDim) {
        this.embeddingDim = Math.max(8, embeddingDim);
    }

    @Override
    public double[] embed(double[] ruleFeatures, String rawFingerprint) {
        double[] out = new double[embeddingDim];
        String seed = (rawFingerprint == null ? "" : rawFingerprint) + "|" + java.util.Arrays.toString(ruleFeatures);
        byte[] bytes = seed.getBytes(StandardCharsets.UTF_8);
        for (int d = 0; d < embeddingDim; d++) {
            CRC32 crc = new CRC32();
            crc.update(bytes);
            crc.update(d);
            long v = crc.getValue();
            double x = ((v >> 16) & 0xffff) / 65535.0 * 2.0 - 1.0;
            double y = (v & 0xffff) / 65535.0 * 2.0 - 1.0;
            out[d] = Math.tanh(x + 0.3 * y + (ruleFeatures.length > d % ruleFeatures.length ? ruleFeatures[d % ruleFeatures.length] : 0) * 0.05);
        }
        return out;
    }
}
