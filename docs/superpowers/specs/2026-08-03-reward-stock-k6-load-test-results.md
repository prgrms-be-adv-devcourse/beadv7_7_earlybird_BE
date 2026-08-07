# Reward 재고 차감·복원 k6 부하테스트 결과

설계: [2026-07-31-reward-stock-k6-load-test-design.md](./2026-07-31-reward-stock-k6-load-test-design.md) — `decreaseStock`만 다룬다.
`restoreStock`(재고 복원)은 설계 문서 작성 당시엔 저빈도로 판단해 스코프에서 뺐지만, 정산(settlement-service)이
실패한 프로젝트를 일괄 환불할 때 같은 리워드에 여러 건의 `restoreStock`이 동시에 몰릴 수 있다는 점이 지적되어
같은 방식으로 추가 테스트했다.

스크립트: `project-service/k6/reward-stock-load-test.js`(차감) / `reward-restore-stock-load-test.js`(복원).
실행 환경: 로컬 project-service(`:8081`) + 로컬 `earlybird-mysql` 컨테이너. 배포 환경은 대상이 아니다.

## 실행 방법

```bash
cd project-service/k6
STOCK=300 VUS=100 DURATION=20s k6 run reward-stock-load-test.js
STOCK=300 VUS=100 DURATION=20s k6 run reward-restore-stock-load-test.js
```

## decreaseStock 결과 요약 (STOCK=300, VUS=100, DURATION=20s)

| 지표 | 값 |
| --- | --- |
| TPS (`http_reqs` rate) | 1243~1336 req/s (2회 실행, 오차 범위) |
| 지연시간 p90 / p95 | 약 126ms / 160ms |
| 성공(200) | 300 |
| 재고부족(409) | 24,750 ~ 26,542 |
| 락경합 재시도소진(409) | 46 ~ 54 |
| 예상 못한 응답 | 0 |

**정합성**: `teardown()`에서 조회한 `remainingQuantity`는 두 번 모두 `0`. `STOCK(300) - 0 = 300`이 성공 카운터(300)와 정확히 일치 — 100 VU 동시경합에서도 lost update 없이 낙관적 락(`@Version` + `@Retryable`)이 정상 동작함을 실측 트래픽으로 재확인했다.

## DB 레벨 지표

같은 실행(2회차)에서 `docker stats earlybird-mysql`을 병행 수집하고, `SHOW GLOBAL STATUS`를 테스트 시작 직전/직후로 떠서 델타를 냈다.

**컨테이너 리소스 (20초 구간)**
- CPU: 부하 시작 직후 15% → 90~110%로 포화(코어 1개 수준), 종료와 함께 하락
- 메모리: ~502MB로 안정, 변화 거의 없음
- Network I/O: 요청량에 비례해 꾸준히 증가, 특이사항 없음

**`SHOW GLOBAL STATUS` 델타**

| 지표 | 델타 |
| --- | --- |
| Com_commit | +304 |
| Com_rollback | +53,312 |
| Innodb_row_lock_waits | +166 |
| Innodb_row_lock_time (누적, ms) | +277 |
| Innodb_row_lock_time_max | 52ms (변화 없음) |
| Innodb_row_lock_current_waits | 0 (테스트 후 잔류 락 없음) |

**해석**: Commit 304는 성공 300건 + setup 단계(프로젝트 등록/승인/리워드 등록)와 대략 일치 — 성공한 트랜잭션만 커밋되고 실패는 전부 롤백되는 설계대로 동작한다. InnoDB row lock 대기는 166건, 최대 대기 52ms로 안정적이라 DB 레벨에서 락 때문에 응답이 지연되거나 데드락이 발생한 흔적은 없다. 이 수준의 부하(100 VU, 재고 300개 경합)에서 DB는 CPU가 한 코어 수준으로 바빠졌을 뿐, 락 대기·커넥션·메모리 어느 쪽도 뚜렷한 병목 신호는 없었다.

**주의**: 이 MySQL 컨테이너는 프로젝트 전체 9개 서비스 스키마를 한 인스턴스로 공유한다 (`docs/1_LOCAL_DB_SETUP.md` 참고). 테스트 시점에 project-service·discovery-server만 기동 중이었으므로 다른 서비스발 노이즈는 없었다고 보지만, 위 GLOBAL STATUS 수치는 project-service 스키마 전용이 아니라 인스턴스 전체 값이라는 점은 감안해야 한다.

## Com_rollback이 실패 건수의 약 2배로 나온 이유

실패 건수(재고부족 + 락경합 ≈ 26,588)보다 Com_rollback 델타(53,312)가 약 2배 많게 나와, `mysql.general_log`를 켜고 요청을 단건으로 재현해 실제 SQL 시퀀스를 확인했다.

**재고부족(409) 요청 1건 (`decreaseStock`, `@Transactional` readOnly=false)**
```
SET autocommit=0
SELECT ... FROM rewards WHERE reward_id=?
SELECT ... FROM projects WHERE project_id=? FOR SHARE
ROLLBACK                          -- ① 실제 비즈니스 트랜잭션 롤백
SET autocommit=1
SET SESSION TRANSACTION READ ONLY
SET autocommit=0
ROLLBACK                          -- ② 쿼리 없는 빈 롤백
SET autocommit=1
SET SESSION TRANSACTION READ WRITE
```

