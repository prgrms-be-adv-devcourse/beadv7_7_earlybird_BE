# GHCR 인증 (fe 이미지 pull)

`beadv7_7_earlybird_fe` 컨테이너 이미지는 GHCR(ghcr.io)에 **private** 패키지로 올라가 있다
(조직 정책상 public 전환 불가 - 확인됨). production EC2가 `docker compose pull fe` 로 이
이미지를 받으려면 인증이 필요하고, `cd.yml` 이 pull 직전에 `GHCR_PAT` 시크릿으로
`docker login` 한다.

## 토큰 재발급이 필요할 때

`GHCR_PAT`는 `read:packages` 스코프의 개인 액세스 토큰(classic)이다.
config-server 용 `GIT_PERSONAL_ACCESS_TOKEN`과 동일한 조직 정책 적용 -
**366일 초과(No expiration 포함) 토큰은 거부됨** ([상세](2_CONFIG_SERVER_SETUP.md)).

만료되거나 `unauthorized` 로 pull 이 실패하면:

1. https://github.com/settings/tokens → 재발급 (scope: `read:packages` 만 있으면 됨)
2. `gh secret set GHCR_PAT --repo prgrms-be-adv-devcourse/beadv7_7_earlybird_BE` 로 교체

## 관련

- FE 자체 이미지 빌드/push는 FE 저장소의 `deploy.yml` 이 내장 `GITHUB_TOKEN`으로 수행한다 -
  별도 설정 불필요, `GHCR_PAT`와는 무관.
- 같은 조직 정책의 상세 트러블슈팅은 [config-server PAT 설정](2_CONFIG_SERVER_SETUP.md) 참고.
