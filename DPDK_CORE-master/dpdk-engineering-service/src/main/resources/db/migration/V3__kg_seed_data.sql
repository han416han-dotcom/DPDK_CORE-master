INSERT INTO kg_fault_node (code, name, description) VALUES
('MEMORY_FAULT', '内存访问异常', 'SIGSEGV/SIGBUS、非法指针、越界访问等'),
('MBUF_FAULT', 'mbuf 相关', 'rte_pktmbuf、队列释放顺序、双 free 等'),
('EAL_INIT_FAULT', 'EAL 初始化', '大页、PCI、vfio、核绑定失败等'),
('DRIVER_PMD_FAULT', 'PMD/网卡驱动', 'eth_dev、驱动 probe、链路 down 等'),
('CONFIG_FAULT', '配置/参数', '无效参数、启动选项错误等'),
('UNKNOWN', '未分类', '信息不足或混合症状');

INSERT INTO kg_repair_node (code, title, steps, reference_url) VALUES
('REP_CORE_ULIMIT', '检查 core 文件限制', '1) ulimit -c unlimited\n2) 确认 systemd LimitCORE\n3) 重启业务进程', 'https://doc.dpdk.org/guides/prog_guide/'),
('REP_HUGEPAGE', '大页与内存预留', '1) 检查 /proc/meminfo HugePages\n2) 配置 nr_hugepages\n3) DPDK --huge-dir / --socket-mem', 'https://doc.dpdk.org/guides/linux_gsg/sys_reqs.html'),
('REP_MBUF_AUDIT', 'mbuf 生命周期审计', '1) 搜索 rte_pktmbuf_free 对称调用\n2) 检查 burst 循环内 free\n3) 使用 ASAN 构建', 'https://doc.dpdk.org/guides/prog_guide/mbuf_lib.html'),
('REP_EAL_PCI', 'EAL / PCI 绑定', '1) 确认网卡绑定 vfio-pci/uio\n2) 检查 IOMMU\n3) 核对 -w / -a 白名单', 'https://doc.dpdk.org/guides/linux_gsg/linux_drivers.html'),
('REP_DRIVER_RESET', '驱动复位与版本', '1) 对齐 DPDK 与网卡固件版本\n2) 尝试 dev_reset / 重新 probe\n3) 收集 ethtool -i', 'https://doc.dpdk.org/guides/nics/');

INSERT INTO kg_fault_repair_edge (fault_id, repair_id, confidence, scenario) VALUES
((SELECT id FROM kg_fault_node WHERE code = 'MEMORY_FAULT'), (SELECT id FROM kg_repair_node WHERE code = 'REP_CORE_ULIMIT'), 0.55, 'core 文件存在但解析异常'),
((SELECT id FROM kg_fault_node WHERE code = 'MEMORY_FAULT'), (SELECT id FROM kg_repair_node WHERE code = 'REP_MBUF_AUDIT'), 0.72, '栈中出现 mbuf API'),
((SELECT id FROM kg_fault_node WHERE code = 'MBUF_FAULT'), (SELECT id FROM kg_repair_node WHERE code = 'REP_MBUF_AUDIT'), 0.9, '默认'),
((SELECT id FROM kg_fault_node WHERE code = 'EAL_INIT_FAULT'), (SELECT id FROM kg_repair_node WHERE code = 'REP_HUGEPAGE'), 0.85, '大页不足'),
((SELECT id FROM kg_fault_node WHERE code = 'EAL_INIT_FAULT'), (SELECT id FROM kg_repair_node WHERE code = 'REP_EAL_PCI'), 0.8, 'PCI 绑定失败'),
((SELECT id FROM kg_fault_node WHERE code = 'DRIVER_PMD_FAULT'), (SELECT id FROM kg_repair_node WHERE code = 'REP_DRIVER_RESET'), 0.88, '驱动层崩溃'),
((SELECT id FROM kg_fault_node WHERE code = 'UNKNOWN'), (SELECT id FROM kg_repair_node WHERE code = 'REP_CORE_ULIMIT'), 0.35, '兜底排查');
