package com.dpdk.collector.service;

import com.dpdk.collector.entity.ParsedFeature;
import com.dpdk.collector.util.DpdkLogParserUtil;
import com.dpdk.collector.util.GdbParserUtil;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.StringJoiner;

@Service
public class DataNormalizationService {
    
    public ParsedFeature normalizeCoredumpResult(GdbParserUtil.GdbParseResult result) {
        ParsedFeature feature = new ParsedFeature();
        
        feature.setCrashSignal(result.getCrashSignal());
        feature.setCrashAddress(result.getCrashAddress());
        
        // 标准化调用栈
        StringJoiner callStackJoiner = new StringJoiner("\n");
        result.getCallStack().forEach(callStackJoiner::add);
        feature.setCallStack(callStackJoiner.toString());
        
        // 标准化寄存器
        StringJoiner registersJoiner = new StringJoiner("\n");
        result.getRegisters().forEach(registersJoiner::add);
        feature.setRegisters(registersJoiner.toString());
        
        // 提取mbuf相关信息
        List<String> mbufOps = result.getCallStack().stream()
                .filter(line -> line.contains("rte_pktmbuf") || line.contains("mbuf"))
                .toList();
        StringJoiner mbufJoiner = new StringJoiner("\n");
        mbufOps.forEach(mbufJoiner::add);
        feature.setMbufOperations(mbufJoiner.toString());
        
        // 提取线程信息
        StringJoiner threadJoiner = new StringJoiner("\n");
        result.getThreadInfo().forEach(threadJoiner::add);
        feature.setThreadInfo(threadJoiner.toString());
        
        feature.setRawContent(result.getRawOutput());
        
        return feature;
    }
    
    public ParsedFeature normalizeLogResult(DpdkLogParserUtil.LogParseResult result) {
        ParsedFeature feature = new ParsedFeature();
        
        // 提取错误关键词
        StringJoiner errorJoiner = new StringJoiner(",");
        result.getErrorKeywords().forEach(errorJoiner::add);
        feature.setErrorKeywords(errorJoiner.toString());
        
        // 提取mbuf操作记录
        StringJoiner mbufJoiner = new StringJoiner("\n");
        result.getMbufOperations().forEach(mbufJoiner::add);
        feature.setMbufOperations(mbufJoiner.toString());
        
        // 提取内存信息
        StringJoiner memoryJoiner = new StringJoiner("\n");
        result.getMemoryInfo().forEach(memoryJoiner::add);
        feature.setMemoryInfo(memoryJoiner.toString());
        
        // 提取EAL参数
        feature.setEalParameters(result.getEalParameters());
        
        feature.setRawContent(result.getRawContent());
        
        return feature;
    }
}