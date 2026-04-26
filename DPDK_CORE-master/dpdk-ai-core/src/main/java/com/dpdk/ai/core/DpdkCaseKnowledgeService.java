package com.dpdk.ai.core;

import com.dpdk.ai.api.DiagnosisSignal;
import com.dpdk.ai.api.TroubleshootingStep;
import com.dpdk.ai.classification.FaultCategory;
import com.dpdk.collector.entity.ParsedFeature;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DpdkCaseKnowledgeService {

    public DpdkDiagnosisContext buildContext(ParsedFeature feature, Map<FaultCategory, Double> classifierScores, FaultCategory topCategory, double confidence) {
        String normalizedText = join(feature.getCallStack(), feature.getRegisters(), feature.getThreadInfo(), feature.getMemoryInfo(),
                feature.getMbufOperations(), feature.getErrorKeywords(), feature.getEalParameters(), feature.getRawContent()).toLowerCase(Locale.ROOT);
        List<String> stackFrames = splitLines(feature.getCallStack());
        String topFrame = stackFrames.isEmpty() ? "" : stackFrames.get(0).toLowerCase(Locale.ROOT);

        EnumMap<FaultCategory, Double> mergedScores = new EnumMap<>(FaultCategory.class);
        for (FaultCategory category : FaultCategory.values()) {
            mergedScores.put(category, classifierScores == null ? 0.0 : classifierScores.getOrDefault(category, 0.0));
        }

        LinkedHashSet<String> caseTags = new LinkedHashSet<>();
        LinkedHashSet<String> signals = new LinkedHashSet<>();

        applyMemoryCaseRules(feature, normalizedText, topFrame, mergedScores, caseTags, signals);
        applyMbufCaseRules(normalizedText, topFrame, mergedScores, caseTags, signals);
        applyThreadContentionRules(feature, normalizedText, topFrame, mergedScores, caseTags, signals);
        applyDriverCaseRules(normalizedText, topFrame, mergedScores, caseTags, signals);
        applyEalRules(normalizedText, topFrame, mergedScores, caseTags, signals);
        applyConfigRules(normalizedText, topFrame, mergedScores, caseTags, signals);

        FaultCategory finalTopCategory = mergedScores.entrySet().stream()
                .max(Comparator.comparingDouble(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(topCategory == null ? FaultCategory.UNKNOWN : topCategory);

        return DpdkDiagnosisContext.builder()
                .parsedFeature(feature)
                .topCategory(finalTopCategory)
                .confidence(recalculateConfidence(mergedScores, confidence))
                .categoryScores(mergedScores)
                .matchedCaseTags(new ArrayList<>(caseTags))
                .extractedSignals(new ArrayList<>(signals))
                .stackFrames(stackFrames)
                .normalizedText(normalizedText)
                .build();
    }

    public List<DiagnosisSignal> buildSignals(DpdkDiagnosisContext context) {
        List<DiagnosisSignal> result = new ArrayList<>();
        ParsedFeature feature = context.getParsedFeature();

        if (feature.getCrashSignal() != null && !feature.getCrashSignal().isBlank()) {
            result.add(DiagnosisSignal.builder().type("signal").label("崩溃信号").value(feature.getCrashSignal()).severity("high").build());
        }
        if (feature.getCrashAddress() != null && !feature.getCrashAddress().isBlank()) {
            result.add(DiagnosisSignal.builder().type("address").label("崩溃地址").value(feature.getCrashAddress()).severity("high").build());
        }

        for (String signal : context.getExtractedSignals()) {
            result.add(DiagnosisSignal.builder().type("feature").label("异常特征").value(signal).severity("medium").build());
        }

        List<String> topFrames = context.getStackFrames().stream().limit(3).toList();
        for (String frame : topFrames) {
            result.add(DiagnosisSignal.builder().type("stack").label("关键栈帧").value(frame).severity("medium").build());
        }
        return result;
    }

    public List<String> buildRootCauseHints(DpdkDiagnosisContext context) {
        List<String> hints = new ArrayList<>();
        ParsedFeature feature = context.getParsedFeature();

        hints.add(buildCategoryHeadline(context));

        if (feature.getCrashSignal() != null && !feature.getCrashSignal().isBlank()) {
            hints.add("崩溃信号: " + feature.getCrashSignal());
        }
        if (feature.getCrashAddress() != null && !feature.getCrashAddress().isBlank()) {
            hints.add("崩溃地址: " + feature.getCrashAddress());
        }

        String topFrame = firstMeaningfulFrame(context.getStackFrames());
        if (topFrame != null) {
            hints.add("首个有效栈帧: " + topFrame);
        }

        if (!context.getMatchedCaseTags().isEmpty()) {
            hints.add("匹配案例: " + String.join(" / ", context.getMatchedCaseTags()));
        }

        hints.addAll(context.getExtractedSignals().stream()
                .filter(signal -> signal != null && !signal.isBlank())
                .distinct()
                .limit(5)
                .toList());
        return hints;
    }

    public String buildSummary(DpdkDiagnosisContext context) {
        ParsedFeature feature = context.getParsedFeature();
        String signal = feature.getCrashSignal() == null ? "未知信号" : feature.getCrashSignal();
        String topFrame = firstMeaningfulFrame(context.getStackFrames());
        String frameSummary = topFrame == null ? "当前未提取到可读函数名" : "首个有效栈帧为 " + topFrame;

        return switch (context.getTopCategory()) {
            case MEMORY_FAULT -> "AI 将本次问题归类为内存访问异常，崩溃信号为 " + signal + "，" + frameSummary + "，优先排查空指针、越界访问和释放后继续访问。";
            case MBUF_FAULT -> "AI 将本次问题归类为 mbuf / mempool 生命周期异常，崩溃信号为 " + signal + "，" + frameSummary + "，建议重点检查分配、释放、引用计数和队列回收路径。";
            case THREAD_CONTENTION_FAULT -> "AI 将本次问题归类为多核线程资源竞争，崩溃信号为 " + signal + "，" + frameSummary + "，建议重点检查共享 ring、锁和原子操作路径。";
            case DRIVER_PMD_FAULT -> "AI 将本次问题归类为驱动 / PMD / 设备适配异常，崩溃信号为 " + signal + "，" + frameSummary + "，建议核对驱动绑定、端口能力与初始化流程。";
            case EAL_INIT_FAULT -> "AI 将本次问题归类为 EAL / hugepage / NUMA 初始化异常，崩溃信号为 " + signal + "，" + frameSummary + "，建议检查 hugepage、socket-mem 和环境可见性。";
            case CONFIG_FAULT -> "AI 将本次问题归类为配置参数异常，崩溃信号为 " + signal + "，" + frameSummary + "，建议检查端口、队列、RSS 和启动参数。";
            default -> "AI 已提取到部分异常特征，但当前案例匹配度不足。" + frameSummary + "，建议补充正确 ELF、完整日志和更完整的栈信息后重试。";
        };
    }

    public String buildSuspectedRootCause(DpdkDiagnosisContext context) {
        ParsedFeature feature = context.getParsedFeature();
        String topFrame = firstMeaningfulFrame(context.getStackFrames());
        String frameHint = topFrame == null ? "当前没有可靠函数名" : "栈顶首先落在 " + topFrame;

        return switch (context.getTopCategory()) {
            case MEMORY_FAULT -> "疑似根因：非法地址访问、空指针解引用、已释放对象再次访问，或 memcpy / 指针运算导致内存破坏。" + frameHint + "，请优先核对对象生命周期与边界检查。";
            case MBUF_FAULT -> "疑似根因：mbuf 重复释放、mempool 耗尽、引用计数错误、跨线程传递不当，或 TX/RX 队列回收不完整。" + frameHint + "，请重点检查 mbuf alloc/free 配对。";
            case THREAD_CONTENTION_FAULT -> "疑似根因：多个 lcore 并发访问共享 ring / mempool / 队列，缺少同步或使用了错误的单生产者/单消费者模式。" + frameHint + "，请检查并发模型与锁粒度。";
            case DRIVER_PMD_FAULT -> "疑似根因：PMD 驱动与设备能力不匹配、驱动绑定冲突、设备初始化顺序错误，或 link/queue 配置与硬件不兼容。" + frameHint + "。";
            case EAL_INIT_FAULT -> "疑似根因：EAL 参数错误、hugepage 不足、NUMA 绑定不匹配、PCI 绑定失败，或 WSL2/Linux 环境下设备不可见。" + frameHint + "。";
            case CONFIG_FAULT -> "疑似根因：启动参数、端口配置、核绑定、队列数量或 RSS/NUMA 参数设置不合理。" + frameHint + "。";
            default -> {
                if (feature.getCrashSignal() != null && feature.getCrashSignal().equalsIgnoreCase("SIGFPE")) {
                    yield "疑似根因：算术异常，例如除零、非法取模或浮点运算错误。当前分类信号不足，但崩溃信号已明确指向 SIGFPE，请优先检查涉及除法和取模的代码路径。";
                }
                yield "疑似根因：当前异常特征不足以准确归类，建议补充匹配的 ELF、完整日志和 core 关联场景。" + frameHint + "。";
            }
        };
    }

    public List<TroubleshootingStep> buildTroubleshootingSteps(DpdkDiagnosisContext context) {
        List<String> details = switch (context.getTopCategory()) {
            case MEMORY_FAULT -> List.of(
                    "检查崩溃栈顶附近是否出现 memcpy、memset、free、rte_pktmbuf_free 等内存访问函数。",
                    "核对指针来源、数组下标、结构体偏移和对象生命周期，排查空指针与越界访问。",
                    "若有可执行文件符号，结合 gdb bt full、info locals 进一步定位具体对象。",
                    "建议在 WSL2 Ubuntu 中加入 ASAN、UBSAN 或 mempool debug 选项复现问题。"
            );
            case MBUF_FAULT -> List.of(
                    "排查 rte_pktmbuf_alloc、rte_pktmbuf_free、rte_pktmbuf_clone、rte_mempool_get / put 调用配对。",
                    "检查 mbuf 是否被多个线程共享、重复释放，或在 burst 发送失败后未正确回收。",
                    "核对 mempool cache、buffer size、headroom、indirect mbuf 和 refcnt 变化。",
                    "必要时打开 mempool audit、mbuf sanity check 和日志追踪。"
            );
            case THREAD_CONTENTION_FAULT -> List.of(
                    "检查共享 ring、共享 mempool、共享统计对象是否被多个 lcore 同时访问。",
                    "确认 ring / queue 的生产者消费者模型是否与实际并发模式一致。",
                    "核对锁、原子变量、内存屏障及临界区范围，排查 ABA、竞态更新和伪共享。",
                    "通过线程栈、日志时间序列和 lcore 绑定关系复盘资源竞争路径。"
            );
            case DRIVER_PMD_FAULT -> List.of(
                    "检查设备是否正确绑定到 DPDK 驱动，确认 vfio-pci / igb_uio / 内核驱动状态。",
                    "核对 PMD 初始化日志、端口能力、queue 配置和 offload 特性是否匹配。",
                    "排查不同驱动版本、固件版本、DPDK 版本之间的兼容性冲突。",
                    "若是 WSL2 场景，确认该设备类型是否支持当前环境的透传或访问方式。"
            );
            case EAL_INIT_FAULT -> List.of(
                    "检查 EAL 参数、coremask / lcore-list、socket-mem、huge-dir、file-prefix 等配置。",
                    "确认 hugepage 数量和挂载状态，核对 NUMA 节点与 CPU 绑定是否一致。",
                    "检查 PCI 设备枚举、绑定状态以及 WSL2 / Linux 宿主机下的可见性。",
                    "必要时保留完整启动日志，重点关注 EAL、mempool、ethdev 初始化阶段。"
            );
            case CONFIG_FAULT -> List.of(
                    "检查端口号、队列数、RSS、MTU、burst size、descriptor 数量等配置是否越界。",
                    "核对启动脚本、环境变量、配置文件和实际部署参数是否一致。",
                    "检查 WSL2 Ubuntu 与 Windows 侧路径、权限、挂载点和网络配置是否一致。",
                    "结合日志中的 invalid / failed / unsupported 关键字确认配置冲突点。"
            );
            default -> List.of(
                    "补充与 core 同时刻的运行日志和启动参数，提升案例匹配准确率。",
                    "确认 gdb 已加载正确可执行文件符号，避免只有裸地址没有函数名。",
                    "优先查看栈顶 3-5 帧、异常信号和错误关键词，缩小排查范围。"
            );
        };

        List<TroubleshootingStep> steps = new ArrayList<>();
        for (int i = 0; i < details.size(); i++) {
            steps.add(TroubleshootingStep.builder()
                    .order(i + 1)
                    .title("排查步骤 " + (i + 1))
                    .detail(details.get(i))
                    .build());
        }
        return steps;
    }

    private void applyMemoryCaseRules(ParsedFeature feature, String text, String topFrame, EnumMap<FaultCategory, Double> scores, Set<String> tags, Set<String> signals) {
        if (containsAny(text, "sigsegv", "sigbus", "segmentation fault", "invalid address", "memcpy", "memset", "abort", "assert")) {
            bump(scores, FaultCategory.MEMORY_FAULT, 3.5);
            tags.add("内存访问异常案例");
        }
        if (containsAny(topFrame, "memcpy", "memset", "free", "malloc", "rte_free", "abort", "panic")) {
            bump(scores, FaultCategory.MEMORY_FAULT, 2.8);
            signals.add("栈顶函数更接近内存访问或异常终止路径");
        }
        if (feature.getCrashAddress() != null && !feature.getCrashAddress().isBlank()) {
            signals.add("检测到崩溃地址: " + feature.getCrashAddress());
        }
        if (containsAny(text, "use after free", "double free", "invalid pointer", "corrupted")) {
            bump(scores, FaultCategory.MEMORY_FAULT, 2.2);
            signals.add("原始输出出现内存破坏相关特征");
        }
    }

    private void applyMbufCaseRules(String text, String topFrame, EnumMap<FaultCategory, Double> scores, Set<String> tags, Set<String> signals) {
        int mbufHits = countAny(text, "mbuf", "rte_pktmbuf", "mempool", "rte_mempool", "refcnt", "rx_burst", "tx_burst");
        if (mbufHits > 0) {
            bump(scores, FaultCategory.MBUF_FAULT, 1.0 + mbufHits * 0.35);
            tags.add("mbuf / mempool 生命周期案例");
            signals.add("检测到 mbuf/mempool 相关调用栈或日志特征");
        }
        if (containsAny(topFrame, "rte_pktmbuf", "mbuf", "mempool", "rte_mempool", "rx_burst", "tx_burst")) {
            bump(scores, FaultCategory.MBUF_FAULT, 3.2);
            signals.add("栈顶函数直接落在 mbuf/mempool 路径");
        }
        if (containsAny(text, "rte_pktmbuf_free", "rte_pktmbuf_alloc", "mempool_get", "mempool_put")) {
            signals.add("出现关键 mbuf 分配/释放路径");
        }
        if (containsAny(text, "pool empty", "mempool exhausted", "no mbuf", "refcnt")) {
            bump(scores, FaultCategory.MBUF_FAULT, 2.6);
            signals.add("疑似 mempool 耗尽或 mbuf 引用计数异常");
        }
    }

    private void applyThreadContentionRules(ParsedFeature feature, String text, String topFrame, EnumMap<FaultCategory, Double> scores, Set<String> tags, Set<String> signals) {
        int threadMarkers = countAny(text, "spinlock", "deadlock", "race", "contention", "compare_exchange", "atomic", "pthread_mutex", "pthread_spin", "rwlock");
        int queueMarkers = countAny(text, "ring full", "ring empty", "enqueue", "dequeue", "lock contention");
        boolean topFrameLooksThreaded = containsAny(topFrame, "spinlock", "pthread", "rte_ring", "enqueue", "dequeue", "atomic", "lock");
        boolean hasEnoughThreadEvidence = threadMarkers >= 2 || queueMarkers >= 1 || topFrameLooksThreaded;

        if (hasEnoughThreadEvidence) {
            bump(scores, FaultCategory.THREAD_CONTENTION_FAULT, 1.6 + threadMarkers * 0.45 + queueMarkers * 0.8);
            tags.add("多核线程资源竞争案例");
            signals.add("检测到多线程 / 锁竞争 / 队列竞争特征");
        }
        if (topFrameLooksThreaded) {
            bump(scores, FaultCategory.THREAD_CONTENTION_FAULT, 2.6);
            signals.add("栈顶函数直接落在锁、原子或 ring 并发路径");
        }
        if (!hasEnoughThreadEvidence && feature.getThreadInfo() != null && splitLines(feature.getThreadInfo()).size() >= 3) {
            signals.add("检测到多个线程，但证据不足以单独判定为资源竞争");
        }
    }

    private void applyDriverCaseRules(String text, String topFrame, EnumMap<FaultCategory, Double> scores, Set<String> tags, Set<String> signals) {
        int driverHits = countAny(text, "pmd", "ethdev", "mlx5", "ixgbe", "i40e", "vfio", "pci", "device", "probe", "driver", "link down");
        if (driverHits > 0) {
            bump(scores, FaultCategory.DRIVER_PMD_FAULT, 1.4 + driverHits * 0.35);
            tags.add("驱动适配 / PMD 冲突案例");
            signals.add("检测到 PMD / 驱动 / 设备初始化关键字");
        }
        if (containsAny(topFrame, "mlx5", "ixgbe", "i40e", "ethdev", "pmd", "vfio", "pci")) {
            bump(scores, FaultCategory.DRIVER_PMD_FAULT, 3.0);
            signals.add("栈顶函数更接近 PMD / 驱动 / 设备初始化路径");
        }
        if (containsAny(text, "probe failed", "device not found", "unsupported device", "bind failed")) {
            bump(scores, FaultCategory.DRIVER_PMD_FAULT, 2.7);
            signals.add("疑似设备探测或驱动绑定失败");
        }
    }

    private void applyEalRules(String text, String topFrame, EnumMap<FaultCategory, Double> scores, Set<String> tags, Set<String> signals) {
        int ealHits = countAny(text, "rte_eal", "eal", "hugepage", "socket-mem", "numa", "file-prefix", "cannot init memory", "cannot reserve memory");
        if (ealHits > 0) {
            bump(scores, FaultCategory.EAL_INIT_FAULT, 1.3 + ealHits * 0.4);
            tags.add("EAL / hugepage / NUMA 初始化案例");
            signals.add("检测到 EAL 初始化相关特征");
        }
        if (containsAny(topFrame, "rte_eal", "eal", "hugepage", "numa")) {
            bump(scores, FaultCategory.EAL_INIT_FAULT, 2.8);
            signals.add("栈顶函数更接近 EAL 初始化阶段");
        }
        if (containsAny(text, "hugepage", "no free hugepages", "socket-mem", "cannot init eal")) {
            bump(scores, FaultCategory.EAL_INIT_FAULT, 2.4);
            signals.add("疑似 hugepage 或 EAL 启动参数异常");
        }
    }

    private void applyConfigRules(String text, String topFrame, EnumMap<FaultCategory, Double> scores, Set<String> tags, Set<String> signals) {
        int configHits = countAny(text, "invalid", "unsupported", "failed", "parameter", "config", "option", "queue", "port", "rss", "mtu");
        if (configHits > 0) {
            bump(scores, FaultCategory.CONFIG_FAULT, 1.0 + configHits * 0.25);
            tags.add("配置参数异常案例");
        }
        if (containsAny(topFrame, "config", "parse", "option", "arg", "queue", "port")) {
            bump(scores, FaultCategory.CONFIG_FAULT, 2.3);
            signals.add("栈顶函数更接近参数解析或配置处理路径");
        }
        if (containsAny(text, "invalid port", "invalid queue", "unsupported offload", "bad argument", "cannot parse")) {
            bump(scores, FaultCategory.CONFIG_FAULT, 2.3);
            signals.add("疑似参数配置不合法或设备能力不支持");
        }
    }

    private double recalculateConfidence(Map<FaultCategory, Double> scores, double baseConfidence) {
        double total = scores.values().stream().mapToDouble(Double::doubleValue).sum();
        double max = scores.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        if (total <= 0.0) {
            return Math.max(0.35, baseConfidence);
        }
        return Math.max(baseConfidence, Math.min(0.995, max / total));
    }

    private static void bump(EnumMap<FaultCategory, Double> scores, FaultCategory category, double delta) {
        scores.merge(category, delta, Double::sum);
    }

    private String buildCategoryHeadline(DpdkDiagnosisContext context) {
        ParsedFeature feature = context.getParsedFeature();
        String signal = feature.getCrashSignal();
        return switch (context.getTopCategory()) {
            case MEMORY_FAULT -> signal != null && signal.equalsIgnoreCase("SIGABRT")
                    ? "更接近内存破坏或主动 abort 触发的异常终止，而不是普通空指针访问。"
                    : "更接近非法地址访问、空指针解引用或越界写导致的内存故障。";
            case MBUF_FAULT -> "更接近 mbuf / mempool 生命周期问题，例如重复释放、引用计数错误或回收路径不完整。";
            case THREAD_CONTENTION_FAULT -> "更接近多线程竞争或共享资源访问模型不匹配，而不是单点内存访问错误。";
            case DRIVER_PMD_FAULT -> "更接近 PMD 驱动、设备初始化或硬件能力适配问题。";
            case EAL_INIT_FAULT -> "更接近 EAL、hugepage、NUMA 或运行环境初始化问题。";
            case CONFIG_FAULT -> "更接近参数配置、端口/队列设置或启动选项不合法。";
            default -> signal != null && signal.equalsIgnoreCase("SIGFPE")
                    ? "崩溃信号已明确为 SIGFPE，更接近除零或非法算术运算，而不是泛化的内存错误。"
                    : context.getTopCategory().getDescription();
        };
    }

    private String firstMeaningfulFrame(List<String> frames) {
        if (frames == null) {
            return null;
        }
        return frames.stream()
                .filter(frame -> frame != null && !frame.isBlank())
                .filter(frame -> !frame.contains("??"))
                .findFirst()
                .orElse(null);
    }

    private static boolean containsAny(String text, String... tokens) {
        return Arrays.stream(tokens).anyMatch(text::contains);
    }

    private static int countAny(String text, String... tokens) {
        int count = 0;
        for (String token : tokens) {
            int index = 0;
            while ((index = text.indexOf(token, index)) >= 0) {
                count++;
                index += token.length();
            }
        }
        return count;
    }

    private static String join(String... parts) {
        return Arrays.stream(parts)
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining("\n"));
    }

    private static List<String> splitLines(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        return Arrays.stream(content.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }
}
