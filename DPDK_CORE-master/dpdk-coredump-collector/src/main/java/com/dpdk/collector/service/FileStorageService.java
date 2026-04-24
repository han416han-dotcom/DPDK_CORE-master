package com.dpdk.collector.service;

import com.dpdk.collector.config.FileStorageConfig;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FileStorageService {
    private final FileStorageConfig fileStorageConfig;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    
    public String storeCoredumpFile(MultipartFile file) throws IOException {
        return storeFile(file, fileStorageConfig.getCoredumpDir());
    }
    
    public String storeLogFile(MultipartFile file) throws IOException {
        return storeFile(file, fileStorageConfig.getLogDir());
    }
    
    public String storeAutoScannedFile(File sourceFile, String targetDir) throws IOException {
        String fileName = sourceFile.getName();
        String timestamp = dateFormatter.format(LocalDateTime.now());
        String newFileName = timestamp + "_" + fileName;
        
        Path targetPath = Paths.get(fileStorageConfig.getPath(), targetDir, newFileName);
        Files.createDirectories(targetPath.getParent());
        
        FileUtils.copyFile(sourceFile, targetPath.toFile());
        return targetPath.toString();
    }
    
    private String storeFile(MultipartFile file, String subDir) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("file 不能为空");
        }
        String originalFileName = file.getOriginalFilename();
        String timestamp = dateFormatter.format(LocalDateTime.now());
        if (originalFileName == null || originalFileName.isBlank()) {
            originalFileName = "uploaded.bin";
        }
        String newFileName = timestamp + "_" + originalFileName;
        
        Path targetPath = Paths.get(fileStorageConfig.getPath(), subDir, newFileName);
        Files.createDirectories(targetPath.getParent());
        
        File targetFile = targetPath.toFile();
        file.transferTo(Objects.requireNonNull(targetFile));
        return targetPath.toString();
    }
}