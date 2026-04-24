package com.dpdk.collector.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class DpdkLogParserUtil {
    private static final Pattern ERROR_PATTERN = Pattern.compile("ERROR|error|Error|failed|FAILED|panic|segfault|invalid|unsupported");
    private static final Pattern MBUF_PATTERN = Pattern.compile("mbuf|rte_pktmbuf|mempool|rte_mempool", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEMORY_PATTERN = Pattern.compile("memory|hugepage|mempool|oom|no memory|cannot reserve memory", Pattern.CASE_INSENSITIVE);
    private static final Pattern THREAD_PATTERN = Pattern.compile("lcore|thread|lock|ring|atomic|race|contention", Pattern.CASE_INSENSITIVE);
    private static final Pattern DRIVER_PATTERN = Pattern.compile("pmd|mlx5|ixgbe|i40e|vfio|pci|ethdev|device|probe|driver", Pattern.CASE_INSENSITIVE);
    private static final Pattern EAL_PATTERN = Pattern.compile("EAL parameters: (.*)");

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogParseResult {
        @Builder.Default
        private List<String> errorKeywords = new ArrayList<>();
        @Builder.Default
        private List<String> mbufOperations = new ArrayList<>();
        @Builder.Default
        private List<String> memoryInfo = new ArrayList<>();
        @Builder.Default
        private List<String> threadInfo = new ArrayList<>();
        @Builder.Default
        private List<String> driverInfo = new ArrayList<>();
        private String ealParameters;
        private String rawContent;
    }

    public static LogParseResult parseLog(File logFile) throws Exception {
        LogParseResult result = new LogParseResult();
        String content = FileUtils.readFileToString(logFile, StandardCharsets.UTF_8);
        result.setRawContent(content);

        String[] lines = content.split("\n");
        for (String line : lines) {
            if (ERROR_PATTERN.matcher(line).find()) {
                result.getErrorKeywords().add(line);
            }
            if (MBUF_PATTERN.matcher(line).find()) {
                result.getMbufOperations().add(line);
            }
            if (MEMORY_PATTERN.matcher(line).find()) {
                result.getMemoryInfo().add(line);
            }
            if (THREAD_PATTERN.matcher(line).find()) {
                result.getThreadInfo().add(line);
            }
            if (DRIVER_PATTERN.matcher(line).find()) {
                result.getDriverInfo().add(line);
            }
            var ealMatcher = EAL_PATTERN.matcher(line);
            if (ealMatcher.find()) {
                result.setEalParameters(ealMatcher.group(1));
            }
        }

        return result;
    }
}
