package com.dpdk.collector.service;

import com.dpdk.collector.entity.ParsedFeature;
import com.dpdk.collector.util.DpdkLogParserUtil;
import com.dpdk.collector.util.GdbParserUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

@Service
public class DataNormalizationService {

    private static final int MAX_TEXT_FIELD_LENGTH = 200_000;
    private static final int MAX_RAW_CONTENT_LENGTH = 1_000_000;

    public ParsedFeature normalizeCoredumpResult(GdbParserUtil.GdbParseResult result) {
        ParsedFeature feature = new ParsedFeature();

        feature.setCrashSignal(truncate(result.getCrashSignal(), 255));
        feature.setCrashAddress(truncate(result.getCrashAddress(), 255));
        feature.setCallStack(truncate(joinLines(result.getCallStack()), MAX_TEXT_FIELD_LENGTH));
        feature.setRegisters(truncate(joinLines(result.getRegisters()), MAX_TEXT_FIELD_LENGTH));

        List<String> mbufOps = result.getCallStack().stream()
                .filter(line -> containsAnyIgnoreCase(line, "rte_pktmbuf", "mbuf", "mempool", "rte_mempool"))
                .toList();
        feature.setMbufOperations(truncate(joinLines(mbufOps), MAX_TEXT_FIELD_LENGTH));

        List<String> memoryInfo = result.getCallStack().stream()
                .filter(line -> containsAnyIgnoreCase(line, "memcpy", "memset", "malloc", "free", "invalid", "segv", "bus error"))
                .toList();
        feature.setMemoryInfo(truncate(joinLines(memoryInfo), MAX_TEXT_FIELD_LENGTH));

        feature.setThreadInfo(truncate(joinLines(result.getThreadInfo()), MAX_TEXT_FIELD_LENGTH));
        feature.setErrorKeywords(truncate(extractErrorKeywords(result.getRawOutput()), MAX_TEXT_FIELD_LENGTH));
        feature.setRawContent(truncate(result.getRawOutput(), MAX_RAW_CONTENT_LENGTH));

        return feature;
    }

    public ParsedFeature normalizeLogResult(DpdkLogParserUtil.LogParseResult result) {
        ParsedFeature feature = new ParsedFeature();

        feature.setErrorKeywords(truncate(joinCommaSeparated(result.getErrorKeywords()), MAX_TEXT_FIELD_LENGTH));
        feature.setMbufOperations(truncate(joinLines(result.getMbufOperations()), MAX_TEXT_FIELD_LENGTH));
        feature.setMemoryInfo(truncate(joinLines(result.getMemoryInfo()), MAX_TEXT_FIELD_LENGTH));
        feature.setThreadInfo(truncate(joinLines(result.getThreadInfo()), MAX_TEXT_FIELD_LENGTH));

        String driverInfo = joinLines(result.getDriverInfo());
        String ealParameters = result.getEalParameters();
        if (driverInfo != null && !driverInfo.isBlank()) {
            ealParameters = (ealParameters == null || ealParameters.isBlank())
                    ? driverInfo
                    : ealParameters + "\n" + driverInfo;
        }
        feature.setEalParameters(truncate(ealParameters, MAX_TEXT_FIELD_LENGTH));
        feature.setRawContent(truncate(result.getRawContent(), MAX_RAW_CONTENT_LENGTH));

        return feature;
    }

    private String extractErrorKeywords(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return null;
        }
        List<String> matchedLines = new ArrayList<>();
        for (String line : rawOutput.split("\\R")) {
            if (containsAnyIgnoreCase(line, "error", "failed", "panic", "invalid", "unsupported", "segmentation fault", "assert")) {
                matchedLines.add(line.trim());
            }
        }
        return joinCommaSeparated(matchedLines);
    }

    private static boolean containsAnyIgnoreCase(String value, String... tokens) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (normalized.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String joinLines(List<String> lines) {
        StringJoiner joiner = new StringJoiner("\n");
        lines.forEach(joiner::add);
        String joined = joiner.toString();
        return joined.isBlank() ? null : joined;
    }

    private static String joinCommaSeparated(List<String> values) {
        StringJoiner joiner = new StringJoiner(", ");
        values.forEach(joiner::add);
        String joined = joiner.toString();
        return joined.isBlank() ? null : joined;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        String suffix = "\n... [truncated by platform]";
        int keepLength = Math.max(0, maxLength - suffix.length());
        return value.substring(0, keepLength) + suffix;
    }
}
