# DPDK 故障分析平台：解析逻辑总览

本文档总结本仓库的“文件解析（Parse）→特征（Feature）→诊断（Diagnose）”全链路逻辑，便于在其他项目中复用/迁移同样的设计思想与扩展点。

---

## 1. 模块与职责

- `dpdk-engineering-service`
  - **职责**：Web UI（Thymeleaf 页面）、一键触发解析、展示诊断报告与图表。
  - **入口**：
    - 页面：`GET /`（列表 + 上传）→ `templates/index.html`
    - 页面：`GET /diagnosis/{id}`（诊断页）→ `templates/diagnosis.html`
    - 触发解析（UI）：`POST /ui/parse/{id}?backend=...` → `DashboardController`
    - 触发解析（API）：`POST /api/platform/parse/coredump/{id}?backend=...` → `PlatformRestController`
    - 获取诊断（API）：`POST /api/platform/diagnose` → `PlatformRestController`

- `dpdk-coredump-collector`
  - **职责**：文件上传、落盘存储、入库记录、异步解析（gdb/日志解析）、标准化特征入库。
  - **入口**：
    - Core 上传：`POST /api/collect/coredump` → `DataCollectionController`
    - Log 上传：`POST /api/collect/log` → `DataCollectionController`
  - **核心服务**：
    - `FileStorageService`：文件落盘
    - `DataParserService`：异步解析 + 状态写回 + 特征入库
    - `DataNormalizationService`：解析结果 → `ParsedFeature`

- `dpdk-ai-core`
  - **职责**：从标准化特征抽取混合特征向量、故障分类、知识图谱建议、导出报告。
  - **核心**：
    - `FaultAnalysisEngine#diagnose(fileId, fileType)`：`ParsedFeature` → `DiagnosisResult`

---

## 2. 数据模型（关键表/实体）

上传与解析过程主要围绕两类文件实体与一类“标准化特征”：

- `CoredumpFile`（表：`coredump_file`）
  - `fileName/filePath/fileSize/fileHash`
  - `status`: `PENDING` → `PARSING` → `PARSED` 或 `FAILED`
  - `errorMessage`: 失败原因（会在 UI 展示）

- `LogFile`（表：`log_file`）
  - 字段结构与 `CoredumpFile` 类似

- `ParsedFeature`（表：`parsed_feature`）
  - 存储从 core/log 提取并标准化后的“特征”（例如崩溃信号、调用栈、寄存器信息等结构化字段）
  - AI 诊断以它为输入（而不是直接解析原文件）

---

## 3. 配置项（解析后端与存储）

统一由 Spring Boot 配置驱动（示例见 `dpdk-engineering-service/src/main/resources/application.yml`）：

### 3.1 文件落盘

```yml
file:
  storage:
    path: D:/workspace/data/dpdk-collector/
    coredump-dir: coredumps/
    log-dir: logs/
    temp-dir: temp/
```

- `FileStorageService` 会把上传文件保存到：
  - `path + coredump-dir + timestamp_originalName`
  - `path + log-dir + timestamp_originalName`

### 3.2 解析后端选择（本机 / WSL2 / 远程）

```yml
dpdk:
  collector:
    parse:
      backend: WSL2_GDB   # LOCAL_GDB / WSL2_GDB / REMOTE_SSH
      program-path:      # 可选：core 对应可执行文件（建议带符号）
```

- `backend`：决定 `DataParserService` 选用哪个 `CoredumpParser` 实现
- `program-path`：
  - 可选，但**强烈建议**配置
  - 若不提供，gdb 可能输出 `No executable file specified`，调用栈不完整，导致诊断趋向“兜底/泛化”
  - 在 `WSL2_GDB` 下：填 Windows 路径（如 `D:/.../app`），会自动转换为 `/mnt/d/...` 传入 WSL gdb

---

## 4. 时序流程（从上传到诊断）

### 4.1 上传（以 core 为例）

入口：`DataCollectionController#uploadCoredump(MultipartFile file)`

关键步骤：

1. **落盘一次**（避免 multipart 临时文件被移动后再次读取失败）
   - `storedPath = fileStorageService.storeCoredumpFile(file)`
2. **基于已落盘文件计算 MD5**
   - `fileHash = FileHashUtil.calculateMD5(new File(storedPath))`
3. **去重**
   - 若 `existsByFileHash(fileHash)`：
     - 删除刚落盘文件
     - 返回 400：`该Core文件已存在`
4. **写入数据库记录**
   - `status = PENDING`
5. **异步解析**
   - `dataParserService.parseCoredumpFileAsync(coredumpFile)`

