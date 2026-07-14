package com.growmighty.lectures.firstday.settlement.presentation;

import com.growmighty.lectures.firstday.common.response.ApiResponse;
import com.growmighty.lectures.firstday.settlement.read.OrderRepository;
import com.growmighty.lectures.firstday.settlement.application.NaiveSettlementService;
import com.growmighty.lectures.firstday.settlement.application.SettlementBatchService;
import com.growmighty.lectures.firstday.settlement.application.dto.SettleReport;
import com.growmighty.lectures.firstday.settlement.domain.SettlementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 정산 실습용 트리거 엔드포인트. (Step1 - "배치가 필요한 이유")
 *
 * <ul>
 *   <li>POST /settlements/naive          : [Step1-데모1] findAll 전량 적재 → 대용량에서 즉시 OOM</li>
 *   <li>POST /settlements/naive?limit=N  : [Step1-데모2] N건까지 메모리에 쌓으며 정산 → 메모리/시간 증가 추세 관찰</li>
 *   <li>POST /settlements/batch          : [Step2] Spring Batch Chunk 지향 처리 → 메모리 일정하게 유지</li>
 *   <li>POST /settlements/batch?failAt=0.5      : [Step3-1] 50% 지점에서 강제 실패(장애 시나리오)</li>
 *   <li>POST /settlements/batch/restart?runId=1 : [Step3-2] 같은 runId 로 네이티브 재시작(이어서 처리)</li>
 *   <li>POST /settlements/batch/multi-threaded?threads=N  : [Step4-1·4-2] 풀 고갈 / 읽기 깔때기(처리량 천장)</li>
 *   <li>POST /settlements/batch/multi-threaded/restart?runId=1&failAt=0.5 : [Step4-3] saveState=false → 재시작이 처음부터</li>
 *   <li>POST /settlements/batch/partitioned?gridSize=N    : [Step4-4] 파티셔닝(정답) — 속도+재시작 둘 다</li>
 *   <li>GET  /settlements/status         : 주문/정산 건수 + 현재 힙 상태 확인</li>
 *   <li>DELETE /settlements              : 정산 결과 비우기 (데모 반복용)</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/settlements")
public class SettlementController {

    private static final long MB = 1024 * 1024;

    private final NaiveSettlementService naiveSettlementService;
    private final SettlementBatchService settlementBatchService;
    private final SettlementRepository settlementRepository;
    private final OrderRepository orderRepository;

    /**
     * [데모1/데모2] limit 이 없으면 findAll 전량 적재(OOM 데모),
     * limit 이 있으면 그 수만큼만 메모리에 쌓으며 정산(추세 관찰).
     */
    @PostMapping("/naive")
    public ApiResponse<SettleReport> settleNaive(@RequestParam(required = false) Integer limit) {
        SettleReport report = limit == null
                ? naiveSettlementService.settleAll()
                : naiveSettlementService.settleUpTo(limit);
        return ApiResponse.ok(report);
    }

    /**
     * [Step2] Spring Batch Chunk 지향 정산. Reader(페이지) → Processor(변환) → Writer(적재).
     * 대용량이어도 chunk 크기만큼만 메모리를 쓰며 끝까지 완주한다. (naive 와 peakHeapMb 비교)
     *
     * <p>[Step3-1] {@code failAt} 을 주면(예: 0.5) 그 비율 지점에서 일부러 실패시킨다(장애 시나리오).
     * {@code failAt} 없이 다시 호출하면 멱등 스킵으로 이미 정산된 부분은 건너뛰고 나머지만 처리한다.
     */
    @PostMapping("/batch")
    public ApiResponse<SettleReport> settleBatch(@RequestParam(required = false) Double failAt) {
        SettleReport report = failAt == null
                ? settlementBatchService.run()
                : settlementBatchService.runFailing(failAt);
        return ApiResponse.ok(report);
    }

