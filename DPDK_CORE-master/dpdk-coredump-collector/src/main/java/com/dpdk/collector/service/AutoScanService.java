package com.dpdk.collector.service;

import com.dpdk.collector.entity.CoredumpFile;
import com.dpdk.collector.entity.LogFile;
import com.dpdk.collector.repository.CoredumpFileRepository;
import com.dpdk.collector.repository.LogFileRepository;
import com.dpdk.collector.util.FileHashUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
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
    private final CoredumpFileRepository coredumpFileRepository;
    private final LogFileRepository logFileRepository;
    private final FileStorageService fileStorageService;
    private final DataParserService dataParserService;
    
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
        for (String path : coredumpPaths) {
            File dir = new File(path);
            if (!dir.exists() || !dir.isDirectory()) {
                continue;
            }
            
            Collection<File> files = FileUtils.listFiles(dir, 
                    new WildcardFileFilter(coredumpPattern), null);
            
            for (File file : files) {
                processCoredumpFile(file);
            }
        }
    }
    
    @Scheduled(fixedRateString = "${scan.interval}")
    public void scanLogFiles() {
        for (String path : logPaths) {
            File dir = new File(path);
            if (!dir.exists() || !dir.isDirectory()) {
                continue;
            }
            
            Collection<File> files = FileUtils.listFiles(dir, 
                    new WildcardFileFilter(logPattern), null);
            
            for (File file : files) {
                processLogFile(file);
            }
        }
    }
    
    private void processCoredumpFile(File file) {
        try {
            String fileHash = FileHashUtil.calculateMD5(file);
            
            // 检查是否已处理过
            if (coredumpFileRepository.existsByFileHash(fileHash)) {
                return;
            }
            
            // 存储文件到系统目录
            String storedPath = fileStorageService.storeAutoScannedFile(file, "coredumps");
            
            // 保存文件信息到数据库
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
                    .build();
            
            coredumpFile = coredumpFileRepository.save(coredumpFile);
            
            // 异步解析文件
            dataParserService.parseCoredumpFileAsync(coredumpFile);
            
        } catch (Exception e) {
            // 记录错误日志
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
                    .build();
            
            logFile = logFileRepository.save(logFile);
            
            dataParserService.parseLogFileAsync(logFile);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}