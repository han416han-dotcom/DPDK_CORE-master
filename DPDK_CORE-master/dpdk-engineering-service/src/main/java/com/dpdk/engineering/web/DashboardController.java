package com.dpdk.engineering.web;

import com.dpdk.ai.api.DiagnosisResult;
import com.dpdk.ai.core.FaultAnalysisEngine;
import com.dpdk.collector.entity.CoredumpFile;
import com.dpdk.collector.parser.ParseBackend;
import com.dpdk.collector.repository.CoredumpFileRepository;
import com.dpdk.engineering.service.OneClickParseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final CoredumpFileRepository coredumpFileRepository;
    private final FaultAnalysisEngine faultAnalysisEngine;
    private final OneClickParseService oneClickParseService;

    @GetMapping("/")
    public String index(Model model,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size) {
        Page<CoredumpFile> files = coredumpFileRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "uploadTime")));
        model.addAttribute("files", files);
        return "index";
    }

    @PostMapping("/ui/parse/{id}")
    public ResponseEntity<?> parse(@PathVariable Long id,
                                   @RequestParam(required = false) String backend) {
        try {
            ParseBackend selectedBackend = null;
            if (backend != null && !backend.isBlank()) {
                selectedBackend = ParseBackend.valueOf(backend.trim());
            }
            oneClickParseService.triggerCoredumpParse(id, selectedBackend);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "diagnosisUrl", "/diagnosis/" + id + "?type=COREDUMP"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/ui/files/{id}")
    public ResponseEntity<?> deleteFile(@PathVariable Long id) {
        try {
            oneClickParseService.deleteCoredumpCompletely(id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/ui/files")
    public ResponseEntity<?> deleteAllFiles() {
        oneClickParseService.deleteAllCoredumps();
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/diagnosis/{id}")
    @SuppressWarnings("null")
    public String diagnosis(Model model,
                            @PathVariable Long id,
                            @RequestParam(defaultValue = "COREDUMP") String type) {
        coredumpFileRepository.findById(id).ifPresent(f -> {
            model.addAttribute("file", f);
            model.addAttribute("fileName", f.getFileName());
            model.addAttribute("fileStatus", f.getStatus());
            model.addAttribute("fileHash", f.getFileHash());
            model.addAttribute("errorMessage", f.getErrorMessage());
            model.addAttribute("filePath", f.getFilePath());
        });
        Optional<DiagnosisResult> r = faultAnalysisEngine.diagnose(id, type);
        model.addAttribute("fileId", id);
        model.addAttribute("fileType", type);
        r.ifPresent(d -> model.addAttribute("diagnosis", d));
        return "diagnosis";
    }
}
