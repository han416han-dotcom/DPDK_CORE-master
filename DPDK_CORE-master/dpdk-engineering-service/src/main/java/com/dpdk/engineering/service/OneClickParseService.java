package com.dpdk.engineering.service;

import com.dpdk.collector.entity.CoredumpFile;
import com.dpdk.collector.entity.LogFile;
import com.dpdk.collector.parser.ParseBackend;
import com.dpdk.collector.repository.CoredumpFileRepository;
import com.dpdk.collector.repository.LogFileRepository;
import com.dpdk.collector.service.DataParserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OneClickParseService {

    private final CoredumpFileRepository coredumpFileRepository;
    private final LogFileRepository logFileRepository;
    private final DataParserService dataParserService;

    @Transactional(readOnly = true)
    public CoredumpFile triggerCoredumpParse(Long id) {
        return triggerCoredumpParse(id, null);
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("null")
    public CoredumpFile triggerCoredumpParse(Long id, ParseBackend backend) {
        CoredumpFile f = coredumpFileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("coredump not found: " + id));
        dataParserService.parseCoredumpFileAsync(f, backend);
        return f;
    }

    @Transactional(readOnly = true)
    @SuppressWarnings("null")
    public LogFile triggerLogParse(Long id) {
        LogFile f = logFileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("log not found: " + id));
        dataParserService.parseLogFileAsync(f);
        return f;
    }
}