    /**
     * [Step3-2] Spring Batch 네이티브 재시작 데모. <b>같은 runId</b> 로 다시 호출하면
     * 새 인스턴스가 아니라 실패한 그 인스턴스를 <b>이어서</b> 재개한다.
     *
     * <ul>
     *   <li>1차: {@code POST /settlements/batch/restart?runId=1&failAt=0.5} → 50%에서 FAILED</li>
     *   <li>2차: {@code POST /settlements/batch/restart?runId=1}            → 이어서 COMPLETED</li>
     * </ul>
     *
     * @param runId  재시작 키(같은 값=이어서, 다른 값=새로). 기본 1.
     * @param failAt null 이면 정상/재개, 값이 있으면 그 비율에서 실패.
     */
    @PostMapping("/batch/restart")
    public ApiResponse<SettleReport> restartBatch(@RequestParam(defaultValue = "1") long runId,
                                                  @RequestParam(required = false) Double failAt) {
        return ApiResponse.ok(settlementBatchService.runRestartable(runId, failAt));
    }

    /**
     * [Step4-1 · 4-2] Multi-threaded Step — "스레드만 늘리는" 순진한 가속.
     *
     * <p>표준(thread-safe) Reader 라 데이터는 안 꼬인다. 대신:
     * <ul>
     *   <li><b>4-1</b>: {@code threads} 를 HikariCP {@code maximumPoolSize} 보다 크게(예: 풀 2 / threads 10)
     *       주고 작은 풀로 기동하면 커넥션 고갈로 타임아웃 실패. 풀을 키우면 살아난다.</li>
     *   <li><b>4-2</b>: 풀을 충분히 키운 뒤 {@code threads} 를 1→2→4→8 로 올려가며 응답의 {@code elapsedMs}
     *       를 비교하라. 읽기가 Reader 하나(깔때기)로 직렬화돼 시간이 거의 안 준다(처리량 천장).</li>
     * </ul>
     *
     * @param threads 동시 스레드 수. 생략 시 {@code settlement.batch.thread-count} 기본값.
     */
    @PostMapping("/batch/multi-threaded")
    public ApiResponse<SettleReport> settleMultiThreaded(@RequestParam(required = false) Integer threads) {
        return ApiResponse.ok(settlementBatchService.runMultiThreaded(threads));
    }

    /**
     * [Step4-3] Multi-threaded Step 의 <b>재시작 상실</b> 데모. 같은 runId 로 다시 호출한다.
     * {@code saveState=false} 라 재시작이 "이어서"가 아니라 <b>처음부터 다시 읽기</b>가 된다.
     *
     * <ul>
     *   <li>1차: {@code POST /settlements/batch/multi-threaded/restart?runId=1&failAt=0.5} → 50%에서 FAILED</li>
     *   <li>2차: {@code POST /settlements/batch/multi-threaded/restart?runId=1} → COMPLETED 이지만
     *       {@code readCount} 가 전체에 가깝다(=앞부분 재독). Step3 단일스레드 재시작과 비교.</li>
     * </ul>
     */
    @PostMapping("/batch/multi-threaded/restart")
    public ApiResponse<SettleReport> restartMultiThreaded(@RequestParam(defaultValue = "1") long runId,
                                                          @RequestParam(required = false) Double failAt) {
        return ApiResponse.ok(settlementBatchService.runMultiThreadedRestartable(runId, failAt));
    }

    /**
     * [Step4-4] Partitioning — id 범위를 {@code gridSize} 개로 나눠 워커마다 전용 Reader 로 병렬 처리.
     * 입구가 여러 개라 4-2 의 깔때기가 사라지고(진짜 병렬), 워커마다 독립 체크포인트(saveState=true)라
     * 4-3 에서 잃었던 재시작도 구조적으로 보존된다. 멱등 재실행도 안전(= {@code settledCount=0, skipped=전체}).
     *
     * @param gridSize 파티션 수. 생략 시 {@code settlement.batch.grid-size} 기본값.
     */
    @PostMapping("/batch/partitioned")
    public ApiResponse<SettleReport> settlePartitioned(@RequestParam(required = false) Integer gridSize) {
        return ApiResponse.ok(settlementBatchService.runPartitioned(gridSize));
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / MB;
        long maxMb = rt.maxMemory() / MB;
        return ApiResponse.ok(Map.of(
                "orderCount", orderRepository.count(),
                "settlementCount", settlementRepository.count(),
                "heapUsedMb", usedMb,
                "heapMaxMb", maxMb));
    }

    @DeleteMapping
    public ApiResponse<Void> clear() {
        settlementRepository.deleteAll();
        return ApiResponse.ok();
    }
}