**비교: 순수 조회 `GET /api/v1/rewards/{id}` (`@Transactional(readOnly=true)`, 클래스 기본값 그대로)**
```
SET SESSION TRANSACTION READ ONLY
SET autocommit=0
SELECT ... FROM rewards WHERE reward_id=?
COMMIT
SET autocommit=1
SET SESSION TRANSACTION READ WRITE
```
→ 커밋 1번뿐, 배가 되지 않는다. 즉 이 배가 현상은 모든 HTTP 요청에 붙는 보편적인 것이 아니라 `decreaseStock` 경로에서만 나타난다.

**원인**: project-service 기동 로그에 `spring.jpa.open-in-view is enabled by default` 경고가 있다 (Spring Boot 기본값, 끄지 않음). `RewardServiceImpl`은 클래스 레벨이 `@Transactional(readOnly=true)`인데 `decreaseStock()`만 `@Transactional`(readOnly=false)로 오버라이드한다. 이 read-write 트랜잭션이 롤백된 직후, OSIV가 요청 전체에 걸쳐 붙들고 있는 Hibernate 세션의 커넥션 정리 과정에서 읽기전용 모드로 전환했다 되돌리는 **쿼리 없는 빈 트랜잭션**이 한 번 더 열렸다 닫히며, 그 빈 트랜잭션도 시작 시점에 커밋할 게 없어 ROLLBACK으로 끝난다. 결과적으로 실패 1건당 "진짜 롤백" 1번 + "OSIV 커넥션 정리용 빈 롤백" 1번, 총 2번이 찍힌다.

**수치 검증**: 재고부족 26,542건 × 2 ≈ 53,084. 나머지 228은 락경합 46건(재시도 최대 3회, 매 시도가 같은 이중 롤백 패턴)에서 나온 것으로 설명되며, 합산하면 실측치 53,312와 거의 일치한다.

**결론**: 애플리케이션이 트랜잭션을 실수로 두 번 여는 버그가 아니라, OSIV(`open-in-view=true`) + 클래스 기본 `readOnly` 값과 메서드별 오버라이드 조합에서 나오는 프레임워크 차원의 부수 효과다. 빈 트랜잭션이라 실제 행(row)을 건드리지 않고 락도 잡지 않으므로 데이터 정합성·성능에 실질적 영향은 없다. `open-in-view=false`로 끄면 없어질 가능성이 높지만, 이는 project-service 전역 설정 변경이라 이번 조사 범위 밖으로 두고 실제 수정은 하지 않았다 — 필요하면 별도 논의 후 진행.

## restoreStock (배치 환불) 결과 요약 (STOCK=300, VUS=100, DURATION=20s)

`setup()`에서 리워드를 만든 뒤 `decreaseStock(quantity=STOCK)` 한 번으로 "전량 판매 완료"(`remainingQuantity=0`)
상태를 만들고, 그 다음 100 VU가 동시에 `restoreStock(quantity=1)`을 반복 호출해 정산 배치 환불 상황을 재현했다.
`restoreStock`은 `decreaseStock`과 달리 프로젝트가 진행중인지 확인하지 않는다(환불은 프로젝트가 이미
실패/취소로 닫힌 뒤에도 일어나야 하므로) — 그래서 setup에 승인 절차는 넣었지만 프로젝트 상태 자체는
restoreStock 실행에 영향을 주지 않는다.

| 지표 | 값 |
| --- | --- |
| TPS | 1350 req/s |
| 지연시간 p90 / p95 | 119ms / 151ms |
| 성공(200) | 300 |
| 이미 꽉 참(409, 총수량 초과) | 26,810 |
| 락경합 재시도소진(409) | 31 |
| 예상 못한 응답 | 0 |

**정합성**: `teardown()`의 `remainingQuantity=300`이 `totalQuantity(300)`, 성공 카운터(300)와 정확히 일치 —
100 VU가 동시에 같은 리워드를 환불해도 lost update 없이 정상 복원됨을 확인했다.

`decreaseStock`(1243~1336 TPS, p95 ~160ms)과 성능 특성이 거의 동일하다 — 예상대로 같은
`@Version` + `@Retryable` 메커니즘이라 결과도 같은 패턴을 보인다.

**주의**: `restoreStock`에는 DB 리소스(`docker stats`/`SHOW GLOBAL STATUS`) 계측과 Com_rollback 원인 분석을
별도로 반복하지 않았다. 코드 경로가 `decreaseStock`과 동일한 `@Transactional`(readOnly=false 오버라이드) +
`@Retryable` 구조라 위 "Com_rollback이 2배로 나온 이유" 절의 원인이 그대로 적용될 가능성이 높지만, 이건
추론이고 실측으로 재확인하지는 않았다.

## 스코프 밖

- Project 마감 확정, Project 삭제 vs Reward 재고차감, Category 락 등 나머지 동시성 지점은 설계 문서와 동일하게 이번 결과에도 포함하지 않는다.
- `open-in-view=false` 전환 실험은 하지 않았다 (전역 설정 변경, 별도 논의 필요).
- `restoreStock`의 DB 레벨 지표(docker stats, InnoDB 락, Com_rollback 배수)는 실측하지 않았다 — 위 주의 참고.
