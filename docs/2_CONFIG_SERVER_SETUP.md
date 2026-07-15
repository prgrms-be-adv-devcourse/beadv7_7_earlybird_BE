# config-server 실행 가이드 (GitHub 토큰 설정)

모든 서비스의 실제 설정(포트, DB 접속 정보 등)은 서비스 모듈이 아니라
**config 레포([beadv7_7_earlybird_config](https://github.com/prgrms-be-adv-devcourse/beadv7_7_earlybird_config))** 에 있고,
config-server 가 기동 시 이 레포를 GitHub 에서 clone 해서 각 서비스에 배달한다.

그런데 이 레포는 **private** 이라 clone 에 인증이 필요하다.
config-server 는 내장 JGit 으로 직접 clone 하기 때문에, 내 PC 의 git 로그인(자격 증명 관리자)을 **쓰지 않는다** —
아래처럼 환경변수로 GitHub 토큰을 따로 넘겨줘야 한다.

> ⚠️ 토큰은 **각자 본인 것을 발급**한다. 남의 토큰을 공유받아 쓰지 말 것 —
> PAT 는 그 사람 GitHub 계정 전체 권한이 담긴 열쇠이고, 채팅에 붙여넣는 순간 유출된 것으로 봐야 한다.

## 1. GitHub 토큰(PAT) 발급

1. GitHub → [Settings → Developer settings → Personal access tokens → Tokens (classic)](https://github.com/settings/tokens) → **Generate new token (classic)**
2. Note: `earlybird-config-server` 등 알아볼 이름
3. **Expiration: 90 days** 권장
   > ⚠️ 조직(`prgrms-be-adv-devcourse`) 정책상 **366일 초과(No expiration 포함) 토큰은 403 으로 거부**된다.
   > 스코프가 맞아도 `git-upload-pack not permitted` 에러가 나면 이것부터 의심할 것.
4. 스코프: **`repo`** 전체 체크 (private 레포 읽기에 필요)
5. Generate 후 나오는 `ghp_...` 값을 복사해 둔다 (이 화면을 벗어나면 다시 볼 수 없음)

토큰이 만료되면 같은 절차로 재발급하고 아래 환경변수만 갈아끼우면 된다.

## 2. 환경변수 설정 (IntelliJ)

config-server 의 Run Configuration → **Environment variables** 에 추가:

| 변수 | 값 |
| --- | --- |
| `GIT_USERNAME` | 본인 GitHub 아이디 |
| `GIT_PERSONAL_ACCESS_TOKEN` | 발급받은 `ghp_...` 토큰 |

터미널(`./gradlew :config-server:bootRun`)로 띄우는 경우:

```powershell
# PowerShell
$env:GIT_USERNAME = "깃허브아이디"
$env:GIT_PERSONAL_ACCESS_TOKEN = "ghp_..."
./gradlew :config-server:bootRun
```

```bash
# macOS / Git Bash
GIT_USERNAME=깃허브아이디 GIT_PERSONAL_ACCESS_TOKEN=ghp_... ./gradlew :config-server:bootRun
```

> ⚠️ 토큰을 `application.yml` 등 **코드에 직접 쓰거나 커밋하지 말 것.**
> 커밋된 yml 에는 `${GIT_USERNAME}` / `${GIT_PERSONAL_ACCESS_TOKEN}` 플레이스홀더만 있어야 한다.
> (GitHub 는 커밋에서 토큰을 발견하면 자동으로 폐기시킨다.)

## 3. 정상 동작 확인

config-server 기동 후:

```bash
curl http://localhost:8888/order-service/default
```

응답 JSON 에 `jdbc:mysql://localhost:3306/orderdb` 가 보이면 성공.
이후 기동 순서는 `discovery-server` → `gateway-server` → 각 서비스 순
([로컬 DB 가이드](1_LOCAL_DB_SETUP.md) 참고 — MySQL 컨테이너가 먼저 떠 있어야 한다).

## 트러블슈팅

증상은 대부분 두 곳에서 나타난다: config-server 자신의 clone 실패 로그, 또는
설정을 못 받은 **서비스 쪽의 엉뚱한 에러**.

### 서비스가 `Failed to configure a DataSource: 'url' attribute is not specified` 로 죽음

서비스 문제가 아니라 **config-server 에서 설정을 못 받아온 것**이다.
서비스 로그 위쪽에 `Could not locate PropertySource ... None of labels [] found` 가 함께 보인다.
→ config-server 가 떠 있는지, 위 curl 확인이 통과하는지부터 본다.

### `Authentication is required but no CredentialsProvider has been registered`

인증 정보가 아예 전달되지 않은 것 (HTTP 401).
→ 환경변수 2개가 config-server 의 **실행 환경**에 설정됐는지 확인.
IntelliJ 라면 해당 Run Configuration 에 넣었는지, 수정 후 **재시작**했는지 확인.

### `git-upload-pack not permitted on '...beadv7_7_earlybird_config.git'`

인증은 됐지만 권한이 거부된 것 (HTTP 403). 순서대로 확인:

1. **토큰 Expiration 이 366일 초과(No expiration)가 아닌지** — 조직 정책상 가장 흔한 원인.
   [토큰 설정](https://github.com/settings/tokens)에서 Expiration 을 90일 등으로 줄인다.
   (수정하면 토큰 값이 재발급되므로 환경변수도 새 값으로 교체)
2. 토큰에 `repo` 스코프가 있는지
3. 본인 계정이 조직 멤버로 config 레포에 접근 가능한지 (브라우저에서 레포가 열리는지)

원인 확인용 — 아래 명령의 응답 본문에 거부 사유가 그대로 나온다:

```bash
curl -u 깃허브아이디:ghp_토큰 https://api.github.com/repos/prgrms-be-adv-devcourse/beadv7_7_earlybird_config
```

### 토큰을 실수로 노출했다면 (채팅에 붙여넣음, 커밋함 등)

[토큰 설정](https://github.com/settings/tokens)에서 해당 토큰을 **즉시 Delete** 하고 재발급한다.
