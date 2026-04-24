package com.dpdk.collector.service;

import com.dpdk.collector.config.ParseBackendConfig;
import com.dpdk.collector.entity.CoredumpFile;
import com.dpdk.collector.entity.LogFile;
import com.dpdk.collector.entity.ParsedFeature;
import com.dpdk.collector.parser.CoredumpParser;
import com.dpdk.collector.parser.ParseBackend;
import com.dpdk.collector.repository.CoredumpFileRepository;
import com.dpdk.collector.repository.LogFileRepository;
import com.dpdk.collector.repository.ParsedFeatureRepository;
import com.dpdk.collector.util.DpdkLogParserUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DataParserService {
    private final CoredumpFileRepository coredumpFileRepository;
    private final LogFileRepository logFileRepository;
    private final ParsedFeatureRepository parsedFeatureRepository;
    private final DataNormalizationService normalizationService;
    private final ParseBackendConfig parseBackendConfig;
    private final List<CoredumpParser> coredumpParsers;
    
    @Async
    public void parseCoredumpFileAsync(CoredumpFile coredumpFile) {
        parseCoredumpFileAsync(coredumpFile, null);
    }

    @Async
    public void parseCoredumpFileAsync(CoredumpFile coredumpFile, ParseBackend backendOverride) {
        try {
            // 更新状态为解析中
            coredumpFile.setStatus("PARSING");
            coredumpFileRepository.save(coredumpFile);
            
            ParseBackend backend = backendOverride != null ? backendOverride : parseBackendConfig.getBackend();
            CoredumpParser parser = coredumpParsers.stream()
                    .filter(p -> p.backend() == backend)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("未找到解析器: " + backend));

            // 调用解析器解析 Core 文件（本机 gdb / WSL2 gdb / 远程 SSH 等）
            File coreFile = new File(coredumpFile.getFilePath());
            var result = parser.parse(coreFile);
            
            // 数据标准化处理
            ParsedFeature feature = normalizationService.normalizeCoredumpResult(result);
            feature.setFileId(coredumpFile.getId());
            feature.setFileType("COREDUMP");
            
            // 保存标准化特征
            parsedFeatureRepository.save(feature);
            
            // 更新状态为解析完成
            coredumpFile.setStatus("PARSED");
            coredumpFileRepository.save(coredumpFile);
            
        } catch (Exception e) {
            // 更新状态为解析失败
            coredumpFile.setStatus("FAILED");
            coredumpFile.setErrorMessage(e.getMessage());
            coredumpFileRepository.save(coredumpFile);
            e.printStackTrace();
        }
    }
    
    @Async
    public void parseLogFileAsync(LogFile logFile) {
        try {
            logFile.setStatus("PARSING");
            logFileRepository.save(logFile);
            
            // 解析DPDK日志文件
            File logFileObj = new File(logFile.getFilePath());
            DpdkLogParserUtil.LogParseResult result = DpdkLogParserUtil.parseLog(logFileObj);
            
            // 数据标准化处理
            ParsedFeature feature = normalizationService.normalizeLogResult(result);
            feature.setFileId(logFile.getId());
            feature.setFileType("LOG");
            
            parsedFeatureRepository.save(feature);
            
            logFile.setStatus("PARSED");
            logFileRepository.save(logFile);
            
        } catch (Exception e) {
            logFile.setStatus("FAILED");
            logFile.setErrorMessage(e.getMessage());
            logFileRepository.save(logFile);
            e.printStackTrace();
        }
    }
}