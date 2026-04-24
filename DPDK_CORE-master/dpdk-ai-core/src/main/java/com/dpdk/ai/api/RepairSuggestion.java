package com.dpdk.ai.api;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class RepairSuggestion {
    String repairCode;
    String title;
    String steps;
    String referenceUrl;
    double confidence;
}
