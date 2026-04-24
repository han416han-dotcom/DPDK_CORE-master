package com.dpdk.collector.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WslGdbParserUtil {
    private static final Pattern CRASH_SIGNAL_PATTERN = Pattern.compile("Program received signal (\\w+),");
    private static final Pattern CRASH_ADDRESS_PATTERN = Pattern.compile("0x([0-9a-fA-F]+) in ");
    private static final Pattern NO_EXECUTABLE_PATTERN = Pattern.compile("No executable file specified", Pattern.CASE_INSENSITIVE);

    public static GdbParserUtil.GdbParseResult parseCoredumpViaWsl(File coreFile, String programPath) throws Exception {
        if (coreFile == null) {
            throw new IllegalArgumentException("coreFile 不能为空");
        }
        if (!coreFile.exists()) {
            throw new IllegalArgumentException("core 文件不存在: " + coreFile.getAbsolutePath());
        }
        if (!coreFile.isFile()) {
            throw new IllegalArgumentException("core 路径不是文件: " + coreFile.getAbsolutePath());
        }

        String osName = System.getProperty("os.name", "").toLowerCase();
        boolean runningOnWindows = osName.contains("windows");
        boolean runningOnLinux = osName.contains("linux");

        if (runningOnLinux) {
            return parseInsideWslLinux(coreFile, programPath);
        }
        if (runningOnWindows) {
            return parseFromWindowsViaWsl(coreFile, programPath);
        }
        throw new IllegalStateException("当前操作系统不支持 WSL2_GDB 解析: " + System.getProperty("os.name", "unknown"));
    }

    private static GdbParserUtil.GdbParseResult parseInsideWslLinux(File coreFile, String programPath) throws Exception {
        ensureGdbAvailableInLinux();
        String corePath = WslPathUtil.normalizeForWsl(coreFile.getAbsolutePath());
        String normalizedProgramPath = WslPathUtil.normalizeForWsl(programPath);

        List<String> cmd = new ArrayList<>();
        cmd.add("gdb");
        if (normalizedProgramPath != null && !normalizedProgramPath.isBlank()) {
            cmd.add(normalizedProgramPath);
        }
        cmd.add("--batch");
        cmd.add("--ex");
        cmd.add("bt");
        cmd.add("--ex");
        cmd.add("info registers");
        cmd.add("--ex");
        cmd.add("info threads");
        cmd.add("-c");
        cmd.add(corePath);
        return executeAndParse(cmd, "WSL2/Linux gdb");
    }

    private static GdbParserUtil.GdbParseResult parseFromWindowsViaWsl(File coreFile, String programPath) throws Exception {
        ensureWslAvailable();
        String wslCorePath = WslPathUtil.normalizeForWsl(coreFile.getAbsolutePath());
        String normalizedProgramPath = WslPathUtil.normalizeForWsl(programPath);

        List<String> cmd = new ArrayList<>();
        cmd.add("wsl");
        cmd.add("gdb");
        if (normalizedProgramPath != null && !normalizedProgramPath.isBlank()) {
            cmd.add(normalizedProgramPath);
        }
        cmd.add("--batch");
        cmd.add("--ex");
        cmd.add("bt");
        cmd.add("--ex");
        cmd.add("info registers");
        cmd.add("--ex");
        cmd.add("info threads");
        cmd.add("-c");
        cmd.add(wslCorePath);
        return executeAndParse(cmd, "Windows -> WSL gdb");
    }

    private static GdbParserUtil.GdbParseResult executeAndParse(List<String> cmd, String modeLabel) throws Exception {
        GdbParserUtil.GdbParseResult result = new GdbParserUtil.GdbParseResult();
        StringBuilder rawOutput = new StringBuilder();

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
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

                if (line.startsWith("rax ") || line.startsWith("eax ") || line.startsWith("x0 ")) {
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

        int exit = process.waitFor();
        result.setRawOutput(rawOutput.toString());
        if (exit != 0) {
            throw new IllegalStateException(buildDetailedError(modeLabel, exit, result.getRawOutput()));
        }

        String normalizedOutput = result.getRawOutput() == null ? "" : result.getRawOutput();
        if (result.getCallStack().isEmpty() && NO_EXECUTABLE_PATTERN.matcher(normalizedOutput).find()) {
            throw new IllegalStateException(modeLabel + " 未指定可执行文件，无法生成有效调用栈。请配置 dpdk.collector.parse.program-path 指向 core 对应的可执行文件（建议使用 WSL2 可访问路径，如 /mnt/d/...）。");
        }
        if (result.getCallStack().isEmpty()) {
            throw new IllegalStateException(modeLabel + " 已执行，但未解析出调用栈。请检查 core 是否有效、gdb 是否可用，以及 program-path 是否指向对应可执行文件。");
        }
        if (GdbParserUtil.hasOnlyUnknownFrames(result.getCallStack())) {
            throw new IllegalStateException(modeLabel + " " + GdbParserUtil.buildMissingSymbolMessage(programPathForMessage(cmd), "命令已成功执行"));
        }
        return result;
    }

    private static String programPathForMessage(List<String> cmd) {
        if (cmd == null || cmd.isEmpty()) {
            return null;
        }
        int gdbIndex = cmd.indexOf("gdb");
        if (gdbIndex < 0) {
            return null;
        }
        int candidateIndex = gdbIndex + 1;
        if (candidateIndex >= cmd.size()) {
            return null;
        }
        String candidate = cmd.get(candidateIndex);
        if (candidate.startsWith("--") || "-c".equals(candidate)) {
            return null;
        }
        return candidate;
    }

    private static String buildDetailedError(String modeLabel, int exit, String rawOutput) {
        String normalizedOutput = rawOutput == null ? "" : rawOutput.trim();
        if (normalizedOutput.contains("No such file or directory")) {
            return modeLabel + " 解析失败：文件或可执行文件路径不存在。请确认当前 WSL2 中路径有效，并优先使用 /mnt/<盘符>/... 形式。原始输出: " + normalizedOutput;
        }
        if (normalizedOutput.contains("not in executable format")) {
            return modeLabel + " 解析失败：program-path 不是可执行 ELF 文件，或与 core 不匹配。原始输出: " + normalizedOutput;
        }
        if (normalizedOutput.contains("is not a core dump")) {
            return modeLabel + " 解析失败：当前文件不是有效的 core dump。原始输出: " + normalizedOutput;
        }
        return modeLabel + " 解析失败，退出码=" + exit + "。原始输出: " + normalizedOutput;
    }

    private static void ensureWslAvailable() {
        try {
            Process p = new ProcessBuilder("wsl", "--status")
                    .redirectErrorStream(true)
                    .start();
            int exit = p.waitFor();
            if (exit != 0) {
                throw new IllegalStateException("wsl --status 执行失败，退出码=" + exit);
            }
        } catch (Exception e) {
            throw new IllegalStateException("未检测到可用的 WSL2（请先安装并启用 WSL2）", e);
        }
    }

    private static void ensureGdbAvailableInLinux() {
        try {
            Process p = new ProcessBuilder("gdb", "--version")
                    .redirectErrorStream(true)
                    .start();
            int exit = p.waitFor();
            if (exit != 0) {
                throw new IllegalStateException("gdb --version 执行失败，退出码=" + exit);
            }
        } catch (Exception e) {
            throw new IllegalStateException("当前 WSL2/Linux 环境中未找到可用的 gdb，请先在 Ubuntu 中安装 gdb 并加入 PATH", e);
        }
    }
}

