# 인프라 구축 방법 (EC2 · k3s · CI/CD)

배포 인프라 구성과, 새 서비스 추가·설정 변경 시 건드릴 파일을 정리한 문서. EC2 자체
프로비저닝(VPC/보안그룹)은 다루지 않는다 — 이미 떠 있는 두 대 위에서의 배포 구조가 대상.

## 1. 토폴로지

| | 앱 박스 | 데이터 박스 |
| --- | --- | --- |
| IP | `54.180.14.152` | `52.78.6.56` |
| k3s 역할 | agent 노드 | control-plane |
| 뜨는 것 | 13개 서비스 파드(nodeSelector로 고정) + docker compose kafka/caddy/fe | MySQL, Elasticsearch (compose, k8s 아님) |

배경: k3s 전환 [#198](https://github.com/prgrms-be-adv-devcourse/beadv7_7_earlybird_BE/issues/198), 매니페스트 git 관리 [#291](https://github.com/prgrms-be-adv-devcourse/beadv7_7_earlybird_BE/issues/291). 앱 박스는 Caddy가 80/443을 쓰고 있어 control-plane은 데이터 박스에 뒀고, 13개 파드는 전부 앱 박스에 고정한다.

`kubectl`은 항상 데이터 박스(control-plane)에서 실행한다. CD의 `deploy-k8s` 잡도 마찬가지.

## 2. Compose 파일 3종

- [`infrastructure/docker-compose.yml`](../infrastructure/docker-compose.yml) — base, 로컬 개발용. 마이그레이션 기간 내내 그대로 유지.
- [`infrastructure/docker-compose.prod.yml`](../infrastructure/docker-compose.prod.yml) — AWS 오버라이드, base 위에 얹어서만 사용. k8s로 옮긴 서비스는 `profiles: ["local-only"]`로 배포용 `up -d`에서 제외 — 지금 남은 건 kafka/caddy/fe뿐.
- [`infrastructure/docker-compose.data.yml`](../infrastructure/docker-compose.data.yml) — 데이터 박스 전용, MySQL/Elasticsearch.

새 서비스를 k8s로 배포할 계획이면 `docker-compose.prod.yml`에도 같은 방식으로 제외 처리한다.

## 3. 새 서비스를 k8s에 추가하기

[`infrastructure/k8s/`](../infrastructure/k8s/)의 기존 매니페스트를 복사해서 시작한다.

- **단일 인스턴스 + hostPort** (대부분의 서비스): [`order-service.yaml`](../infrastructure/k8s/order-service.yaml) 참고 — `nodeSelector`로 앱 박스 고정, `strategy: Recreate`(hostPort 충돌 방지), `hostPort`는 compose 시절 포트 그대로.
- **HPA 자동 스케일링** (명세서 필수 구현 기술 ② 대응): [`chat-service.yaml`](../infrastructure/k8s/chat-service.yaml) 참고 — hostPort/`Recreate` 없이 `replicas: 2` + 같은 파일에 `HorizontalPodAutoscaler` 리소스 추가.

리소스 requests/limits는 실측 기반으로 정한다 (두 EC2 다 메모리 여유 부족) — `sudo crictl stats` 실측 예시는 [#340](https://github.com/prgrms-be-adv-devcourse/beadv7_7_earlybird_BE/issues/340).

체크리스트:
1. `infrastructure/k8s/<service>.yaml` 작성
2. DB 쓰면 `db-credentials` Secret 참조 (9개 서비스 공용, [#164](https://github.com/prgrms-be-adv-devcourse/beadv7_7_earlybird_BE/issues/164))
3. 새 종류 시크릿이 필요하면 §4
4. [`cd-deploy.yml`](../.github/workflows/cd-deploy.yml)의 `ALL_SERVICES`에 이름 추가 — 빠뜨리면 diff 기반 선택 배포([#385](https://github.com/prgrms-be-adv-devcourse/beadv7_7_earlybird_BE/issues/385))에서 영구 제외됨
5. `docker-compose.prod.yml`에 `profiles: ["local-only"]` 추가
6. 최초 1회 데이터 박스에서 `kubectl apply -f infrastructure/k8s/<service>.yaml` (이후는 CD가 관리)

## 4. 시크릿

**값은 절대 커밋하지 않는다.** GitHub Actions Secrets에만 두고, [`cd-deploy.yml`](../.github/workflows/cd-deploy.yml)의 `deploy-k8s` 잡이 매 배포마다 `kubectl create secret --dry-run=client -o yaml | kubectl apply -f -`로 재적용한다.

새 시크릿 추가:
1. `gh secret set <NAME> --repo prgrms-be-adv-devcourse/beadv7_7_earlybird_BE`
2. `cd-deploy.yml`의 `env:`/`envs:`에 추가
3. 같은 스텝에 `kubectl create secret` 한 줄 추가
4. 매니페스트에서 `secretKeyRef`로 참조

전체 목록과 값 근거는 `cd-deploy.yml`의 `deploy-k8s` 스텝 주석과 [`infrastructure/k8s/README.md`](../infrastructure/k8s/README.md) 참고.

## 5. 배포 파이프라인

1. [`ci.yml`](../.github/workflows/ci.yml) — push/PR마다 `./gradlew build`
2. [`cd-build.yml`](../.github/workflows/cd-build.yml) — 13개 이미지를 커밋 SHA로 태그해 GHCR push
3. [`cd-deploy.yml`](../.github/workflows/cd-deploy.yml) `deploy-k8s` — 데이터 박스에서 시크릿 재적용 → diff로 바뀐 서비스만 순차 배포 (동시 재시작 시 CPU 포화로 타임아웃: [#385](https://github.com/prgrms-be-adv-devcourse/beadv7_7_earlybird_BE/issues/385)/[#478](https://github.com/prgrms-be-adv-devcourse/beadv7_7_earlybird_BE/issues/478)/[#479](https://github.com/prgrms-be-adv-devcourse/beadv7_7_earlybird_BE/issues/479)/[#482](https://github.com/prgrms-be-adv-devcourse/beadv7_7_earlybird_BE/issues/482))
4. `cd-deploy.yml` `deploy` — 앱 박스에서 kafka/caddy/fe만 pull + up -d, Caddy는 bind-mount라 `docker restart`로 재적용

## 6. 자주 쓰는 명령 (데이터 박스)

```bash
kubectl -n webapp get pods
kubectl -n webapp logs deployment/<service> --tail=100
kubectl -n webapp rollout status deployment/<service>
kubectl -n webapp rollout undo deployment/<service>
kubectl -n webapp scale deployment/<service> --replicas=0   # 매니페스트는 유지한 채 내리기
```

전체 롤백 절차는 [`infrastructure/k8s/README.md`](../infrastructure/k8s/README.md) 참고.

## 관련 문서

- [`infrastructure/k8s/README.md`](../infrastructure/k8s/README.md) — 매니페스트 적용/롤백
- [`1_LOCAL_DB_SETUP.md`](1_LOCAL_DB_SETUP.md) — 로컬 MySQL
- [`2_CONFIG_SERVER_SETUP.md`](2_CONFIG_SERVER_SETUP.md) — config-server PAT
- [`3_JWT_AUTH.md`](3_JWT_AUTH.md) — JWT 발급·검증
- [`4_GHCR_AUTH.md`](4_GHCR_AUTH.md) — GHCR pull 인증
