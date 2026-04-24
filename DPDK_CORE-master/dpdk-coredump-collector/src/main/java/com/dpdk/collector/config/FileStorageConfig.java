package com.dpdk.collector.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "file.storage")
public class FileStorageConfig {
    private String path;
    private String coredumpDir;
    private String logDir;
    private String tempDir;
}