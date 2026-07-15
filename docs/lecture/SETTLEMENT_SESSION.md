# 정산(Settlement) 배치 세션 - 진행 가이드

> 기준 브랜치: `lectures/settlement/step1`
> 기존 주문/결제 모놀리식 위에, 정산을 **Spring Batch** 로 만들어 가는 1일 세션입니다.

이 문서는 **강사용 로드맵**입니다. step1 에는 "출발점"이 되는 코드만 들어 있고,
배치 Job/Step 은 세션 중 라이브 코딩으로 채워 갑니다.

---

## 0. step1 에 미리 세팅된 것 (출발점)

| 영역 | 위치 | 설명 |
|---|---|---|
| 정산 엔티티 | `settlement/domain/Settlement.java` | 결과 테이블. **1원 무결성**(수수료+지급액=주문금액)을 `verifyIntegrity()` 로 강제. `order_id` 유니크 = 멱등성 키 |
| 정산 상태 | `settlement/domain/SettlementStatus.java` | PENDING / COMPLETED / FAILED |
| 리포지토리 | `settlement/domain` + `settlement/infrastructure` | `existsByOrderId` 등 멱등성 체크용 메서드 포함 |
| **안티패턴** 서비스 | `settlement/application/NaiveSettlementService.java` | `settleAll()`=findAll 전량 적재(즉시 OOM), `settleUpTo(limit)`=조금씩 쌓으며 정산(추세 관찰). 둘 다 시간·피크힙 리포트 |
| 힙 모니터 | `settlement/support/HeapMonitor.java` | 작업 중 힙 사용량을 0.5초마다 `[mem]` 막대로 로그 → 메모리 차오르는 과정 실시간 시각화 |
| 트리거 API | `settlement/presentation/SettlementController.java` | `POST /settlements/naive[?limit=N]`, `GET /settlements/status`(건수+힙), `DELETE /settlements` |
| 대용량 시더 | `settlement/support/SettlementDataSeeder.java` | JdbcTemplate 배치 INSERT 로 주문/결제 N건 적재 (`settlement.seed.enabled=true`) |
| 배치 인프라 | `build.gradle`, `application.properties` | `spring-boot-starter-batch`, JobRepository 스키마 자동 생성, Job 자동실행 OFF |
| 요청 모음 | `settlement.http` | 실습 엔드포인트 호출 모음 |

> 데이터 모델 메모: 시더는 `payments.id == orders.id == orders.payment_id` 로 1:1 매핑합니다.
> 그래서 조인 실습은 `orders ⨝ payments ON orders.payment_id = payments.id` 형태가 됩니다.
> 금액은 일부러 3%로 나누어 떨어지지 않게 만들어 반올림(1원) 이슈가 드러나도록 했습니다.

---

## 1. [09:00~13:00] 개념 + 기초 파이프라인

### (1) 정산 도메인의 특수성 - 1원의 무결성
- `Settlement.of(...)` 를 열어 설명: **수수료만 반올림(HALF_UP)** 하고 **지급액은 `주문금액 - 수수료`(나머지)** 로 계산.
- 만약 지급액도 따로 반올림하면? → `feeAmount + payoutAmount != orderAmount` → `verifyIntegrity()` 에서 즉시 실패.
- 토크 포인트: "정산은 1원이 틀리면 사고다. 어디서 반올림하고 어디서 나머지를 취하느냐가 설계다."

### (2) 배치가 필요한 이유 - 직접 보여주기 (Step1 핵심 데모)

목표: 수강생이 **메모리가 차오르다 터지는 과정**과 **무엇이 느린지**를 눈으로 보게 한다.

#### 준비 — main() 을 Run/Profile 로, 힙을 좁혀서
IDE Run Configuration → VM options:
```
-Xmx1g -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./oom.hprof -Xlog:gc*:file=gc.log:tags,uptime
```
Program arguments(또는 application.properties):
```
--settlement.seed.enabled=true --settlement.seed.count=1000000 --spring.jpa.show-sql=false --logging.level.org.hibernate.orm.jdbc.bind=off
```
> `bootRun` 은 VM 옵션이 앱 JVM 에 안 먹을 수 있으니 **main() 직접 실행**을 권장.
> 부팅 로그의 `[SEED] 완료 ... 소요시간=...ms` → 첫 번째 "오래 걸리는 작업"(대량 적재) 확인.

