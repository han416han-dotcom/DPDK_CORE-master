package com.dpdk.collector.service;

import com.dpdk.collector.entity.CoredumpFile;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class ProgramPathResolver {

    public String resolveProgramPath(CoredumpFile coredumpFile, String configuredProgramPath) {
        return resolve(coredumpFile, configuredProgramPath).getResolvedProgramPath();
    }

    public ProgramPathResolution resolve(CoredumpFile coredumpFile, String configuredProgramPath) {
        String configured = normalize(configuredProgramPath);
        if (coredumpFile == null) {
            return ProgramPathResolution.builder()
                    .resolvedProgramPath(configured)
                    .usedConfiguredFallback(configured != null && !configured.isBlank())
                    .attemptedCandidates(List.of())
                    .inferredProgramNames(List.of())
                    .build();
        }

        List<String> inferredNames = inferProgramBaseNames(normalize(coredumpFile.getFileName()));
        List<String> candidates = buildCandidates(coredumpFile, configured, inferredNames);
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank() && exists(candidate)) {
                return ProgramPathResolution.builder()
                        .resolvedProgramPath(candidate)
                        .usedConfiguredFallback(configured != null && configured.equals(candidate))
                        .attemptedCandidates(List.copyOf(candidates))
                        .inferredProgramNames(List.copyOf(inferredNames))
                        .build();
            }
        }
        return ProgramPathResolution.builder()
                .resolvedProgramPath(configured)
                .usedConfiguredFallback(configured != null && !configured.isBlank())
                .attemptedCandidates(List.copyOf(candidates))
                .inferredProgramNames(List.copyOf(inferredNames))
                .build();
    }

    private List<String> buildCandidates(CoredumpFile coredumpFile, String configuredProgramPath, List<String> inferredNames) {
        List<String> candidates = new ArrayList<>();
        String storedPath = normalize(coredumpFile.getFilePath());
        String sourcePath = extractOriginalSourcePath(coredumpFile.getErrorMessage());
        Path storedParent = parentOf(storedPath);
        Path sourceParent = parentOf(sourcePath);
        Path configuredParent = parentOf(configuredProgramPath);

        for (String inferredName : inferredNames) {
            addCandidate(candidates, sourceParent, inferredName);
            addFuzzyCandidates(candidates, sourceParent, inferredName);

            addCandidate(candidates, sourceParent == null ? null : sourceParent.getParent(), inferredName);
            addFuzzyCandidates(candidates, sourceParent == null ? null : sourceParent.getParent(), inferredName);

            addCandidate(candidates, storedParent, inferredName);
            addFuzzyCandidates(candidates, storedParent, inferredName);

            addCandidate(candidates, storedParent == null ? null : storedParent.getParent(), inferredName);
            addFuzzyCandidates(candidates, storedParent == null ? null : storedParent.getParent(), inferredName);

            addCandidate(candidates, configuredParent, inferredName);
            addFuzzyCandidates(candidates, configuredParent, inferredName);

            addCandidate(candidates, configuredParent == null ? null : configuredParent.getParent(), inferredName);
            addFuzzyCandidates(candidates, configuredParent == null ? null : configuredParent.getParent(), inferredName);
        }

        if (configuredProgramPath != null && !configuredProgramPath.isBlank()) {
            candidates.add(configuredProgramPath);
        }

        return deduplicate(candidates);
    }

    private List<String> inferProgramBaseNames(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return List.of();
        }

        String normalized = fileName.toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();

        if (normalized.startsWith("core_app_")) {
            String suffix = normalized.substring("core_".length());
            String trimmed = stripNumericSuffix(suffix);
            if (!trimmed.isBlank()) {
                names.add(trimmed);
                names.add(stripAllNumericSuffixes(trimmed));
                names.add(removeTrailingToken(trimmed));
            }
        } else if (normalized.startsWith("core_")) {
            String suffix = normalized.substring("core_".length());
            String trimmed = stripNumericSuffix(suffix);
            if (!trimmed.isBlank()) {
                names.add(trimmed);
                names.add(stripAllNumericSuffixes(trimmed));
                names.add(removeTrailingToken(trimmed));
                names.add("app_" + trimmed);
                names.add("app_" + stripAllNumericSuffixes(trimmed));
                names.add("app_" + removeTrailingToken(trimmed));
            }
        }

        if (normalized.startsWith("test_core_")) {
            String suffix = normalized.substring("test_core_".length());
            if (!suffix.isBlank()) {
                names.add("app_" + suffix);
                names.add(suffix);
                names.add("crash_app");
            }
        }

        return deduplicate(names);
    }

    private String stripNumericSuffix(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String[] parts = name.split("_");
        int end = parts.length;
        while (end > 0 && parts[end - 1].matches("\\d+")) {
            end--;
        }
        if (end <= 0) {
            return "";
        }
        return String.join("_", Arrays.copyOf(parts, end));
    }

    private String stripAllNumericSuffixes(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String normalized = name;
        while (!normalized.isBlank() && Character.isDigit(normalized.charAt(normalized.length() - 1))) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.endsWith("_") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private String removeTrailingToken(String name) {
        if (name == null || name.isBlank() || !name.contains("_")) {
            return name == null ? "" : name;
        }
        int lastUnderscore = name.lastIndexOf('_');
        if (lastUnderscore <= 0) {
            return name;
        }
        return name.substring(0, lastUnderscore);
    }

    private void addCandidate(List<String> candidates, Path directory, String fileName) {
        if (directory == null || fileName == null || fileName.isBlank()) {
            return;
        }
        candidates.add(directory.resolve(fileName).toString());
    }

    private void addFuzzyCandidates(List<String> candidates, Path directory, String fileName) {
        if (directory == null || fileName == null || fileName.isBlank()) {
            return;
        }
        try {
            if (!Files.isDirectory(directory)) {
                return;
            }
            String normalizedTarget = fileName.toLowerCase(Locale.ROOT);
            try (var paths = Files.list(directory)) {
                paths.filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .filter(name -> matchesFuzzyExecutableName(normalizedTarget, name))
                        .sorted()
                        .limit(5)
                        .forEach(name -> candidates.add(directory.resolve(name).toString()));
            }
        } catch (Exception ignored) {
        }
    }

    private boolean matchesFuzzyExecutableName(String targetName, String candidateName) {
        if (candidateName == null || candidateName.isBlank()) {
            return false;
        }
        String normalizedCandidate = candidateName.toLowerCase(Locale.ROOT);
        if (normalizedCandidate.equals(targetName)) {
            return true;
        }
        if (normalizedCandidate.startsWith(targetName) || targetName.startsWith(normalizedCandidate)) {
            return true;
        }
        return sharedPrefixLength(normalizedCandidate, targetName) >= Math.min(12, Math.min(normalizedCandidate.length(), targetName.length()));
    }

    private int sharedPrefixLength(String left, String right) {
        int max = Math.min(left.length(), right.length());
        int count = 0;
        while (count < max && left.charAt(count) == right.charAt(count)) {
            count++;
        }
        return count;
    }

    private Path parentOf(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            return Paths.get(path).getParent();
        } catch (Exception e) {
            return null;
        }
    }

    private boolean exists(String path) {
        try {
            Path candidate = Paths.get(path);
            return Files.exists(candidate) && Files.isRegularFile(candidate);
        } catch (Exception e) {
            return false;
        }
    }

    private List<String> deduplicate(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null && !normalized.isBlank() && !result.contains(normalized)) {
                result.add(normalized);
            }
        }
        return result;
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

    private String normalize(String path) {
        return path == null ? null : path.trim();
    }
}
