package com.growmighty.lectures.firstday.cart.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectApiData(
    @JsonProperty("projectId") Long projectId,
    @JsonProperty("id") Long id,
    @JsonProperty("title") String title,
    @JsonProperty("status") String status,
    @JsonProperty("closed") Boolean closed,
    @JsonProperty("orderable") Boolean orderable
) {
    public Long projectId() {
        return projectId != null ? projectId : id;
    }

    public Boolean orderable() {
        if (orderable != null) return orderable;
        if (status != null) return "IN_PROGRESS".equals(status);
        if (closed != null) return !closed;
        return true;
    }
}
