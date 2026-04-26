package com.dpdk.collector.service;

import com.dpdk.collector.config.ParseBackendConfig;
import com.dpdk.collector.entity.CoredumpFile;
import com.dpdk.collector.entity.LogFile;
import com.dpdk.collector.entity.ParsedFeature;
import com.dpdk.collector.parser.CoredumpParser;
import com.dpdk.collector.parser.ParseBackend;
import com.dpdk.collector.repository.CoredumpFileRepository;
import com.dpdk.collector.repository.LogFileRepository;
import com.dpdk.collector.repository.ParsedFeatureRepository;
import com.dpdk.collector.util.DpdkLogParserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DataParserService {
    private final CoredumpFileRepository coredumpFileRepository;
    private final LogFileRepository logFileRepository;
    private final ParsedFeatureRepository parsedFeatureRepository;
    private final DataNormalizationService normalizationService;
    private final ParseBackendConfig parseBackendConfig;
    private final ProgramPathResolver programPathResolver;
    private final CoreFileValidator coreFileValidator;
    private final List<CoredumpParser> coredumpParsers;

    @Async
    public void parseCoredumpFileAsync(CoredumpFile coredumpFile) {
        parseCoredumpFileAsync(coredumpFile, null);
    }

    @Async
    public void parseCoredumpFileAsync(CoredumpFile coredumpFile, ParseBackend backendOverride) {
        try {
            coredumpFile.setStatus("PARSING");
            coredumpFileRepository.save(coredumpFile);

            ParseBackend backend = backendOverride != null ? backendOverride : parseBackendConfig.getBackend();
            CoredumpParser parser = coredumpParsers.stream()
                    .filter(p -> p.backend() == backend)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("未找到解析器: " + backend));

            File coreFile = new File(coredumpFile.getFilePath());
            ProgramPathResolution resolution = programPathResolver.resolve(coredumpFile, parseBackendConfig.getProgramPath());
            coreFileValidator.validate(coredumpFile, resolution);

            String originalSourcePath = extractOriginalSourcePath(coredumpFile.getErrorMessage());
            String parsingMessage = buildParsingMessage(coredumpFile, resolution, originalSourcePath);
            coredumpFile.setErrorMessage(parsingMessage);
            coredumpFileRepository.save(coredumpFile);

            var result = parser.parse(coreFile, resolution.getResolvedProgramPath());

            ParsedFeature feature = normalizationService.normalizeCoredumpResult(result);
            feature.setFileId(coredumpFile.getId());
            feature.setFileType("COREDUMP");
            parsedFeatureRepository.save(feature);

            coredumpFile.setStatus("PARSED");
            coredumpFile.setErrorMessage(null);
            coredumpFileRepository.save(coredumpFile);
        } catch (Exception e) {
            coredumpFile.setStatus("FAILED");
            coredumpFile.setErrorMessage(e.getMessage());
            coredumpFileRepository.save(coredumpFile);
            e.printStackTrace();
        }
    }

    @Async
    public void parseLogFileAsync(LogFile logFile) {
        try {
            logFile.setStatus("PARSING");
            String originalSourcePath = extractOriginalSourcePath(logFile.getErrorMessage());
            logFile.setErrorMessage("解析中，source=" + (originalSourcePath == null ? logFile.getFilePath() : originalSourcePath)
                    + ", stored=" + logFile.getFilePath());
            logFileRepository.save(logFile);

            File logFileObj = new File(logFile.getFilePath());
            DpdkLogParserUtil.LogParseResult result = DpdkLogParserUtil.parseLog(logFileObj);

            ParsedFeature feature = normalizationService.normalizeLogResult(result);
            feature.setFileId(logFile.getId());
            feature.setFileType("LOG");
            parsedFeatureRepository.save(feature);

            logFile.setStatus("PARSED");
            logFile.setErrorMessage(null);
            logFileRepository.save(logFile);
        } catch (Exception e) {
            logFile.setStatus("FAILED");
            logFile.setErrorMessage(e.getMessage());
            logFileRepository.save(logFile);
            e.printStackTrace();
        }
    }

    private String buildParsingMessage(CoredumpFile coredumpFile, ProgramPathResolution resolution, String originalSourcePath) {
        StringBuilder builder = new StringBuilder();
        builder.append("解析中，source=")
                .append(originalSourcePath == null ? coredumpFile.getFilePath() : originalSourcePath)
                .append(", stored=")
                .append(coredumpFile.getFilePath())
                .append(", program-path=")
                .append(resolution.getResolvedProgramPath() == null ? "<none>" : resolution.getResolvedProgramPath());

        if (!resolution.getInferredProgramNames().isEmpty()) {
            builder.append(", inferred=").append(resolution.getInferredProgramNames());
        }
        if (resolution.isUsedConfiguredFallback()) {
            builder.append(", fallback=configured-program-path");
        }
        return builder.toString();
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
}
