# 설정 서버(config-server) 소개 — 발표용 요약

## 요약

- 모든 서비스의 실제 설정(포트, DB 접속 정보, JWT 시크릿 등)을 **코드가 아닌 이 서버 하나**가 관리 — 서비스 모듈에는 `spring.application.name` 만 남기고, 나머지는 여기서 내려받는다
- 커스텀 코드 없음 — Spring Cloud Config Server 의 내장 기능(`@EnableConfigServer`)만으로 동작, 우리가 직접 짠 로직은 없다

## 하는 일

각 서비스가 기동할 때 `optional:configserver:http://localhost:8888` 로 자신의 설정을 요청하면, config-server 가 **팀 전용 private 레포([beadv7_7_earlybird_config](https://github.com/prgrms-be-adv-devcourse/beadv7_7_earlybird_config))** 에서 그 서비스 이름에 맞는 `.yml` 파일을 찾아 내려준다. 설정의 단일 출처(single source of truth)가 코드 저장소가 아니라 별도 레포이므로, 포트나 DB 주소를 바꿀 때 서비스 코드를 건드리지 않고 그 레포만 고치면 된다.

로컬 개발에서는 GitHub 대신 로컬 파일시스템을 읽는 `native` 프로파일을 켜서, 커밋 없이도 바로 반영되는 설정으로 테스트할 수 있다.

| 실행 방식 | 무엇을 읽나 | 설정 |
| --- | --- | --- |
| 기본(운영/기본 로컬) | GitHub 원격 저장소(private) | [`application.yml`](../../config-server/src/main/resources/application.yml) |
| `SPRING_PROFILES_ACTIVE=native` (docker-compose 로컬) | 로컬 파일시스템(`file:///config-repo`) | [`application-native.yml`](../../config-server/src/main/resources/application-native.yml) |

코드: [`ConfigServerApplication`](../../config-server/src/main/java/com/growmighty/lectures/firstday/configserver/ConfigServerApplication.java) — `@EnableConfigServer` 하나가 전부.

## 흐름

```
config-server 기동
→ private config 레포를 clone (GIT_USERNAME/GIT_PERSONAL_ACCESS_TOKEN 으로 인증)
→ discovery-server, gateway-server, 각 business 서비스가 순서대로 기동하며
  optional:configserver:http://localhost:8888 로 자신의 설정을 요청
→ config-server 가 서비스 이름(spring.application.name)에 맞는 .yml 을 찾아 응답
→ 각 서비스는 받은 설정(포트, DB 주소, JWT 시크릿 등)으로 나머지 기동을 마친다
```

기동 순서가 이래서 중요하다 — config-server 가 먼저 떠 있어야 나머지 서비스들이 자기 설정을 받을 수 있다 (`optional:` 이므로 config-server 가 꺼져 있어도 기동 자체는 실패하지 않지만, 그 경우 로컬에 남겨둔 최소 값만으로 뜬다).

## 왜 테스트가 없나

이 서버에는 우리가 작성한 로직이 없다 — 있는 건 `@EnableConfigServer` 애노테이션과 YAML 설정뿐이다. 여기에 테스트를 추가한다면 우리 코드가 아니라 Spring Cloud Config Server 자체의 동작(예: git clone이 되는지)을 다시 검증하는 셈이라, 실질적인 회귀 방지 효과가 크지 않다고 판단해 테스트는 작성하지 않았다.

## 설계 결정과 트레이드오프

- **설정 저장 위치: 별도 private git 레포 vs 서비스 코드에 값 하드코딩** — 설정 전용 레포([beadv7_7_earlybird_config](https://github.com/prgrms-be-adv-devcourse/beadv7_7_earlybird_config))를 두고 config-server가 내려주는 방식을 선택. 포트·DB주소·시크릿을 바꿀 때 서비스 코드를 재배포하지 않아도 되지만, 그만큼 config-server와 그 레포가 살아 있어야 다른 서비스들이 정상적으로 설정을 받는다 — 그래서 `optional:`로 감싸 config-server가 꺼져 있어도 서비스가 최소값으로는 기동되게 했다.
- **로컬 개발: git 원격 그대로 vs native(로컬 파일) 프로파일** — 로컬은 `native` 프로파일로 로컬 파일시스템을 직접 읽게 했다. 커밋 없이도 바로 반영돼 반복 개발이 빠르지만, "로컬에서 쓰는 설정 파일"과 "운영에서 쓰는 git 설정"이 갈라져 있어 둘이 어긋날 위험이 생긴다 — 그래서 두 쪽 파일 구조를 최대한 동일하게 맞춰 관리한다.

## 참고

- 인증 토큰(PAT) 발급/설정 방법: [`docs/2_CONFIG_SERVER_SETUP.md`](../2_CONFIG_SERVER_SETUP.md)
