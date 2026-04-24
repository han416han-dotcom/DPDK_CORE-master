package com.dpdk.collector.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GdbParserUtil {
    private static final Pattern CRASH_SIGNAL_PATTERN = Pattern.compile("Program received signal (\\w+),");
    private static final Pattern CRASH_ADDRESS_PATTERN = Pattern.compile("0x([0-9a-fA-F]+) in ");
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GdbParseResult {
        private String crashSignal;
        private String crashAddress;
        @Builder.Default
        private List<String> callStack = new ArrayList<>();
        @Builder.Default
        private List<String> registers = new ArrayList<>();
        @Builder.Default
        private List<String> threadInfo = new ArrayList<>();
        private String rawOutput;
    }
    
    public static GdbParseResult parseCoredump(File coreFile) throws Exception {
        String osName = System.getProperty("os.name", "");
        if (osName.toLowerCase().contains("windows")) {
            throw new IllegalStateException(
                    "当前运行环境为 Windows，core 解析依赖 gdb 通常不可用。请在 Linux/WSL2 环境运行解析服务，或确保 gdb 可用并在 PATH 中。");
        }
        if (coreFile == null) {
            throw new IllegalArgumentException("coreFile 不能为空");
        }
        if (!coreFile.exists()) {
            throw new IllegalArgumentException("core 文件不存在: " + coreFile.getAbsolutePath());
        }
        if (!coreFile.isFile()) {
            throw new IllegalArgumentException("core 路径不是文件: " + coreFile.getAbsolutePath());
        }

        GdbParseResult result = new GdbParseResult();
        StringBuilder rawOutput = new StringBuilder();
        
        // 执行GDB命令解析Core文件
        ensureGdbAvailable();
        ProcessBuilder pb = new ProcessBuilder(
                "gdb", "--batch", "--ex", "bt", "--ex", "info registers",
                "--ex", "info threads", "-c", coreFile.getAbsolutePath()
        );
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
        
        String line;
        boolean inCallStack = false;
        boolean inRegisters = false;
        boolean inThreads = false;
        
        while ((line = reader.readLine()) != null) {
            rawOutput.append(line).append("\n");
            
            // 提取崩溃信号
            Matcher signalMatcher = CRASH_SIGNAL_PATTERN.matcher(line);
            if (signalMatcher.find()) {
                result.setCrashSignal(signalMatcher.group(1));
            }
            
            // 提取崩溃地址
            Matcher addressMatcher = CRASH_ADDRESS_PATTERN.matcher(line);
            if (addressMatcher.find()) {
                result.setCrashAddress(addressMatcher.group(1));
            }
            
            // 识别调用栈开始
            if (line.startsWith("#0")) {
                inCallStack = true;
                inRegisters = false;
                inThreads = false;
            }
            
            // 识别寄存器信息开始
            if (line.startsWith("rax ")) {
                inCallStack = false;
                inRegisters = true;
                inThreads = false;
            }
            
            // 识别线程信息开始
            if (line.startsWith("* ")) {
                inCallStack = false;
                inRegisters = false;
                inThreads = true;
            }
            
            // 收集对应信息
            if (inCallStack && line.startsWith("#")) {
                result.getCallStack().add(line);
            } else if (inRegisters && !line.isBlank()) {
                result.getRegisters().add(line);
            } else if (inThreads && !line.isBlank()) {
                result.getThreadInfo().add(line);
            }
        }
        
        process.waitFor();
        result.setRawOutput(rawOutput.toString());
        
        return result;
    }

    private static void ensureGdbAvailable() {
        try {
            Process p = new ProcessBuilder("gdb", "--version")
                    .redirectErrorStream(true)
                    .start();
            p.waitFor();
        } catch (Exception e) {
            throw new IllegalStateException("未找到可用的 gdb（请确认已安装并配置到 PATH）", e);
        }
    }
}