package com.growmighty.lectures.firstday.ai.tool.feign.port.project;

import com.growmighty.lectures.firstday.ai.tool.feign.port.project.dto.RewardResult;

import java.util.List;

public interface RewardPort {

    List<RewardResult> findByProject(Long projectId);

    RewardResult findById(Long rewardId);
}
