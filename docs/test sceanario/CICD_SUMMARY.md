# CI/CD 소개 — 발표용 요약

## 요약

- 테스트를 통과하지 못한 코드는 배포될 수 없도록, CD를 CI 결과에 종속시켜 놓았다
- `main` = 배포 가능한 운영 코드, `develop` = 통합/테스트 서버 — 개인 브랜치 → `develop` → `main` 순으로 승격

## 하는 일

| 단계 | 트리거 | 하는 일 | 코드 |
| --- | --- | --- | --- |
| CI | `main`/`develop`에 push 또는 그 브랜치로의 PR | `./gradlew build --parallel` (order/settlement 등 DB 의존 테스트는 Testcontainers로 실제 MySQL을 띄워 검증) | [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) |
| CD | CI 워크플로우의 완료 이벤트(`workflow_run`), 또는 수동 실행(`workflow_dispatch`) | `main`의 CI가 **성공**했을 때만 EC2에 SSH 접속해 `git pull` 후 서비스별 순차 재빌드 | [`.github/workflows/cd.yml`](../../.github/workflows/cd.yml) |

## 흐름

```
개인 브랜치(팀원명/도메인/기능) → PR → develop 로 통합 (CI 실행)
→ develop 이 충분히 안정되면 main 으로 승격 (다시 CI 실행)
→ main 에 대한 CI 가 성공(conclusion == 'success')하면
→ CD 가 workflow_run 이벤트를 받아 EC2 배포 실행
→ config-server, discovery-server, gateway-server, caddy, 각 business 서비스 순으로 순차 재빌드
```

CD의 게이팅 조건: [`cd.yml#L14`](../../.github/workflows/cd.yml#L14) — `workflow_run.head_branch == 'main' && workflow_run.conclusion == 'success'`. 순차 재빌드 대상과 순서: [`cd.yml#L37`](../../.github/workflows/cd.yml#L37).

## 설계 결정과 트레이드오프

- **CD 트리거: push 이벤트로 독립 실행 vs CI 완료 이벤트에 게이팅** — 원래는 CD가 push 이벤트로 CI와 독립적으로 실행돼, **테스트가 깨진 커밋도 배포될 수 있는** 문제가 있었다 (#111). `workflow_run` 구독 + `head_branch`/`conclusion` 체크로 게이팅해 이 문제를 고쳤다. 대신 `workflow_run`은 "어떤 워크플로우든 완료됐다"는 이벤트라서, 원하는 조건(main 브랜치의 성공)을 코드로 직접 걸어야 했고, `workflow_dispatch`(수동 실행)는 이 게이트를 완전히 우회하므로 그 경로는 별도로 주의해야 한다.
- **배포 방식: 전체 서비스 동시 빌드 vs 순차 빌드** — `docker compose up -d --build`로 11개 이미지를 한 번에 빌드하면, EC2가 2vCPU 인스턴스라 CPU가 감당하지 못해 **`sshd`까지 응답 불가능해지는 실제 장애**를 겪었다. 그 후 서비스 하나씩 순차로 빌드하도록 바꿨다 ([`cd.yml`](../../.github/workflows/cd.yml#L37) 주석 참고). 전체 배포 시간은 늘어나지만, 박스가 먹통이 되지 않고 각 빌드가 확실히 끝난다.
- **프로덕션 외부 진입점: gateway-server 직접 노출 vs Caddy 리버스 프록시 추가** — 로컬은 gateway-server가 8000 포트를 직접 열지만, 프로덕션은 [`docker-compose.prod.yml`](../../infrastructure/docker-compose.prod.yml)로 gateway-server의 호스트 포트를 닫고 Caddy가 80/443(TLS)을 받아 내부로 전달한다. "게이트웨이가 유일한 진입점"이라는 원칙은 같지만, TLS 종료를 Caddy가 전담해서 gateway-server 코드가 HTTPS를 신경 쓸 필요가 없어지는 대신, 로컬과 운영의 물리적인 진입 구조가 한 겹 달라져서 로컬에서 그대로 재현하긴 어렵다.

## 참고

- 브랜치 보호 규칙(ruleset)은 GitHub 저장소 설정(UI)에 있어 코드에는 보이지 않는다 — 발표에서 "필수 체크가 걸려 있다"고 말하려면 별도로 확인이 필요하다.
