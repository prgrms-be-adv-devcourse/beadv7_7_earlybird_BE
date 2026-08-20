package com.growmighty.lectures.firstday.ai.policy.infrastructure.search;

import com.growmighty.lectures.firstday.ai.policy.domain.PolicyCategory;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Mapping;
import org.springframework.data.elasticsearch.annotations.Setting;

@Document(indexName = "policies")
@Setting(settingPath = "elasticsearch/policy-index-settings.json")
@Mapping(mappingPath = "elasticsearch/policy-index-mapping.json")
public record PolicyDocument(
    @Id String chunkId,
    PolicyCategory category,
    String topic,
    String content,
    float[] embedding
) {
}
