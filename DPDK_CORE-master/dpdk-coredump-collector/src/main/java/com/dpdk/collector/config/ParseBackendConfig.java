package com.dpdk.collector.config;

import com.dpdk.collector.parser.ParseBackend;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "dpdk.collector.parse")
public class ParseBackendConfig {
    /**
     * 默认解析后端：LOCAL_GDB / WSL2_GDB / REMOTE_SSH
     */
    private ParseBackend backend = ParseBackend.LOCAL_GDB;
}

