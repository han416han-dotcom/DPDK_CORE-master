package com.dpdk.collector.controller;

import com.dpdk.collector.entity.CoredumpFile;
import com.dpdk.collector.entity.LogFile;
import com.dpdk.collector.repository.CoredumpFileRepository;
import com.dpdk.collector.repository.LogFileRepository;
import com.dpdk.collector.service.DataParserService;
import com.dpdk.collector.service.FileStorageService;
import com.dpdk.collector.util.FileHashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/collect")
@RequiredArgsConstructor
public class DataCollectionController {
    private final FileStorageService fileStorageService;
    private final CoredumpFileRepository coredumpFileRepository;
    private final LogFileRepository logFileRepository;
    private final DataParserService dataParserService;
    
    @PostMapping("/coredump")
    @SuppressWarnings("null")
    public ResponseEntity<?> uploadCoredump(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body("上传失败: 文件为空");
            }

            // 只落盘一次：先存储，再基于已保存的文件计算哈希（避免 Multipart 临时文件被 transferTo 移走后再次读取失败）
            String storedPath = fileStorageService.storeCoredumpFile(file);
            String fileHash = FileHashUtil.calculateMD5(new File(storedPath));

            if (coredumpFileRepository.existsByFileHash(fileHash)) {
                try {
                    Files.deleteIfExists(Path.of(storedPath));
                } catch (Exception ignored) {
                }
                return ResponseEntity.badRequest().body("该Core文件已存在");
            }
            
            // 保存文件信息
            CoredumpFile coredumpFile = CoredumpFile.builder()
                    .fileName(file.getOriginalFilename())
                    .filePath(storedPath)
                    .fileSize(file.getSize())
                    .fileHash(fileHash)
                    .generateTime(LocalDateTime.now())
                    .uploadTime(LocalDateTime.now())
                    .sourceType("MANUAL_UPLOAD")
                    .status("PENDING")
                    .build();
            
            coredumpFile = coredumpFileRepository.save(coredumpFile);
            
            // 异步解析
            dataParserService.parseCoredumpFileAsync(coredumpFile);
            
            return ResponseEntity.ok(coredumpFile);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("上传失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/log")
    @SuppressWarnings("null")
    public ResponseEntity<?> uploadLog(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().body("上传失败: 文件为空");
            }

            String storedPath = fileStorageService.storeLogFile(file);
            String fileHash = FileHashUtil.calculateMD5(new File(storedPath));

            if (logFileRepository.existsByFileHash(fileHash)) {
                try {
                    Files.deleteIfExists(Path.of(storedPath));
                } catch (Exception ignored) {
                }
                return ResponseEntity.badRequest().body("该日志文件已存在");
            }
            
            LogFile logFile = LogFile.builder()
                    .fileName(file.getOriginalFilename())
                    .filePath(storedPath)
                    .fileSize(file.getSize())
                    .fileHash(fileHash)
                    .generateTime(LocalDateTime.now())
                    .uploadTime(LocalDateTime.now())
                    .sourceType("MANUAL_UPLOAD")
                    .status("PENDING")
                    .build();
            
            logFile = logFileRepository.save(logFile);
            
            dataParserService.parseLogFileAsync(logFile);
            
            return ResponseEntity.ok(logFile);
            
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("上传失败: " + e.getMessage());
        }
    }
}