# 로컬 DB(MySQL) 설정 가이드

얼리버드는 서비스마다 자기만의 DB를 갖는다(Database per Service).
로컬 개발 환경에서는 **MySQL 컨테이너 1개** 안에 서비스별 데이터베이스(스키마) 9개를 만들어 사용한다.
운영처럼 인스턴스를 서비스별로 쪼개지 않아도, 스키마를 분리하면 "다른 서비스의 테이블을 직접 조인할 수 없다"는 경계는 그대로 유지된다.

## 사전 준비

- **Docker Desktop** 설치 및 실행 ([다운로드](https://www.docker.com/products/docker-desktop/))
- 로컬에 MySQL을 직접 설치해서 쓰고 있다면 → [트러블슈팅: 3306 포트 충돌](#3306-포트-충돌) 참고

> ⚠️ DB 의존 테스트(order/settlement)는 **Testcontainers** 로 일회용 MySQL 컨테이너를 띄워서 돈다.
> 따라서 `./gradlew build` (테스트 포함) 실행 시에도 Docker 가 켜져 있어야 한다. (CI 구성 시에도 동일)

## 1. MySQL 컨테이너 실행

레포 루트에서:

```bash
docker compose -f infrastructure/docker-compose.yml up -d mysql
```

최초 실행 시 `infrastructure/mysql/init/01-create-databases.sql` 이 자동 실행되어
서비스별 데이터베이스 9개가 생성되고 `earlybird` 계정에 권한이 부여된다.

> Elasticsearch/Kibana 까지 같이 띄우려면 `mysql` 을 빼고 `up -d` 만 실행하면 된다.

## 2. 접속 정보

| 항목 | 값 |
| --- | --- |
| Host | `localhost` |
| Port | `3306` |
| 애플리케이션 계정 | `earlybird` / `earlybird` |
| root 계정 | `root` / `root` |
| 문자셋 | `utf8mb4` (이모지 포함 한글 OK) |
| 타임존 | `Asia/Seoul` |

⚠️ 위 계정은 **로컬 개발 전용**이다. 배포 환경 계정은 별도로 관리한다.

### 서비스별 데이터베이스

| 서비스 | 데이터베이스 | JDBC URL |
| --- | --- | --- |
| order-service (:8080) | `orderdb` | `jdbc:mysql://localhost:3306/orderdb` |
| project-service (:8081) | `projectdb` | `jdbc:mysql://localhost:3306/projectdb` |
| payment-service (:8082) | `paymentdb` | `jdbc:mysql://localhost:3306/paymentdb` |
| user-service (:8083) | `userdb` | `jdbc:mysql://localhost:3306/userdb` |
| cart-service (:8085) | `cartdb` | `jdbc:mysql://localhost:3306/cartdb` |
| settlement-service (:8086) | `settlementdb` | `jdbc:mysql://localhost:3306/settlementdb` |
| file-service (:8087) | `filedb` | `jdbc:mysql://localhost:3306/filedb` |
| board-service (:8088) | `boarddb` | `jdbc:mysql://localhost:3306/boarddb` |
| notification-service (:8089) | `notificationdb` | `jdbc:mysql://localhost:3306/notificationdb` |

각 서비스가 실제로 어떤 URL/계정을 쓰는지는 서비스 모듈이 아니라
**config 레포(`beadv7_7_earlybird_config`)의 서비스별 `.yml`** 에 정의되어 있다.
접속 정보를 바꾸려면 그쪽을 수정하고 config-server 를 재기동(또는 `/actuator/refresh`)해야 한다.

## 3. 정상 동작 확인

```bash
# 컨테이너가 떠 있는지 (STATUS 가 Up 인지)
docker ps --filter name=earlybird-mysql

# 데이터베이스 9개가 만들어졌는지
docker exec earlybird-mysql mysql -uearlybird -pearlybird -e "SHOW DATABASES;"
```

`orderdb` ~ `notificationdb` 가 목록에 보이면 성공.

## 4. GUI 툴로 접속 (선택)

IntelliJ Database 탭 (또는 DBeaver 등):

1. `+` → Data Source → MySQL
2. Host `localhost`, Port `3306`, User `earlybird`, Password `earlybird`
3. Database 는 비워두면 전체 스키마가 보인다 (Schemas 탭에서 9개 모두 체크)

## 5. 서비스 띄워서 테이블 생성 확인

기동 순서: `config-server` → `discovery-server` → `gateway-server` → 각 서비스.

```bash
./gradlew :config-server:bootRun     # 먼저
./gradlew :order-service:bootRun     # 이후
```

JPA `ddl-auto` 설정에 따라 기동 시 엔티티 테이블이 자동 생성된다.
확인:

```bash
docker exec earlybird-mysql mysql -uearlybird -pearlybird -e "SHOW TABLES IN orderdb;"
```

## 데이터 초기화 (깨끗하게 다시 시작)

데이터는 `earlybird-mysql-data` 볼륨에 저장되므로 컨테이너를 지워도 남는다.
스키마까지 완전히 리셋하려면 볼륨을 삭제하고 다시 띄운다:

```bash
docker compose -f infrastructure/docker-compose.yml down mysql
docker volume rm infrastructure_earlybird-mysql-data
docker compose -f infrastructure/docker-compose.yml up -d mysql
```

> init 스크립트(`01-create-databases.sql`)는 **볼륨이 없을 때만** 실행된다.
> 스크립트를 수정했는데 반영이 안 된다면 위 절차로 볼륨을 지워야 한다.

## 트러블슈팅

### 3306 포트 충돌

로컬에 MySQL 이 이미 설치되어 있으면 `Ports are not available: ... 3306` 에러가 난다.

- **권장**: 로컬 MySQL 서비스를 중지하고 컨테이너를 쓴다.
  - Windows: 관리자 PowerShell 에서 `net stop MySQL80` (서비스 이름은 버전에 따라 다름)
  - macOS: `brew services stop mysql`
- 로컬 MySQL 을 꼭 유지해야 한다면 [Docker 없이 로컬 MySQL 사용](#부록-docker-없이-로컬-mysql-사용)을 따른다.
  (compose 의 포트 매핑을 `13306:3306` 처럼 바꾸는 방법도 있지만, config 레포의 JDBC URL 은 팀 공통이므로 혼자만 포트를 바꾸면 안 된다.)

### Access denied for user 'earlybird'

init 스크립트가 실행되기 전(과거 볼륨이 남아있는 상태)일 가능성이 높다.
[데이터 초기화](#데이터-초기화-깨끗하게-다시-시작) 절차로 볼륨을 지우고 다시 띄운다.

### 서비스가 DB 연결에 실패하며 기동이 안 됨

1. `docker ps` 로 MySQL 이 떠 있는지 확인 (기동 직후라면 준비까지 10~20초 걸릴 수 있음)
2. config-server 가 먼저 떠 있는지, 서비스가 config 를 정상적으로 받아왔는지 확인
3. 한 번도 초기화된 적 없는 개발 환경이라면 `docker logs earlybird-mysql` 로 init 스크립트 에러 여부 확인

### 한글이 `???` 로 저장됨

컨테이너가 `utf8mb4` 로 기동되므로 정상적으로는 발생하지 않는다.
발생했다면 GUI 툴/클라이언트의 연결 문자셋을 확인하고, 오래된 볼륨이라면 초기화 후 재시도.

## 부록: Docker 없이 로컬 MySQL 사용

이미 설치된 로컬 MySQL(8.x)을 그대로 쓰려면 root 로 접속해 다음을 실행한다:

```sql
CREATE USER IF NOT EXISTS 'earlybird'@'%' IDENTIFIED BY 'earlybird';
SOURCE infrastructure/mysql/init/01-create-databases.sql;
```

문자셋이 `utf8mb4` 인지(`SHOW VARIABLES LIKE 'character_set_server';`) 확인할 것.
이후 확인 절차는 [3. 정상 동작 확인](#3-정상-동작-확인)과 동일하다 (docker exec 대신 `mysql -u...` 직접 실행).
