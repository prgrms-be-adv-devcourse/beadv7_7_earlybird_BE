package com.growmighty.lectures.firstday.project.application;

import com.growmighty.lectures.firstday.common.exception.EntityNotFoundException;
import com.growmighty.lectures.firstday.project.application.dto.RegisterRewardCommand;
import com.growmighty.lectures.firstday.project.application.dto.RewardInfo;
import com.growmighty.lectures.firstday.project.domain.Reward;
import com.growmighty.lectures.firstday.project.domain.RewardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 리워드(후원 옵션) 애플리케이션 서비스.
 * 재고 변경은 검색 색인 이벤트를 발행하지 않는다 — 검색 문서(ProjectDocument)에 재고 필드가 없다.
 */
@Service
@RequiredArgsConstructor
public class RewardService {
    private final RewardRepository rewardRepository;

    @Transactional
    public RewardInfo register(RegisterRewardCommand command) {
        // TODO(팀): 존재하는 프로젝트인지 + 등록 가능한 상태(DRAFT/IN_REVIEW?)인지 검증
        Reward reward = Reward.register(
            command.projectId(), command.name(), command.description(),
            command.price(), command.totalQuantity());
        return RewardInfo.from(rewardRepository.save(reward));
    }

    @Transactional(readOnly = true)
    public RewardInfo getReward(Long rewardId) {
        return RewardInfo.from(getRewardEntity(rewardId));
    }

    @Transactional(readOnly = true)
    public List<RewardInfo> getRewardsByProject(Long projectId) {
        return rewardRepository.findByProjectId(projectId).stream()
            .map(RewardInfo::from)
            .toList();
    }

    @Transactional
    public void decreaseStock(Long rewardId, int quantity) {
        getRewardEntity(rewardId).decreaseStock(quantity);
    }

    @Transactional
    public void restoreStock(Long rewardId, int quantity) {
        getRewardEntity(rewardId).restoreStock(quantity);
    }

    private Reward getRewardEntity(Long rewardId) {
        return rewardRepository.findById(rewardId)
            .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 리워드입니다. rewardId=" + rewardId));
    }
}
