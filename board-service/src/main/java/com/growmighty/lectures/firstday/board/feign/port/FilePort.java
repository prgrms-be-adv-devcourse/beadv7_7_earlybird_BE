package com.growmighty.lectures.firstday.board.feign.port;

import java.util.List;
import java.util.Map;

/**
 * board-service는 file-service의 클래스를 알지 못한다. 오직 이 인터페이스로만 첨부 사진을 바라보고,
 * 실제 통신은 infrastructure 의 HTTP 클라이언트가 담당한다.
 */
public interface FilePort {
    /** reviewId -> 해당 리뷰의 photoUrl 목록. 사진이 없는 reviewId는 결과 맵에 아예 안 들어있을 수 있다. ( 키 기반이라 순서 무관 ) */
    Map<Long, List<String>> getReviewPhotoUrls(List<Long> reviewIds);
}
