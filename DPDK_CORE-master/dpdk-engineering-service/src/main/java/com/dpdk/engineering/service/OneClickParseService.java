package com.dpdk.engineering.service;

import com.dpdk.collector.entity.CoredumpFile;
import com.dpdk.collector.entity.LogFile;
import com.dpdk.collector.parser.ParseBackend;
import com.dpdk.collector.repository.CoredumpFileRepository;
import com.dpdk.collector.repository.LogFileRepository;
import com.dpdk.collector.repository.ParsedFeatureRepository;
import com.dpdk.collector.service.DataParserService;
import com.dpdk.collector.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OneClickParseService {

    private static final String FILE_TYPE_COREDUMP = "COREDUMP";

    private final CoredumpFileRepository coredumpFileRepository;
    private final LogFileRepository logFileRepository;
    private final ParsedFeatureRepository parsedFeatureRepository;
    private final DataParserService dataParserService;
    private final FileStorageService fileStorageService;

    @Transactional
    public CoredumpFile triggerCoredumpParse(Long id) {
        return triggerCoredumpParse(id, null);
    }

    @Transactional
    @SuppressWarnings("null")
    public CoredumpFile triggerCoredumpParse(Long id, ParseBackend backend) {
        CoredumpFile file = coredumpFileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("coredump not found: " + id));
        parsedFeatureRepository.deleteByFileIdAndFileType(id, FILE_TYPE_COREDUMP);
        file.setStatus("PENDING");
        file.setErrorMessage(null);
        CoredumpFile savedFile = coredumpFileRepository.save(file);
        dataParserService.parseCoredumpFileAsync(savedFile, backend);
        return savedFile;
    }

    @Transactional
    @SuppressWarnings("null")
    public void deleteCoredumpCompletely(Long id) {
        CoredumpFile file = coredumpFileRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("coredump not found: " + id));
        parsedFeatureRepository.deleteByFileIdAndFileType(id, FILE_TYPE_COREDUMP);
        fileStorageService.deleteFile(file.getFilePath());
        coredumpFileRepository.delete(file);
    }

    @Transactional
    public void deleteAllCoredumps() {
        List<CoredumpFile> files = coredumpFileRepository.findAll();
        files.forEach(file -> {
            parsedFeatureRepository.deleteByFileIdAndFileType(file.getId(), FILE_TYPE_COREDUMP);
            fileStorageService.deleteFile(file.getFilePath());
        });
        coredumpFileRepository.deleteAllInBatch();
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
