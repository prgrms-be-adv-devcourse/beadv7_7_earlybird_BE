# infrastructure/k8s

k3s 클러스터(데이터 박스 `52.78.6.56`가 컨트롤 플레인, 앱 박스 `54.180.14.152`는 agent 노드로 조인됨)에서 운영하는 서비스들의 git 관리 매니페스트. 이 마이그레이션의 배경은 [이슈 #198](https://github.com/prgrms-be-adv-devcourse/beadv7_7_earlybird_BE/issues/198), 이 디렉터리가 생긴 이유는 [이슈 #291](https://github.com/prgrms-be-adv-devcourse/beadv7_7_earlybird_BE/issues/291) 참고 (기존엔 데이터 박스에 수기로 올린 파일이라 git 추적이 안 되고 있었음).

## 매니페스트 적용

```bash
# 이 클러스터에 대해 kubectl 이 구성된 박스에서 실행 (현재는 데이터 박스)
kubectl apply -f infrastructure/k8s/
```

`kubectl apply`는 멱등이고 각 파일의 `metadata`로 네임스페이스/서비스가 스코프되므로 재실행해도 안전하다.

## 시크릿

**이 디렉터리에는 시크릿 값을 절대 커밋하지 않는다.** `kubectl create secret`으로 수동 부트스트랩하거나, (CD 연동 이후 — #198 Phase 6 참고) GitHub Actions 시크릿으로부터 배포 파이프라인이 매 배포마다 재적용하도록 한다.

현재는 cd.yml의 `deploy-k8s` 잡이 GitHub Actions 시크릿으로부터 매 배포마다 재적용한다 (아래는 그 예시 — 전체 목록은 `cd.yml` 참고):

```bash
# config-server — git-remote 모드는 GitHub PAT 필요
kubectl -n webapp create secret generic config-server-git \
  --from-literal=GIT_USERNAME=<github-id> \
  --from-literal=GIT_PERSONAL_ACCESS_TOKEN=<ghp_...>

# gateway-server — user-service의 JWT_SECRET과 반드시 동일한 값이어야 함
kubectl -n webapp create secret generic gateway-jwt \
  --from-literal=JWT_SECRET=<user-service의 JWT_SECRET과 동일한 값>

# 9개 DB-backed 서비스 공용 (#164) — 데이터 박스 mysql 컨테이너의 MYSQL_USER/MYSQL_PASSWORD
# (docker-compose.data.yml, 그 박스의 .env로만 관리)와 반드시 동일한 값이어야 함
kubectl -n webapp create secret generic db-credentials \
  --from-literal=DB_USERNAME=<계정> \
  --from-literal=DB_PASSWORD=<비밀번호>
```

## 롤백

**이미 k8s로 옮겨간 Deployment의 이미지가 잘못됐을 때:**
```bash
kubectl -n webapp rollout undo deployment/<name>              # 이전 리비전으로
kubectl -n webapp rollout history deployment/<name>            # 리비전 목록 + change-cause 확인
kubectl -n webapp rollout undo deployment/<name> --to-revision=<N>
```

**마이그레이션 단계 자체를 되돌려야 할 때** (예: 특정 서비스를 docker-compose로 되돌리기):
1. 해당 단계에서 `docker-compose.prod.yml`을 `profiles: ["local-only"]`로 바꾼 커밋, 그리고 같은 단계의 Caddyfile/config repo 변경을 `git revert`한다.
2. CD를 재실행하거나, 앱 박스에서 수동으로: `docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.prod.yml up -d`.
3. 매니페스트 파일을 지우는 대신 `kubectl -n webapp scale deployment/<name> --replicas=0` (스케일만 낮춰서 나중에 재시도할 때 롤아웃 히스토리를 유지).

마이그레이션 기간 동안 base `infrastructure/docker-compose.yml`은 절대 건드리지 않기 때문에(AWS 전용 오버라이드인 `docker-compose.prod.yml`만 바뀜) 이 롤백이 항상 저렴하게 유지된다 — compose 기반 서비스 정의가 언제든 그대로 남아있다.
