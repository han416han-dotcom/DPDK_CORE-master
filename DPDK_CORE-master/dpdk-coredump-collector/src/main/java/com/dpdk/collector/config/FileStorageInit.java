package com.dpdk.collector.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@RequiredArgsConstructor
public class FileStorageInit {

    private final FileStorageConfig fileStorageConfig;

    @Bean
    public ApplicationRunner ensureFileStorageDirsExist() {
        return args -> {
            String basePath = fileStorageConfig.getPath();
            if (basePath == null || basePath.isBlank()) {
                throw new IllegalStateException("配置缺失：file.storage.path 不能为空（用于保存上传文件）");
            }

            createDir(Paths.get(basePath));

            if (fileStorageConfig.getCoredumpDir() != null && !fileStorageConfig.getCoredumpDir().isBlank()) {
                createDir(Paths.get(basePath, fileStorageConfig.getCoredumpDir()));
            }
            if (fileStorageConfig.getLogDir() != null && !fileStorageConfig.getLogDir().isBlank()) {
                createDir(Paths.get(basePath, fileStorageConfig.getLogDir()));
            }
            if (fileStorageConfig.getTempDir() != null && !fileStorageConfig.getTempDir().isBlank()) {
                createDir(Paths.get(basePath, fileStorageConfig.getTempDir()));
            }
        };
    }

    private static void createDir(Path path) throws Exception {
        Files.createDirectories(path);
        if (!Files.isDirectory(path) || !Files.isWritable(path)) {
            throw new IllegalStateException("目录不可写或不可用: " + path.toAbsolutePath());
        }
    }
}

