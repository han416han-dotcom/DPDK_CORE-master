-- Core文件表
CREATE TABLE coredump_file (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    file_size BIGINT NOT NULL,
    file_hash VARCHAR(64) NOT NULL UNIQUE,  # MD5哈希，防止重复处理
    generate_time DATETIME NOT NULL,
    upload_time DATETIME NOT NULL,
    source_type VARCHAR(20) NOT NULL,  # AUTO_SCAN/MANUAL_UPLOAD
    status VARCHAR(20) NOT NULL,  # PENDING/PARSING/PARSED/FAILED
    error_message TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 日志文件表
CREATE TABLE log_file (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    file_size BIGINT NOT NULL,
    file_hash VARCHAR(64) NOT NULL UNIQUE,
    generate_time DATETIME NOT NULL,
    upload_time DATETIME NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 标准化特征表
CREATE TABLE parsed_feature (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_id BIGINT NOT NULL,
    file_type VARCHAR(20) NOT NULL,  # COREDUMP/LOG
    crash_signal VARCHAR(50),        # 崩溃信号：SIGSEGV/SIGILL/SIGABRT等
    crash_address VARCHAR(128),      # 崩溃地址
    call_stack TEXT,                 # 调用栈信息
    registers TEXT,                  # 寄存器值
    mbuf_operations TEXT,            # mbuf相关操作记录
    memory_info TEXT,                # 内存相关信息
    thread_info TEXT,                # 线程信息
    eal_parameters TEXT,             # EAL参数
    error_keywords TEXT,             # 错误关键词
    raw_content TEXT,                # 原始关键内容
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (file_id) REFERENCES coredump_file(id) ON DELETE CASCADE
);

-- 系统配置表
CREATE TABLE system_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value TEXT,
    description VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);