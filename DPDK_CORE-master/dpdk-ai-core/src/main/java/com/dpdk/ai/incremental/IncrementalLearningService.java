package com.dpdk.ai.incremental;

import com.dpdk.ai.feature.HybridFeatureVector;
import com.dpdk.ai.incremental.entity.FaultCaseRecord;
import com.dpdk.ai.incremental.repository.FaultCaseRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 增量学习入口：持久化标注样本，供离线重训练或外部训练流水线拉取。
 */
@Service
@RequiredArgsConstructor
public class IncrementalLearningService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FaultCaseRecordRepository faultCaseRecordRepository;

    @Transactional
    public FaultCaseRecord recordDiagnosisSample(Long fileId,
                                                 String fileType,
                                                 HybridFeatureVector vector,
                                                 String predictedFaultCode,
                                                 String userFeedbackCode,
                                                 String notes) {
        FaultCaseRecord r = FaultCaseRecord.builder()
                .fileId(fileId)
                .fileType(fileType)
                .featureVectorJson(toJson(vector))
                .predictedFaultCode(predictedFaultCode)
                .userFeedbackCode(userFeedbackCode)
                .notes(notes)
                .build();
        return faultCaseRecordRepository.save(r);
    }

    private String toJson(HybridFeatureVector v) {
        try {
            return OBJECT_MAPPER.writeValueAsString(v);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
