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
    private static final Pattern NO_EXECUTABLE_PATTERN = Pattern.compile("No executable file specified", Pattern.CASE_INSENSITIVE);
    private static final Pattern UNKNOWN_FRAME_PATTERN = Pattern.compile("#\\d+\\s+0x[0-9a-fA-F]+\\s+in\\s+\\?\\?\\s*\\(\\)");

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

    public static GdbParseResult parseCoredump(File coreFile, String programPath) throws Exception {
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
        StringBuilder rawOutputBuilder = new StringBuilder();

        ensureGdbAvailable();
        List<String> cmd = new ArrayList<>();
        cmd.add("gdb");
        if (programPath != null && !programPath.isBlank()) {
            cmd.add(programPath.trim());
        }
        cmd.add("--batch");
        cmd.add("--ex");
        cmd.add("bt");
        cmd.add("--ex");
        cmd.add("info registers");
        cmd.add("--ex");
        cmd.add("info threads");
        cmd.add("-c");
        cmd.add(coreFile.getAbsolutePath());

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            boolean inCallStack = false;
            boolean inRegisters = false;
            boolean inThreads = false;

            while ((line = reader.readLine()) != null) {
                rawOutputBuilder.append(line).append("\n");

                Matcher signalMatcher = CRASH_SIGNAL_PATTERN.matcher(line);
                if (signalMatcher.find()) {
                    result.setCrashSignal(signalMatcher.group(1));
                }

                Matcher addressMatcher = CRASH_ADDRESS_PATTERN.matcher(line);
                if (addressMatcher.find()) {
                    result.setCrashAddress(addressMatcher.group(1));
                }

                if (line.startsWith("#0")) {
                    inCallStack = true;
                    inRegisters = false;
                    inThreads = false;
                }

                if (line.startsWith("rax ")) {
                    inCallStack = false;
                    inRegisters = true;
                    inThreads = false;
                }

                if (line.startsWith("* ")) {
                    inCallStack = false;
                    inRegisters = false;
                    inThreads = true;
                }

                if (inCallStack && line.startsWith("#")) {
                    result.getCallStack().add(line);
                } else if (inRegisters && !line.isBlank()) {
                    result.getRegisters().add(line);
                } else if (inThreads && !line.isBlank()) {
                    result.getThreadInfo().add(line);
                }
            }
        }

        process.waitFor();
        result.setRawOutput(rawOutputBuilder.toString());

        String rawOutput = result.getRawOutput() == null ? "" : result.getRawOutput();
        if (result.getCallStack().isEmpty() && NO_EXECUTABLE_PATTERN.matcher(rawOutput).find()) {
            throw new IllegalStateException("gdb 未指定可执行文件，无法生成有效调用栈。请配置 dpdk.collector.parse.program-path 指向 core 对应的可执行文件（建议带符号）。");
        }
        if (hasOnlyUnknownFrames(result.getCallStack())) {
            throw new IllegalStateException(buildMissingSymbolMessage(programPath, coreFile.getAbsolutePath()));
        }

        return result;
    }

    public static boolean hasOnlyUnknownFrames(List<String> callStack) {
        if (callStack == null || callStack.isEmpty()) {
            return false;
        }
        int recognizedFrames = 0;
        for (String frame : callStack) {
            if (!UNKNOWN_FRAME_PATTERN.matcher(frame).find()) {
                recognizedFrames++;
            }
        }
        return recognizedFrames == 0;
    }

    public static String buildMissingSymbolMessage(String programPath, String corePath) {
        String executableHint = (programPath == null || programPath.isBlank())
                ? "当前未配置 dpdk.collector.parse.program-path，且未自动匹配到对应 ELF。"
                : "当前已传入/自动匹配的 program-path: " + programPath + "，但 gdb 仍未解析出函数名，请确认它就是生成该 core 的原始 ELF 可执行文件，且最好带调试符号。";
        return "gdb 已读取 core 文件，但调用栈几乎全部是 '?? ()'，说明缺少可用符号信息，当前 AI 分析结果不可信。"
                + " 请为该 core 提供匹配的原始可执行文件后重新解析。"
                + " " + executableHint
                + " core 路径: " + corePath;
    }

    private static void ensureGdbAvailable() {
        try {
            Process p = new ProcessBuilder("gdb", "--version")
                    .redirectErrorStream(true)
                    .start();
            int exit = p.waitFor();
            if (exit != 0) {
                throw new IllegalStateException("gdb --version 执行失败，退出码=" + exit);
            }
        } catch (Exception e) {
            throw new IllegalStateException("未找到可用的 gdb（请确认已安装并配置到 PATH）", e);
        }
    }
}