> 同样逻辑也适用于日志上传（`/api/collect/log`）。

### 4.2 异步解析与标准化（core）

入口：`DataParserService#parseCoredumpFileAsync(CoredumpFile, ParseBackend override)`

关键步骤：

1. `status = PARSING` 写回 DB
2. 选择解析后端（策略模式）
   - `backend = override ?? parseBackendConfig.backend`
   - 在容器中从 `List<CoredumpParser>` 找到对应实现
3. 执行解析（返回 `GdbParseResult`）
4. 标准化
   - `ParsedFeature feature = normalizationService.normalizeCoredumpResult(result)`
5. `ParsedFeature` 入库
6. `status = PARSED` 写回 DB
7. 异常处理
   - `status = FAILED`
   - `errorMessage = e.getMessage()`

### 4.3 AI 诊断

入口：`FaultAnalysisEngine#diagnose(fileId, fileType)`

1. 从 `ParsedFeature` 表读取最新特征
2. 抽取混合向量（规则特征 + CNN embedding + 拼接向量）
3. 分类器输出 top 类别与置信度
4. 知识图谱给出修复建议（可为空时 fallback）
5. 返回 `DiagnosisResult`：
   - `faultCode/faultName/confidence/rootCauseHints/featureSnapshot/repairSuggestions`

---

## 5. Core 解析后端（策略模式）

为适配 Windows + WSL2 + 未来 Linux 虚拟机，本项目把“core 解析”抽象为可插拔后端：

### 5.1 接口

- `com.dpdk.collector.parser.CoredumpParser`
  - `backend(): ParseBackend`
  - `parse(File coreFile, String programPath): GdbParseResult`

### 5.2 后端枚举

- `ParseBackend.LOCAL_GDB`
- `ParseBackend.WSL2_GDB`
- `ParseBackend.REMOTE_SSH`（占位，待接入）

### 5.3 LOCAL_GDB

- 实现：`LocalGdbCoredumpParser`
- 调用：`GdbParserUtil.parseCoredump(coreFile, programPath)`
- 命令形态：
  - `gdb <programPath?> --batch --ex bt --ex "info registers" --ex "info threads" -c <core>`

### 5.4 WSL2_GDB（Windows 上推荐）

- 实现：`Wsl2GdbCoredumpParser`
- 调用：`WslGdbParserUtil.parseCoredumpViaWsl(coreFile, programPath)`
- 命令形态：
  - `wsl gdb <programPath?> --batch ... -c <core>`
- 路径转换：
  - `D:\a\b\c` → `/mnt/d/a/b/c`（由 `WslPathUtil` 完成）

### 5.5 REMOTE_SSH（预留）

- 实现：`RemoteSshCoredumpParser`（当前抛 `UnsupportedOperationException`）
- 未来接入建议：
  - 通过 SSH 上传 core 与 program（或使用共享存储）
  - 远端执行 gdb，回传文本输出
  - 本地复用同一套正则提取逻辑生成 `GdbParseResult`

---

## 6. “解析成功但结果不可信”的判定与处理建议

### 常见现象

- `status = PARSED` 但调用栈为空/很短
- 多个不同 core 诊断都落到相同类别（“兜底”）
- `gdb` 输出包含 `No executable file specified`

### 解决

- 配置 `dpdk.collector.parse.program-path` 指向 core 对应可执行文件（建议带符号）
- 确保 program 与 core 同构（相同版本/构建/架构）
- 未来可扩展：支持 `.debug` 符号目录、`set solib-search-path`、`set debug-file-directory` 等 gdb 初始化命令

---

## 7. 前端展示与图表（诊断页）

诊断页：`templates/diagnosis.html`

- 默认“文字概览”
- 可切换“图表视图”（Chart.js）：
  - 置信度环形图
  - 根因提示数 vs 修复建议数
  - 特征向量（Combined Vector）前 32 维截面

> 图表数据来源：复用 `POST /api/platform/diagnose` 返回的 `DiagnosisResult` JSON。

---

## 8. 迁移到其他项目的复用建议（结构不变、实现可替换）

如果你要把同样思想迁移到另一项目，建议保留这几个“稳定接口”：

1. **文件元数据表**（带 `status/errorMessage`）
2. **标准化特征表**（AI/规则的统一入口）
3. **解析后端策略接口**（本机/WSL2/远程都能接入）
4. **诊断引擎统一输出 DTO**（如 `DiagnosisResult`）
5. **前端通过诊断 DTO 做视图切换**（文字/图表/导出）

