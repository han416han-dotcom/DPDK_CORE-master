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

    /**
     * 可选：core 对应的可执行文件（或带符号的 binary）路径。
     * 如果未提供，gdb 可能无法生成有效的调用栈，导致诊断结果趋向兜底。
     *
     * Windows + WSL2_GDB：这里填写 Windows 路径（例如 D:/path/to/app），会自动转换为 /mnt/d/... 传给 WSL。
     */
    private String programPath;
}

