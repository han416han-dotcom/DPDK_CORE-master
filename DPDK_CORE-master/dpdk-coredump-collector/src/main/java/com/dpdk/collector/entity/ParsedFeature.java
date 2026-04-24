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
    
    @Column(columnDefinition = "TEXT")
    private String callStack;
    
    @Column(columnDefinition = "TEXT")
    private String registers;
    
    @Column(columnDefinition = "TEXT")
    private String mbufOperations;
    
    @Column(columnDefinition = "TEXT")
    private String memoryInfo;
    
    @Column(columnDefinition = "TEXT")
    private String threadInfo;
    
    @Column(columnDefinition = "TEXT")
    private String ealParameters;
    
    @Column(columnDefinition = "TEXT")
    private String errorKeywords;
    
    @Column(columnDefinition = "TEXT")
    private String rawContent;
    
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}