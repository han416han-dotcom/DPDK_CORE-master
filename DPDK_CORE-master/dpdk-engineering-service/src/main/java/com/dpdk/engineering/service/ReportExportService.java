package com.dpdk.engineering.service;

import com.dpdk.ai.api.DiagnosisResult;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;

@Service
public class ReportExportService {

    public String toMarkdown(DiagnosisResult d) {
        StringJoiner sj = new StringJoiner("\n");
        sj.add("# DPDK 故障分析报告");
        sj.add("");
        sj.add("- 文件 ID: " + d.getFileId());
        sj.add("- 类型: " + d.getFileType());
        sj.add("- 预测故障: **" + d.getFaultCode() + "** (" + d.getFaultName() + ")");
        sj.add("- 置信度: " + String.format("%.4f", d.getConfidence()));
        sj.add("");
        sj.add("## 根因提示");
        d.getRootCauseHints().forEach(h -> sj.add("- " + h));
        sj.add("");
        sj.add("## 修复建议");
        d.getRepairSuggestions().forEach(r -> {
            sj.add("### " + r.getTitle() + " (`" + r.getRepairCode() + "`)");
            sj.add("- 置信度: " + String.format("%.2f", r.getConfidence()));
            if (r.getReferenceUrl() != null && !r.getReferenceUrl().isBlank()) {
                sj.add("- 参考: " + r.getReferenceUrl());
            }
            sj.add("");
            sj.add("```");
            sj.add(r.getSteps() == null ? "" : r.getSteps());
            sj.add("```");
            sj.add("");
        });
        return sj.toString();
    }

    public byte[] toPdf(DiagnosisResult d) throws DocumentException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, bos);
        document.open();
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 11);
        document.add(new Paragraph("DPDK Fault Analysis Report", titleFont));
        document.add(new Paragraph(" ", bodyFont));
        document.add(new Paragraph("File: " + d.getFileId() + " / " + d.getFileType(), bodyFont));
        document.add(new Paragraph("Fault: " + d.getFaultCode() + " - " + d.getFaultName(), bodyFont));
        document.add(new Paragraph("Confidence: " + String.format("%.4f", d.getConfidence()), bodyFont));
        document.add(new Paragraph(" ", bodyFont));
        document.add(new Paragraph("Hints:", bodyFont));
        for (String h : d.getRootCauseHints()) {
            document.add(new Paragraph("- " + h, bodyFont));
        }
        document.add(new Paragraph(" ", bodyFont));
        document.add(new Paragraph("Repairs:", bodyFont));
        for (var r : d.getRepairSuggestions()) {
            document.add(new Paragraph("* " + r.getTitle() + " (" + r.getRepairCode() + ")", bodyFont));
            if (r.getSteps() != null) {
                document.add(new Paragraph(r.getSteps(), bodyFont));
            }
        }
        document.close();
        return bos.toByteArray();
    }

    public byte[] toMarkdownBytes(DiagnosisResult d) {
        return toMarkdown(d).getBytes(StandardCharsets.UTF_8);
    }
}
