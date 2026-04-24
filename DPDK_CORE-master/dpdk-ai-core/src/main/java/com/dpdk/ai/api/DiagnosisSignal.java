package com.dpdk.ai.api;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DiagnosisSignal {
    String type;
    String label;
    String value;
    String severity;
}