#### 데모 A — "절벽으로 걸어가기" (느려지는 추세 + 메모리 증가)
```http
GET  http://localhost:8080/settlements/status                 # 시작 힙/건수
POST http://localhost:8080/settlements/naive?limit=100000     # 10만
DELETE http://localhost:8080/settlements
POST http://localhost:8080/settlements/naive?limit=300000     # 30만
DELETE http://localhost:8080/settlements
POST http://localhost:8080/settlements/naive?limit=500000     # 50만
```
- 응답의 `elapsedMs` 와 `peakHeapMb` 가 limit 에 비례해 **쭉 증가**하는 걸 표로 적어가며 보여준다.
- 콘솔의 `[mem:naive-climb] used=…MB / max=1024MB (NN%) [######----]` 막대가 호출마다 더 차오른다.
- 토크 포인트: "데이터가 늘면 시간도 메모리도 **선형으로** 증가한다. 이대로면 끝은 절벽이다."

#### 데모 B — "한 줄의 함정" (OOM)
```http
DELETE http://localhost:8080/settlements
POST   http://localhost:8080/settlements/naive                # limit 없음 = findAll 전량
```
- `[mem:naive-findAll]` 막대가 `max` 에 붙는 순간 `OutOfMemoryError` → `./oom.hprof` 생성.
- 토크 포인트: "이 **한 줄**(`findAll()`)이 100만 엔티티를 통째로 올린다. 100만을 다 못 읽고 죽는다."

#### 데모 C — 힙 덤프 분석 (왜/무엇이 터졌나)
`oom.hprof` 를 IntelliJ Ultimate 로 열고:
- **Count 정렬**: `Order`, `Money`, `EntityEntryImpl` 가 수십만~수백만 개
  - `EntityEntryImpl 수 ≈ Order 수` → 엔티티마다 영속성 컨텍스트 관리비용이 1:1로 붙음
  - `Money 수 ≈ Order×3×2` → 변경감지 **스냅샷 때문에 2배**로 든다
- **Retained 정렬 / Biggest Objects**: `StatefulPersistenceContext` 1개가 수백MB를 붙들어 GC 불가
- **Summary 탭**: GC 후 남은 live 총량(=줄일 수 없는 알맹이) 확인
- `gc.log`: Full GC 가 반복되는데 회수율 ≈ 0 → `GC overhead limit exceeded` 의 정체
- 주의: 인메모리 H2 라서 **시드 데이터(약 330MB)가 힙에 상주** → findAll 에 주어진 공간은 1GB 가 아니라 ~670MB. (더 깔끔히 분리하려면 아래 파일 H2 옵션)

#### (선택) 파일 기반 H2 로 "findAll 단독 범인" 부각
```
--spring.datasource.url=jdbc:h2:file:./build/oomdemo;DB_CLOSE_DELAY=-1
```
DB 데이터가 디스크로 빠져 힙에서 사라지므로, 힙 덤프에 **Hibernate 적재분만** 도드라진다.

#### 마무리 → 다음(Chunk)으로 연결
- "끊어서(Chunk) 1,000건만 올리고 → 쓰고 → `clear()` 로 비우면, `StatefulPersistenceContext` 가 절대 안 커진다."
- 그래서 (3) Spring Batch Chunk 지향 처리로 넘어간다.

### (3) 스프링 배치 기초 - Chunk 지향 처리  ✅ Step2 구현됨
`settlement/batch/` 패키지로 구현했다. (`lectures/settlement/step2-done`)

```
Reader(주문 페이지로 읽기) → Processor(주문→정산 변환) → Writer(정산 적재)
     └──────────── chunk(기본 1000)만큼 모아 한 트랜잭션 ────────────┘
```

| 구성 | 파일 | 설명 |
|---|---|---|
| **ItemReader** | `batch/SettlementJobConfig#settlementOrderReader` | `JpaPagingItemReader<Order>` — PAID 주문을 id 순 페이지로 읽음. pageSize=chunkSize → 페이지마다 영속성 컨텍스트를 비워 **메모리 안전** |
| **ItemProcessor** | `batch/OrderToSettlementProcessor` | `Order → Settlement` 변환. 1원 무결성은 `Settlement.of` 에 위임. 대상 아님은 `null` 반환 → 필터링 |
| **ItemWriter** | `batch/SettlementJobConfig#settlementWriter` | `JpaItemWriter<Settlement>` — chunk 단위 적재 (Bulk Insert 최적화는 오후) |
| **Step/Job** | `batch/SettlementJobConfig` | `StepBuilder.chunk(chunkSize, tx)` + `JobBuilder` |
| **실행기** | `application/SettlementBatchService` | `JobOperator.start(job, params)` (Batch6: JobLauncher deprecated). HeapMonitor 로 감싸 피크 힙 측정 |
| **엔드포인트** | `POST /settlements/batch` | `SettleReport` 반환(read/written/elapsed/peakHeap) |

