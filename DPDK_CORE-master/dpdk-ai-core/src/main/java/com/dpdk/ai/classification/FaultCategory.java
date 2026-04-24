package com.dpdk.ai.classification;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FaultCategory {
    MEMORY_FAULT("MEMORY_FAULT", "内存访问 / 非法地址类故障"),
    MBUF_FAULT("MBUF_FAULT", "mbuf 生命周期异常 / mempool 操作异常"),
    THREAD_CONTENTION_FAULT("THREAD_CONTENTION_FAULT", "多核线程资源竞争 / 锁争用 / 队列竞争"),
    DRIVER_PMD_FAULT("DRIVER_PMD_FAULT", "驱动适配冲突 / PMD / 设备初始化异常"),
    EAL_INIT_FAULT("EAL_INIT_FAULT", "EAL 初始化 / hugepage / NUMA / PCI 绑定异常"),
    CONFIG_FAULT("CONFIG_FAULT", "配置或参数类"),
    UNKNOWN("UNKNOWN", "未分类");

    private final String code;
    private final String description;
}
