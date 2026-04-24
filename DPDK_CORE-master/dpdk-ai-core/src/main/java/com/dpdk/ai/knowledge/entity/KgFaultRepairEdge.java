package com.dpdk.ai.knowledge.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "kg_fault_repair_edge")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KgFaultRepairEdge {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "fault_id")
    private KgFaultNode fault;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "repair_id")
    private KgRepairNode repair;

    @Column(nullable = false)
    private Double confidence;

    @Column(length = 255)
    private String scenario;
}
