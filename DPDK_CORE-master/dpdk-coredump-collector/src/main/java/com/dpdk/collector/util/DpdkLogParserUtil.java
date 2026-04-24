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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DpdkLogParserUtil {
    private static final Pattern ERROR_PATTERN = Pattern.compile("ERROR|error|Error");
    private static final Pattern MBUF_PATTERN = Pattern.compile("mbuf|rte_pktmbuf");
    private static final Pattern MEMORY_PATTERN = Pattern.compile("memory|hugepage|mempool");
    private static final Pattern EAL_PATTERN = Pattern.compile("EAL parameters: (.*)");
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LogParseResult {
        private List<String> errorKeywords = new ArrayList<>();
        private List<String> mbufOperations = new ArrayList<>();
        private List<String> memoryInfo = new ArrayList<>();
        private String ealParameters;
        private String rawContent;
    }
    
    public static LogParseResult parseLog(File logFile) throws Exception {
        LogParseResult result = new LogParseResult();
        String content = FileUtils.readFileToString(logFile, StandardCharsets.UTF_8);
        result.setRawContent(content);
        
        String[] lines = content.split("\n");
        
        for (String line : lines) {
            // 提取错误信息
            Matcher errorMatcher = ERROR_PATTERN.matcher(line);
            if (errorMatcher.find()) {
                result.getErrorKeywords().add(line);
            }
            
            // 提取mbuf相关操作
            Matcher mbufMatcher = MBUF_PATTERN.matcher(line);
            if (mbufMatcher.find()) {
                result.getMbufOperations().add(line);
            }
            
            // 提取内存相关信息
            Matcher memoryMatcher = MEMORY_PATTERN.matcher(line);
            if (memoryMatcher.find()) {
                result.getMemoryInfo().add(line);
            }
            
            // 提取EAL参数
            Matcher ealMatcher = EAL_PATTERN.matcher(line);
            if (ealMatcher.find()) {
                result.setEalParameters(ealMatcher.group(1));
            }
        }
        
        return result;
    }
}