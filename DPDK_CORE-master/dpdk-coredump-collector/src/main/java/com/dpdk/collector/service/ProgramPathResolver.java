package com.dpdk.collector.service;

import com.dpdk.collector.entity.CoredumpFile;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ProgramPathResolver {

    public String resolveProgramPath(CoredumpFile coredumpFile, String configuredProgramPath) {
        String configured = normalize(configuredProgramPath);
        if (coredumpFile == null) {
            return configured;
        }

        List<String> candidates = buildCandidates(coredumpFile, configured);
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank() && exists(candidate)) {
                return candidate;
            }
        }
        return configured;
    }

    private List<String> buildCandidates(CoredumpFile coredumpFile, String configuredProgramPath) {
        List<String> candidates = new ArrayList<>();
        String fileName = normalize(coredumpFile.getFileName());
        String originalPath = normalize(coredumpFile.getFilePath());

        if (configuredProgramPath != null && !configuredProgramPath.isBlank()) {
            candidates.add(configuredProgramPath);
        }

        String inferredBaseName = inferProgramBaseName(fileName).orElse(null);
        Path originalParent = parentOf(originalPath);
        if (inferredBaseName != null) {
            addCandidate(candidates, originalParent, inferredBaseName);
            addCandidate(candidates, originalParent == null ? null : originalParent.getParent(), inferredBaseName);
        }

        if (configuredProgramPath != null && !configuredProgramPath.isBlank()) {
            Path configuredParent = parentOf(configuredProgramPath);
            if (inferredBaseName != null) {
                addCandidate(candidates, configuredParent, inferredBaseName);
                addCandidate(candidates, configuredParent == null ? null : configuredParent.getParent(), inferredBaseName);
            }
        }

        return candidates;
    }

    private Optional<String> inferProgramBaseName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return Optional.empty();
        }
        String normalized = fileName.toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("core_")) {
            return Optional.empty();
        }
        String suffix = normalized.substring("core_".length());
        if (suffix.isBlank()) {
            return Optional.empty();
        }

        String[] parts = suffix.split("_");
        if (parts.length < 2) {
            return Optional.of(suffix);
        }

        int end = parts.length;
        while (end > 0 && parts[end - 1].matches("\\d+")) {
            end--;
        }
        if (end == parts.length) {
            return Optional.of(suffix);
        }
        if (end <= 0) {
            return Optional.empty();
        }
        return Optional.of(String.join("_", java.util.Arrays.copyOf(parts, end)));
    }

    private void addCandidate(List<String> candidates, Path directory, String fileName) {
        if (directory == null || fileName == null || fileName.isBlank()) {
            return;
        }
        candidates.add(directory.resolve(fileName).toString());
    }

    private Path parentOf(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        Path p = Paths.get(path);
        return p.getParent();
    }

    private boolean exists(String path) {
        try {
            Path candidate = Paths.get(path);
            return Files.exists(candidate) && Files.isRegularFile(candidate);
        } catch (Exception e) {
            return false;
        }
    }

    private String normalize(String path) {
        return path == null ? null : path.trim();
    }
}
