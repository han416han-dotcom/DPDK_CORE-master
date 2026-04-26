package com.dpdk.collector.service;

import com.dpdk.collector.entity.CoredumpFile;
import com.dpdk.collector.entity.LogFile;
import com.dpdk.collector.repository.CoredumpFileRepository;
import com.dpdk.collector.repository.LogFileRepository;
import com.dpdk.collector.util.FileHashUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.io.filefilter.WildcardFileFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;

@Service
@RequiredArgsConstructor
public class AutoScanService {
    private static final long MIN_STABLE_AGE_MILLIS = 3_000L;

    private final CoredumpFileRepository coredumpFileRepository;
    private final LogFileRepository logFileRepository;
    private final FileStorageService fileStorageService;
    private final DataParserService dataParserService;

    @Value("${scan.enable:false}")
    private boolean scanEnabled;

    @Value("${scan.coredump-paths}")
    private String[] coredumpPaths;

    @Value("${scan.log-paths}")
    private String[] logPaths;

    @Value("${scan.file-patterns.coredump}")
    private String coredumpPattern;

    @Value("${scan.file-patterns.log}")
    private String logPattern;

    @Scheduled(fixedRateString = "${scan.interval}")
    public void scanCoredumpFiles() {
        if (!scanEnabled) {
            return;
        }
        scanFiles(coredumpPaths, coredumpPattern, this::processCoredumpFile);
    }

    @Scheduled(fixedRateString = "${scan.interval}")
    public void scanLogFiles() {
        if (!scanEnabled) {
            return;
        }
        scanFiles(logPaths, logPattern, this::processLogFile);
    }

    private void scanFiles(String[] paths, String pattern, FileProcessor processor) {
        if (paths == null || paths.length == 0 || pattern == null || pattern.isBlank()) {
            return;
        }

        for (String path : paths) {
            if (path == null || path.isBlank()) {
                continue;
            }

            File dir = new File(path.trim());
            if (!dir.exists() || !dir.isDirectory()) {
                continue;
            }

            Collection<File> files = FileUtils.listFiles(
                    dir,
                    new WildcardFileFilter(pattern),
                    TrueFileFilter.INSTANCE
            );

            for (File file : files) {
                if (isReadyForProcessing(file)) {
                    processor.process(file);
                }
            }
        }
    }

    private boolean isReadyForProcessing(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return false;
        }
        if (file.length() <= 0) {
            return false;
        }
        if (file.getName().startsWith(".")) {
            return false;
        }
        long stableAge = System.currentTimeMillis() - file.lastModified();
        return stableAge >= MIN_STABLE_AGE_MILLIS;
    }

    private void processCoredumpFile(File file) {
        try {
            String fileHash = FileHashUtil.calculateMD5(file);
            if (coredumpFileRepository.existsByFileHash(fileHash)) {
                return;
            }

            String storedPath = fileStorageService.storeAutoScannedFile(file, "coredumps");

            CoredumpFile coredumpFile = CoredumpFile.builder()
                    .fileName(file.getName())
                    .filePath(storedPath)
                    .fileSize(file.length())
                    .fileHash(fileHash)
                    .generateTime(LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(file.lastModified()),
                            ZoneId.systemDefault()))
                    .uploadTime(LocalDateTime.now())
                    .sourceType("AUTO_SCAN")
                    .status("PENDING")
                    .errorMessage("AUTO_SCAN source=" + file.getAbsolutePath())
                    .build();

            coredumpFile = coredumpFileRepository.save(coredumpFile);
            dataParserService.parseCoredumpFileAsync(coredumpFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processLogFile(File file) {
        try {
            String fileHash = FileHashUtil.calculateMD5(file);
            if (logFileRepository.existsByFileHash(fileHash)) {
                return;
            }

            String storedPath = fileStorageService.storeAutoScannedFile(file, "logs");

            LogFile logFile = LogFile.builder()
                    .fileName(file.getName())
                    .filePath(storedPath)
                    .fileSize(file.length())
                    .fileHash(fileHash)
                    .generateTime(LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(file.lastModified()),
                            ZoneId.systemDefault()))
                    .uploadTime(LocalDateTime.now())
                    .sourceType("AUTO_SCAN")
                    .status("PENDING")
                    .errorMessage("AUTO_SCAN source=" + file.getAbsolutePath())
                    .build();

            logFile = logFileRepository.save(logFile);
            dataParserService.parseLogFileAsync(logFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FunctionalInterface
    private interface FileProcessor {
        void process(File file);
    }
}