#### 데모 동선 (naive 와 대비)
```http
DELETE http://localhost:8080/settlements
POST   http://localhost:8080/settlements/batch
```
- 콘솔 `[mem:batch]` 막대가 **거의 평평**하게 유지된다 (chunk 크기만큼만 사용). naive 의 우상향과 정반대.
- 100만 + `-Xmx1g` 에서: **naive 는 OOM, batch 는 완주.** 이게 Step1→Step2 의 결론.
- chunk 크기 조절: 기동 시 `--settlement.batch.chunk-size=5000`
  - 작게 = 트랜잭션 잦음/메모리 적음, 크게 = 빠르지만 메모리 많이 → 트레이드오프 관찰.

> 참고: Step2 시점(멱등 스킵 없음)에는 같은 데이터로 batch 를 두 번 돌리면 `order_id` 유니크
> 위반으로 **Job FAILED** 였다. **Step3-done 에서는 Processor 가 `existsByOrderId` 로 스킵**하므로
> 두 번 돌려도 안전(멱등)하다 — 두 번째는 `skippedCount=전체`. (자세한 건 아래 (4) Step3.)

- 실측(시드 5만, chunk 1000): read=50000, written=50000, peakHeap≈142MB, ~1.7s, status COMPLETED.

### (4) 멱등성과 재시작 (Restartability)  ✅ Step3 구현됨
`lectures/settlement/step3-done`. **두 가지 다른 무기**를 나란히 보여주는 게 핵심이다.

| 구분 | 무엇이 막아주나 | 키 | 재실행 시 |
|---|---|---|---|
| **멱등성**(application) | `existsByOrderId` 스킵 + `order_id` 유니크 | **새** JobParameters(timestamp) | 전체를 다시 읽되 이미 정산된 건 **필터링** |
| **재시작**(framework) | Spring Batch ExecutionContext 체크포인트 | **같은** JobParameters(runId) | 마지막 커밋 지점부터 **이어서 읽기** |

| 구성 | 파일 | 설명 |
|---|---|---|
| 장애 스위치 | `batch/SettlementFaultBox` | 50% 실패를 위한 런타임 토글. **JobParameters 가 아닌 싱글톤**에 둬야 재시작 때 같은 파라미터로도 장애를 끌 수 있다 |
| 장애 예외 | `batch/SettlementFaultException` | 실습용 폭탄(진짜 버그 아님)을 타입으로 표시 |
| 멱등 Processor | `batch/OrderToSettlementProcessor` | `@StepScope` + `existsByOrderId` 스킵(=필터링) + 카운터 기반 장애 주입. 카운터는 Step 실행마다 0 으로 초기화 |
| 실행기 | `application/SettlementBatchService` | `run()`(정상/복구), `runFailing(ratio)`(장애), `runRestartable(runId, ratio)`(네이티브 재시작) |
| 엔드포인트 | `POST /settlements/batch?failAt=0.5`, `POST /settlements/batch/restart?runId=1` | Step3-1 / Step3-2 트리거 |
| 리포트 | `application/dto/SettleReport` | `skippedCount`(멱등 스킵 수), `status`(COMPLETED/FAILED) 추가 |
| 통합 테스트 | `test/.../settlement/batch/SettlementRestartIntegrationTest` | 50% 실패 → 재실행/재시작 → 총 100건·중복 0 을 검증 |

