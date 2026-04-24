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

    public ParsedFeature normalizeCoredumpResult(GdbParserUtil.GdbParseResult result) {
        ParsedFeature feature = new ParsedFeature();

        feature.setCrashSignal(result.getCrashSignal());
        feature.setCrashAddress(result.getCrashAddress());
        feature.setCallStack(joinLines(result.getCallStack()));
        feature.setRegisters(joinLines(result.getRegisters()));

        List<String> mbufOps = result.getCallStack().stream()
                .filter(line -> containsAnyIgnoreCase(line, "rte_pktmbuf", "mbuf", "mempool", "rte_mempool"))
                .toList();
        feature.setMbufOperations(joinLines(mbufOps));

        List<String> memoryInfo = result.getCallStack().stream()
                .filter(line -> containsAnyIgnoreCase(line, "memcpy", "memset", "malloc", "free", "invalid", "segv", "bus error"))
                .toList();
        feature.setMemoryInfo(joinLines(memoryInfo));

        feature.setThreadInfo(joinLines(result.getThreadInfo()));
        feature.setErrorKeywords(extractErrorKeywords(result.getRawOutput()));
        feature.setRawContent(result.getRawOutput());

        return feature;
    }

    public ParsedFeature normalizeLogResult(DpdkLogParserUtil.LogParseResult result) {
        ParsedFeature feature = new ParsedFeature();

        feature.setErrorKeywords(joinCommaSeparated(result.getErrorKeywords()));
        feature.setMbufOperations(joinLines(result.getMbufOperations()));
        feature.setMemoryInfo(joinLines(result.getMemoryInfo()));
        feature.setThreadInfo(joinLines(result.getThreadInfo()));

        String driverInfo = joinLines(result.getDriverInfo());
        String ealParameters = result.getEalParameters();
        if (driverInfo != null && !driverInfo.isBlank()) {
            ealParameters = (ealParameters == null || ealParameters.isBlank())
                    ? driverInfo
                    : ealParameters + "\n" + driverInfo;
        }
        feature.setEalParameters(ealParameters);
        feature.setRawContent(result.getRawContent());

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
}
