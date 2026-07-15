# 협업 규칙 (초안 — 팀 회의에서 확정할 것)

> 아래는 제안 초안이다. 팀 회의에서 확정 후 이 문구를 지우자.

## 브랜치 전략

- `main` — 항상 빌드/기동 가능한 상태 유지. **직접 push 금지, PR로만 병합.**
- 작업 브랜치: `feat/<도메인>-<설명>`, `fix/<도메인>-<설명>`, `chore/<설명>`
  - 예: `feat/project-approve-reason`, `fix/order-stock-rollback`

## PR 규칙

1. 이슈를 먼저 만들고 브랜치를 판다 (작은 수정은 생략 가능).
2. PR 템플릿을 채운다 — 특히 **어떻게 테스트했는지**.
3. CI(`./gradlew build`)가 초록불이어야 병합 가능.
4. 리뷰어 1명 이상 승인 후 병합. 자기 PR은 자기가 병합.
5. 병합 방식: Squash and merge (커밋 히스토리를 깔끔하게).

## 커밋 메시지

`타입: 요약` 형식 (한글 OK):

```
feat: 프로젝트 심사 반려 사유 저장
fix: 주문 취소 시 리워드 재고 복구 누락 수정
docs: README 기동 순서 보완
test: 정산 배치 파티셔너 테스트 추가
refactor / chore
```

## 코드 컨벤션

- 아키텍처: README의 레이어 구조와 "도메인 간 ID 참조" 원칙을 지킨다.
- 다른 도메인 호출이 필요하면 `application/port/` 인터페이스 + `infrastructure/client/` 어댑터를 추가한다 — 엔티티 공유 금지.
- 설정 변경은 이 저장소가 아니라 [beadv7_7_earlybird_config](https://github.com/prgrms-be-adv-devcourse/beadv7_7_earlybird_config)에서.
- `TODO(팀):` 마커는 담당 도메인 팀원이 채운다.

---

## GitHub 저장소 설정 체크리스트 (관리자 권한 필요 — PO 확인)

Settings → Branches → Add branch protection rule (`main`):

- [ ] Require a pull request before merging (승인 1개 이상)
- [ ] Require status checks to pass — `build` (CI 워크플로 첫 실행 후 목록에 뜸)
- [ ] Do not allow bypassing the above settings