#### 데모 동선 (chunk 를 잘게: `--settlement.batch.chunk-size=100`, 시드 2000)
```http
### [Step3-1 + 멱등성] 새 인스턴스로 복구
DELETE http://localhost:8080/settlements
POST   http://localhost:8080/settlements/batch?failAt=0.5   # status=FAILED, 약 50% 커밋
GET    http://localhost:8080/settlements/status             # settlementCount ≈ 50%
POST   http://localhost:8080/settlements/batch              # COMPLETED, skipped≈50% + settled≈50%
POST   http://localhost:8080/settlements/batch              # 멱등: settled=0, skipped=전체

### [Step3-2] 네이티브 재시작 (같은 runId 로 이어서)
DELETE http://localhost:8080/settlements
POST   http://localhost:8080/settlements/batch/restart?runId=1&failAt=0.5  # FAILED
POST   http://localhost:8080/settlements/batch/restart?runId=1            # 이어서 COMPLETED
```
- 토크 포인트
  - "50% 실패해도 **이미 정산된 50%는 사고가 아니다** — chunk 단위 트랜잭션으로 커밋됐기 때문."
  - **멱등성**: 새로 실행해도 `existsByOrderId` 가 걸러 한 주문은 한 번만. → `skippedCount` 로 눈에 보인다.
  - **재시작**: 같은 `runId`(=같은 JobParameters)면 새 인스턴스가 아니라 **그 인스턴스를 이어서**. Reader 체크포인트 덕에 앞부분은 **재독조차 안 한다**.
  - 왜 장애 스위치를 파라미터 밖에 뒀나? 재시작은 파라미터가 같아야 하는데, 장애 조건이 파라미터면 재시작 때 또 터진다. 그래서 `SettlementFaultBox`(런타임 토글).
  - naive 방식은 재실행하면 유니크 제약으로 그냥 터진다 → 대비 효과.
  - 검증: `SELECT COUNT(*), COUNT(DISTINCT order_id) FROM settlements;` 두 값이 같아야 무결.
- 생산 환경 노트: `existsByOrderId` 스킵은 매 행 조회라 단순하지만, 대용량이면 Reader 쿼리에서 미정산만 거르거나(`NOT EXISTS`) 주문 상태 플래그로 바꾸는 게 더 싸다. 여기선 개념 전달 우선.

---

## 2. [14:00~18:00] 라이브 세션 - 성능 한계 돌파

### (1) Step4 - 병렬 처리의 한계와 함정  ✅ Step4 구현됨 (`lectures/settlement/step4-done`)

> 하나의 서사: **스레드만 늘렸다가 풀을 터뜨리고(4-1) → 고쳤는데도 안 빨라지는 걸 측정하고(4-2)
> → 빨라지려니 재시작까지 잃는 걸 보고(4-3) → 둘 다 잡는 파티셔닝으로 수렴한다(4-4).**

| 구성 | 파일 | 설명 |
|---|---|---|
| 범위 분할 마스터 | `batch/OrderRangePartitioner` | PAID 주문의 `id` 범위를 `gridSize` 개로 균등 분할 → 각 파티션에 `minId/maxId` 전달 (4-4) |
| 워커 구성 | `batch/SettlementParallelJobConfig` | `@StepScope` 워커 Reader(자기 범위만, **전용 입구**) + 워커 Step. 파티션마다 Reader **독립** |
| Job 조립기 | `batch/SettlementParallelJobFactory` | 스레드 수/gridSize 를 런타임에 받아 멀티스레드 Job·파티셔닝 Job 을 동적으로 생성 |
| 실행기 | `application/SettlementBatchService` | `runMultiThreaded(threads)`, `runMultiThreadedRestartable(runId, failAt)`, `runPartitioned(gridSize)` |
| 엔드포인트 | `POST /settlements/batch/multi-threaded?threads=N`, `.../multi-threaded/restart?runId=1&failAt=0.5`, `.../partitioned?gridSize=N` | 4-1·4-2 / 4-3 / 4-4 |
| 풀·병렬도 설정 | `application.properties` | `spring.datasource.hikari.*`, `settlement.batch.thread-count/grid-size` |

#### ⚠️ 강사 필독 — Spring Batch 6 의 멀티스레드 (이 코드가 왜 이렇게 생겼나)

선행학습 자료가 말한 그대로다 — "옛날의 '중복/누락' 은 옛날 버전 얘기, 요즘 표준 Reader 는 안전하다."
이 프로젝트(Spring Boot 4.1 / **Batch 6.0.4**)에서 확인한 사실 두 가지:

1. **표준 `JpaPagingItemReader.read()` 가 내부 `Lock` 으로 보호된다.** → 멀티스레드여도 데이터가 안 꼬인다.
   그 **안전함의 비결이 곧 "읽기를 한 줄로 세우는 것"** — 이게 4-2 의 깔때기(병목)다.
