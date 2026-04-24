package com.dpdk.collector.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WslGdbParserUtil {
    private static final Pattern CRASH_SIGNAL_PATTERN = Pattern.compile("Program received signal (\\w+),");
    private static final Pattern CRASH_ADDRESS_PATTERN = Pattern.compile("0x([0-9a-fA-F]+) in ");

    public static GdbParserUtil.GdbParseResult parseCoredumpViaWsl(File coreFile) throws Exception {
        if (coreFile == null) {
            throw new IllegalArgumentException("coreFile 不能为空");
        }
        if (!coreFile.exists()) {
            throw new IllegalArgumentException("core 文件不存在: " + coreFile.getAbsolutePath());
        }

        String osName = System.getProperty("os.name", "");
        if (!osName.toLowerCase().contains("windows")) {
            throw new IllegalStateException("WSL2 解析仅适用于 Windows 环境");
        }

        String wslCorePath = WslPathUtil.windowsToWslMountPath(coreFile.getAbsolutePath());

        ensureWslAvailable();

        GdbParserUtil.GdbParseResult result = new GdbParserUtil.GdbParseResult();
        StringBuilder rawOutput = new StringBuilder();

        List<String> cmd = new ArrayList<>();
        cmd.add("wsl");
        cmd.add("gdb");
        cmd.add("--batch");
        cmd.add("--ex");
        cmd.add("bt");
        cmd.add("--ex");
        cmd.add("info registers");
        cmd.add("--ex");
        cmd.add("info threads");
        cmd.add("-c");
        cmd.add(wslCorePath);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        String line;
        boolean inCallStack = false;
        boolean inRegisters = false;
        boolean inThreads = false;

        while ((line = reader.readLine()) != null) {
            rawOutput.append(line).append("\n");

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

        int exit = process.waitFor();
        result.setRawOutput(rawOutput.toString());
        if (exit != 0) {
            throw new IllegalStateException("WSL gdb 解析失败，退出码=" + exit);
        }
        return result;
    }

    private static void ensureWslAvailable() {
        try {
            Process p = new ProcessBuilder("wsl", "--status")
                    .redirectErrorStream(true)
                    .start();
            p.waitFor();
        } catch (Exception e) {
            throw new IllegalStateException("未检测到可用的 WSL2（请先安装并启用 WSL2）", e);
        }
    }
}

