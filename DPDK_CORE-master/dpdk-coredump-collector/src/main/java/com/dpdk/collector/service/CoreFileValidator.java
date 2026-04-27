package com.dpdk.collector.service;

import com.dpdk.collector.entity.CoredumpFile;
import com.dpdk.collector.util.WslPathUtil;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class CoreFileValidator {

    public void validate(CoredumpFile coredumpFile, ProgramPathResolution resolution) {
        if (coredumpFile == null) {
            throw new IllegalArgumentException("coredumpFile 不能为空");
        }

        FileKind coreKind = detectFileKind(coredumpFile.getFilePath());
        if (coreKind == FileKind.EXECUTABLE) {
            throw new IllegalStateException(buildExecutableDisguisedAsCoreMessage(coredumpFile, resolution));
        }

        if (coreKind == FileKind.UNKNOWN) {
            throw new IllegalStateException(buildUnknownCoreTypeMessage(coredumpFile, resolution));
        }
    }

    private String buildExecutableDisguisedAsCoreMessage(CoredumpFile coredumpFile, ProgramPathResolution resolution) {
        return "当前上传文件并不是有效的 core dump，而是一个 ELF 可执行文件被误当成 core。"
                + " 请检查是否把 app 可执行文件误重命名为 core 文件。"
                + buildContext(coredumpFile, resolution);
    }

    private String buildUnknownCoreTypeMessage(CoredumpFile coredumpFile, ProgramPathResolution resolution) {
        return "当前文件无法识别为有效的 core dump。请确认上传的是程序崩溃后生成的 core 文件，而不是可执行文件、日志或其他普通文件。"
                + buildContext(coredumpFile, resolution);
    }

    private String buildContext(CoredumpFile coredumpFile, ProgramPathResolution resolution) {
        String sourcePath = extractOriginalSourcePath(coredumpFile.getErrorMessage());
        String resolvedProgramPath = resolution == null ? null : resolution.getResolvedProgramPath();
        List<String> inferredNames = resolution == null ? List.of() : resolution.getInferredProgramNames();
        return " 失败上下文 | source=" + (sourcePath == null ? coredumpFile.getFilePath() : sourcePath)
                + " | stored=" + coredumpFile.getFilePath()
                + " | program-path=" + (resolvedProgramPath == null ? "<none>" : resolvedProgramPath)
                + " | inferred=" + inferredNames;
    }

    private String extractOriginalSourcePath(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String prefix = "AUTO_SCAN source=";
        if (!message.startsWith(prefix)) {
            return null;
        }
        return message.substring(prefix.length()).trim();
    }

    private FileKind detectFileKind(String path) {
        try {
            List<String> cmd = buildFileCommand(path);
            Process process = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }

            int exit = process.waitFor();
            if (exit != 0) {
                return FileKind.UNKNOWN;
            }

            String description = output.toString().toLowerCase(Locale.ROOT);
            if (description.contains("core file")) {
                return FileKind.CORE_DUMP;
            }
            if (description.contains("elf") && (description.contains("executable") || description.contains("pie executable"))) {
                return FileKind.EXECUTABLE;
            }
            return FileKind.UNKNOWN;
        } catch (Exception e) {
            return FileKind.UNKNOWN;
        }
    }

    private List<String> buildFileCommand(String path) {
        String normalizedPath = WslPathUtil.normalizeForWsl(path);
        List<String> cmd = new ArrayList<>();
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("windows")) {
            cmd.add("wsl");
        }
        cmd.add("file");
        cmd.add(normalizedPath);
        return cmd;
    }

    private enum FileKind {
        CORE_DUMP,
        EXECUTABLE,
        UNKNOWN
    }
}