2. **신형 `chunk(size)`(`ChunkOrientedStep`) 에 `taskExecutor` 를 주면 "읽기는 단일 스레드, 가공만 병렬"** 이다
   (= 4-4 끝의 'Async 카드'). 그래서 "여러 스레드가 한 Step 을 통째로 도는" 전통적 Multi-threaded Step 을
   보여주려고, 이 코드는 레거시 빌더 `chunk(size, txManager)` + `taskExecutor(TaskExecutor)` 를 의도적으로 쓴다.
   (이 레거시 빌더는 Batch 6 에서 `@Deprecated(forRemoval)`.)

> 멀티스레드 데모 기동(처리량 측정은 데이터가 좀 있어야 보인다):
> ```bash
> ./gradlew bootRun --args='--settlement.seed.enabled=true --settlement.seed.count=20000 \
>   --settlement.batch.chunk-size=200 --spring.jpa.show-sql=false --logging.level.org.hibernate.orm.jdbc.bind=off'
> ```

#### [Step4-1] 스레드만 늘리면 터진다 — 커넥션 풀

풀을 일부러 작게 걸고(`--spring.datasource.hikari.maximum-pool-size=2`) 스레드를 많이 준다.
```http
POST /settlements/batch/multi-threaded?threads=10
```
- 결과: `status=FAILED`, 로그에 **`HikariPool-1 - Connection is not available, request timed out after 3000ms`**.
- 처방: `maximum-pool-size` 를 스레드 수에 맞게 키워 재기동 → 같은 호출이 **COMPLETED**.
- 토크 포인트: 병렬화는 스레드 수만의 문제가 아니라 **공유 자원(커넥션·DB)** 과의 균형. 무한정 키우면 이번엔 DB 가 못 버틴다.

#### [Step4-2] 분명 고쳤는데 왜 안 빨라지죠? — 읽기 깔때기 (직접 측정)

풀을 충분히 키운 뒤, 스레드를 **1 → 2 → 4 → 8** 로 올려가며 응답의 `elapsedMs` 를 표로 적는다.
```http
POST /settlements/batch/multi-threaded?threads=1
POST /settlements/batch/multi-threaded?threads=2
POST /settlements/batch/multi-threaded?threads=4
POST /settlements/batch/multi-threaded?threads=8
```
- 실측(시드 2만, chunk 200) 예시 — **2배씩 늘려도 시간이 절반으로 안 준다:**

  | threads | 1 | 2 | 4 | 8 |
  |---|---|---|---|---|
  | elapsedMs | ~1240 | ~660 | ~500 | ~455 |
  | 배속 | 1.0× | 1.9× | 2.5× | **2.7×** (8배 아님) |

- 왜? **일꾼은 여럿이지만 데이터를 읽는 Reader 는 딱 하나(깔때기).** Batch 6 표준 Reader 는 안전을 위해
  읽기를 한 줄로 세우므로, 뒤에 일꾼이 아무리 많아도 입구가 한 줄이라 전체 속도가 거기 묶인다.
  (계산대 10개 열어도 입구가 회전문 하나면 소용없다.)
- 데이터는 안 꼬인다(`read=settled=전체, 중복 0`). 위험이 사라진 대신 **속도의 천장**으로 모습만 바꾼 것.

#### [Step4-3] 게다가 정산에선 — 재시작까지 잃는다

멀티스레드 Step 은 여러 스레드가 한 Reader 를 호출하므로, "어디까지 읽었는지" 를 신뢰성 있게 기록할 수 없어
**`saveState(false)` 가 사실상 강제**된다(코드: `SettlementParallelJobFactory#multiThreadedReader`). 체크포인트 자체가 없다.
```http
POST /settlements/batch/multi-threaded/restart?runId=5&failAt=0.5   # 50%에서 FAILED
POST /settlements/batch/multi-threaded/restart?runId=5              # 재개
```
- 결과: 재개 호출의 **`readCount` 가 전체에 가깝고 `skippedCount` 가 1차 커밋분**이다 → 즉 **"이어서"가 아니라
  처음부터 다시 읽었다**는 증거. (1부 Step3 의 "마지막 커밋부터 이어서" 가 사라졌다.)
- 멱등성(existsByOrderId + UNIQUE)이 데이터는 지켜주니 최종 건수는 정확하다. 하지만 **멱등성이 없었다면
  재시작이 곧 이중정산 사고**였을 것. 정산에서 "속도 얻자고 재시작·무결성을 내준다" 는 답이 아니다.
- → 그래서 우리가 찾는 건 "속도냐 안전이냐" 가 아니라 **둘 다 가져가는 길**이다.

#### [Step4-4] 정답 — 데이터를 아예 나눠준다 (Partitioning)

