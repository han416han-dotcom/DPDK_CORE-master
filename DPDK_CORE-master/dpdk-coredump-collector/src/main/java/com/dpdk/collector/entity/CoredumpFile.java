package com.dpdk.collector.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "coredump_file")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoredumpFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String fileName;
    
    @Column(nullable = false)
    private String filePath;
    
    @Column(nullable = false)
    private Long fileSize;
    
    @Column(nullable = false, unique = true)
    private String fileHash;
    
    @Column(nullable = false)
    private LocalDateTime generateTime;
    
    @Column(nullable = false)
    private LocalDateTime uploadTime;
    
    @Column(nullable = false)
    private String sourceType; // AUTO_SCAN/MANUAL_UPLOAD
    
    @Column(nullable = false)
    private String status; // PENDING/PARSING/PARSED/FAILED
    
    private String errorMessage;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}