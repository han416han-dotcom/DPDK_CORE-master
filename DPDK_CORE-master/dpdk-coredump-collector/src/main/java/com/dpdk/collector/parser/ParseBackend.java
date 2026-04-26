package com.dpdk.collector.parser;

public enum ParseBackend {
    /**
     * 兼容旧代码，前端与默认配置不再使用。
     */
    @Deprecated
    LOCAL_GDB,

    WSL2_GDB,

    /**
     * 兼容旧代码，前端与默认配置不再使用。
     */
    @Deprecated
    REMOTE_SSH
}