문제는 "Reader 하나로 데이터가 한 줄(깔때기)로만 들어온 것" 이었다. 그럼 **입구를 여러 개**로 만들면 된다.
마스터(`OrderRangePartitioner`)가 `id` 범위를 `gridSize` 등분해 **워커마다 전용 `@StepScope` Reader(전용 입구)** 를 준다.
```http
POST /settlements/batch/partitioned?gridSize=1
POST /settlements/batch/partitioned?gridSize=2
POST /settlements/batch/partitioned?gridSize=4
POST /settlements/batch/partitioned?gridSize=8
```
- **두 통증을 한 방에:**
  - **속도 천장 돌파** — 입구가 여러 개라 깔때기가 사라진다. 실측(시드 2만): `~1070 → 524 → 260 → 201ms`
    = gridSize 8 에서 **5.3×**(멀티스레드 2.7× 와 대비). 같은 병렬도라도 파티셔닝이 더 빠르고 더 잘 확장된다.
  - **재시작 보존** — 워커마다 `saveState=true` 전용 Reader + **파티션별 독립 StepExecution** 이라 체크포인트가
    파티션 단위로 남는다(4-3 에서 잃은 것). 멱등 재실행도 안전: 다시 돌리면 `settled=0, skipped=전체`.
  - **무결성 유지** — 각 워커가 멱등 스킵 + UNIQUE 최후방어 그대로 → **빨라지면서도 "1원의 무결성" 1도 안 깨짐.**
- `gridSize` 주의: 워커 수 > 커넥션 풀이면 4-1 처럼 풀이 터진다(파티셔닝도 예외 아님). 또 어느 지점부터는
  DB·IO·코어가 진짜 병목이라 더 안 빨라진다("왜 8→16 인데 그대로?").

|  | Multi-threaded Step (4-2·4-3) | Partitioning (4-4) |
|---|---|---|
| Reader | **하나를 공유**(깔때기) | 파티션마다 **전용**(여러 입구) |
| 처리량 | 어느 선에서 천장(2.7×) | 잘 확장(5.3×) |
| 재시작 | `saveState=false` → 처음부터 | 파티션별 체크포인트 → 보존 |
| 한 줄 비유 | 입구 회전문 하나 | 입구를 여러 개로 |

#### 별도 카드: Async (개념만, 구현 생략)
`AsyncItemProcessor`/`AsyncItemWriter` 는 **읽기는 빠른데 가공(Processor)이 무거울 때**(복잡한 계산·외부 호출) 꺼내는 카드다.
파티셔닝이 **데이터(입구)를 나누는** 거라면 Async 는 **가공만 병렬로 돌리는** 거라 결이 다르다.
재미있는 사실: **Batch 6 의 신형 `chunk(size).taskExecutor(...)` 가 사실상 이 동작**(읽기 단일·가공 병렬)이다.
오늘은 "이런 게 있다" 만 알고 넘어간다.

### (2) 대용량 조인 최적화 & DB 부하 줄이기
- 주문-결제(-정산) 조인 시 성능 저하 → **Driving Table** 선택, 인덱스 활용.
- **Cursor vs Paging** 선택 기준:
  - Paging: 매 페이지 `OFFSET` 비용 / 단순.
  - Cursor: 커넥션 유지하며 스트리밍 / 멀티스레드와는 상성 주의.
- **Bulk Insert**: `JdbcBatchItemWriter` + `rewriteBatchedStatements`(MySQL) 류로 INSERT 묶기.
- **인덱스 전략**: 조인/조회 키(`payment_id`, `order_id`)에 인덱스, 정산 테이블 유니크 인덱스.

### (3) 요약 & Q&A

---

## 부록 - 자주 쓰는 명령

```bash
# 일반 기동 (시드 없음)
./gradlew bootRun

# 소량 시드로 빠르게 흐름만 확인
./gradlew bootRun --args='--settlement.seed.enabled=true --settlement.seed.count=2000 --spring.jpa.show-sql=false'

# 정산 결과 초기화 (멱등성/재시작 데모 반복용)
curl -X DELETE http://localhost:8080/settlements

# H2 콘솔: http://localhost:8080/h2-console  (JDBC URL: jdbc:h2:mem:tangled)
```

> 참고: 대량 시드/배치 실행 시에는 `--spring.jpa.show-sql=false --logging.level.org.hibernate.orm.jdbc.bind=off`
> 로 로그 폭주를 막으세요.
