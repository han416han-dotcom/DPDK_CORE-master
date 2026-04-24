package com.dpdk.ai.feature;

import com.dpdk.collector.entity.ParsedFeature;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 规则引擎侧特征：长度、关键词密度、字段非空指示等，拼成固定维度向量。
 */
@Component
public class RuleBasedFeatureExtractor {

    public static final int RULE_DIM = 48;

    public double[] extract(ParsedFeature pf) {
        double[] v = new double[RULE_DIM];
        if (pf == null) {
            return v;
        }
        v[0] = pf.getFileType() == null ? 0 : ("COREDUMP".equalsIgnoreCase(pf.getFileType()) ? 1 : 0);
        v[1] = pf.getFileType() == null ? 0 : ("LOG".equalsIgnoreCase(pf.getFileType()) ? 1 : 0);
        v[2] = len(pf.getCallStack());
        v[3] = len(pf.getRegisters());
        v[4] = len(pf.getMbufOperations());
        v[5] = len(pf.getMemoryInfo());
        v[6] = len(pf.getThreadInfo());
        v[7] = len(pf.getEalParameters());
        v[8] = len(pf.getErrorKeywords());
        v[9] = len(pf.getRawContent());
        v[10] = keyword(pf.getCallStack(), "rte_pktmbuf");
        v[11] = keyword(pf.getCallStack(), "mbuf");
        v[12] = keyword(pf.getCallStack(), "rte_eal");
        v[13] = keyword(pf.getCallStack(), "pmd");
        v[14] = keyword(pf.getRawContent(), "error");
        v[15] = keyword(pf.getRawContent(), "failed");
        v[16] = pf.getCrashSignal() == null ? 0 : 1;
        v[17] = pf.getCrashAddress() == null ? 0 : 1;
        v[18] = normalizeHash(pf.getCrashSignal());
        v[19] = normalizeHash(pf.getCrashAddress());
        v[20] = keyword(pf.getRawContent(), "mempool");
        v[21] = keyword(pf.getRawContent(), "rte_mempool");
        v[22] = keyword(pf.getRawContent(), "refcnt");
        v[23] = keyword(pf.getRawContent(), "lcore");
        v[24] = keyword(pf.getRawContent(), "thread");
        v[25] = keyword(pf.getRawContent(), "lock");
        v[26] = keyword(pf.getRawContent(), "ring");
        v[27] = keyword(pf.getRawContent(), "atomic");
        v[28] = keyword(pf.getRawContent(), "hugepage");
        v[29] = keyword(pf.getRawContent(), "socket-mem");
        v[30] = keyword(pf.getRawContent(), "numa");
        v[31] = keyword(pf.getRawContent(), "vfio");
        v[32] = keyword(pf.getRawContent(), "pci");
        v[33] = keyword(pf.getRawContent(), "mlx5");
        v[34] = keyword(pf.getRawContent(), "ixgbe");
        v[35] = keyword(pf.getRawContent(), "driver");
        v[36] = keyword(pf.getRawContent(), "unsupported");
        v[37] = keyword(pf.getRawContent(), "invalid");
        v[38] = keyword(pf.getRawContent(), "queue");
        v[39] = keyword(pf.getRawContent(), "port");
        String blob = safe(pf.getCallStack()) + safe(pf.getRawContent()) + safe(pf.getThreadInfo());
        for (int i = 0; i < RULE_DIM - 40; i++) {
            v[40 + i] = ngramEnergy(blob, i);
        }
        return v;
    }

    private static double len(String s) {
        if (s == null) {
            return 0;
        }
        return Math.min(1.0, s.length() / 8000.0);
    }

    private static double keyword(String s, String k) {
        if (s == null || k == null) {
            return 0;
        }
        String low = s.toLowerCase(Locale.ROOT);
        int c = 0;
        int i = 0;
        while ((i = low.indexOf(k, i)) >= 0) {
            c++;
            i += k.length();
        }
        return Math.min(1.0, c / 20.0);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static double normalizeHash(String s) {
        if (s == null) {
            return 0;
        }
        return (s.hashCode() % 997) / 997.0;
    }

    private static double ngramEnergy(String blob, int shift) {
        if (blob.isEmpty()) {
            return 0;
        }
        int sum = 0;
        int step = 7 + (shift % 5);
        for (int i = shift % step; i + 1 < blob.length(); i += step) {
            sum += blob.charAt(i) ^ blob.charAt(i + 1);
        }
        return Math.tanh(sum / 400.0);
    }
}
