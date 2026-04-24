package com.dpdk.ai.incremental.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "fault_case_record")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaultCaseRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long fileId;

    @Column(nullable = false, length = 20)
    private String fileType;

    @Column(columnDefinition = "TEXT")
    private String featureVectorJson;

    @Column(length = 64)
    private String predictedFaultCode;

    @Column(length = 64)
    private String userFeedbackCode;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
