package com.dpdk.collector.service;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ProgramPathResolution {
    String resolvedProgramPath;
    boolean usedConfiguredFallback;
    List<String> attemptedCandidates;
    List<String> inferredProgramNames;
}
