package com.dpdk.ai.api;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DiagnosisCategoryScore {
    String faultCode;
    String faultName;
    double score;
}
