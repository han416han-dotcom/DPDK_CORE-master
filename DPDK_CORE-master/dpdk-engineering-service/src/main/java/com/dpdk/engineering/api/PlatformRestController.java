package com.dpdk.engineering.api;

import com.dpdk.ai.api.DiagnosisResult;
import com.dpdk.ai.api.RepairSuggestion;
import com.dpdk.ai.core.FaultAnalysisEngine;
import com.dpdk.ai.feature.HybridFeatureVector;
import com.dpdk.ai.incremental.IncrementalLearningService;
import com.dpdk.ai.knowledge.KnowledgeGraphService;
import com.dpdk.collector.entity.CoredumpFile;
import com.dpdk.collector.entity.LogFile;
import com.dpdk.collector.parser.ParseBackend;
import com.dpdk.engineering.service.OneClickParseService;
import com.dpdk.engineering.service.ReportExportService;
import com.lowagie.text.DocumentException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
public class PlatformRestController {

    private final OneClickParseService oneClickParseService;
    private final FaultAnalysisEngine faultAnalysisEngine;
    private final KnowledgeGraphService knowledgeGraphService;
    private final ReportExportService reportExportService;
    private final IncrementalLearningService incrementalLearningService;

    @PostMapping("/parse/coredump/{id}")
    public ResponseEntity<CoredumpFile> parseCoredump(@PathVariable Long id,
                                                      @RequestParam(required = false) String backend) {
        ParseBackend b = null;
        if (backend != null && !backend.isBlank()) {
            b = ParseBackend.valueOf(backend.trim());
        }
        return ResponseEntity.ok(oneClickParseService.triggerCoredumpParse(id, b));
    }

    @PostMapping("/parse/log/{id}")
    public ResponseEntity<LogFile> parseLog(@PathVariable Long id) {
        return ResponseEntity.ok(oneClickParseService.triggerLogParse(id));
    }

    @PostMapping("/diagnose")
    public ResponseEntity<DiagnosisResult> diagnose(@RequestBody DiagnoseRequest req) {
        Optional<DiagnosisResult> r = faultAnalysisEngine.diagnose(req.getFileId(), req.getFileType());
        return r.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/repairs/{faultCode}")
    public List<RepairSuggestion> repairs(@PathVariable String faultCode) {
        return knowledgeGraphService.suggestRepairs(faultCode);
    }

    @GetMapping(value = "/report/{fileId}", produces = MediaType.TEXT_MARKDOWN_VALUE)
    public ResponseEntity<byte[]> reportMarkdown(
            @PathVariable Long fileId,
            @RequestParam(defaultValue = "COREDUMP") String fileType) {
        return faultAnalysisEngine.diagnose(fileId, fileType)
                .map(d -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                                .filename("report-" + fileId + ".md").build().toString())
                        .body(reportExportService.toMarkdownBytes(d)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/report/{fileId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> reportPdf(
            @PathVariable Long fileId,
            @RequestParam(defaultValue = "COREDUMP") String fileType) throws DocumentException {
        Optional<DiagnosisResult> d = faultAnalysisEngine.diagnose(fileId, fileType);
        if (d.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        byte[] pdf = reportExportService.toPdf(d.get());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("report-" + fileId + ".pdf").build().toString())
                .body(pdf);
    }

    @PostMapping("/feedback")
    public ResponseEntity<Void> feedback(@RequestBody FeedbackRequest req) {
        faultAnalysisEngine.diagnose(req.getFileId(), req.getFileType()).ifPresent(d -> {
            var vec = d.getFeatureSnapshot();
            var hybrid = HybridFeatureVector.builder()
                    .ruleFeatures(vec.getRuleFeatures())
                    .cnnEmbedding(vec.getCnnEmbedding())
                    .combinedVector(vec.getCombinedVector())
                    .build();
            incrementalLearningService.recordDiagnosisSample(
                    req.getFileId(),
                    req.getFileType(),
                    hybrid,
                    d.getFaultCode(),
                    req.getUserFeedbackCode(),
                    req.getNotes());
        });
        return ResponseEntity.accepted().build();
    }

    @Data
    public static class DiagnoseRequest {
        @NotNull
        private Long fileId;
        @NotBlank
        private String fileType;
    }

    @Data
    public static class FeedbackRequest {
        @NotNull
        private Long fileId;
        @NotBlank
        private String fileType;
        private String userFeedbackCode;
        private String notes;
    }
}
