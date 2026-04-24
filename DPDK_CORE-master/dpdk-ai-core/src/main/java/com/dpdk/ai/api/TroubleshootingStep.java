package com.dpdk.ai.api;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TroubleshootingStep {
    int order;
    String title;
    String detail;
}
