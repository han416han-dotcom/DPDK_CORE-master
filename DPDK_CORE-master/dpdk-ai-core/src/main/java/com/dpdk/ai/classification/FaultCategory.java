package com.dpdk.ai.classification;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FaultCategory {
    MEMORY_FAULT("MEMORY_FAULT", "内存访问 / 非法地址类故障"),
    MBUF_FAULT("MBUF_FAULT", "mbuf 生命周期或队列相关"),
    EAL_INIT_FAULT("EAL_INIT_FAULT", "EAL 初始化 / 大页 / PCI 绑定"),
    DRIVER_PMD_FAULT("DRIVER_PMD_FAULT", "PMD / 网卡驱动层"),
    CONFIG_FAULT("CONFIG_FAULT", "配置或参数类"),
    UNKNOWN("UNKNOWN", "未分类");

    private final String code;
    private final String description;
}
