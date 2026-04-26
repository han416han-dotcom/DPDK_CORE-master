package com.dpdk.collector.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "parsed_feature")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedFeature {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private Long fileId;
    
    @Column(nullable = false)
    private String fileType; // COREDUMP/LOG
    
    private String crashSignal;
    private String crashAddress;
    
    @Column(columnDefinition = "LONGTEXT")
    private String callStack;
    
    @Column(columnDefinition = "LONGTEXT")
    private String registers;
    
    @Column(columnDefinition = "LONGTEXT")
    private String mbufOperations;
    
    @Column(columnDefinition = "LONGTEXT")
    private String memoryInfo;
    
    @Column(columnDefinition = "LONGTEXT")
    private String threadInfo;
    
    @Column(columnDefinition = "LONGTEXT")
    private String ealParameters;
    
    @Column(columnDefinition = "LONGTEXT")
    private String errorKeywords;
    
    @Column(columnDefinition = "LONGTEXT")
    private String rawContent;
    
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}