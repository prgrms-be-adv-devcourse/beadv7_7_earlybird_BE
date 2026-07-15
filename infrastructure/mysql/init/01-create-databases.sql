-- 얼리버드 서비스별 데이터베이스 생성 (Database per Service)
-- 이 스크립트는 MySQL 컨테이너 "최초 기동" 시 한 번만 실행된다.
-- (earlybird-mysql-data 볼륨이 이미 존재하면 실행되지 않음 — docs/LOCAL_DB_SETUP.md 참고)

CREATE DATABASE IF NOT EXISTS orderdb        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS projectdb      CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS paymentdb      CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS userdb         CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS cartdb         CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS settlementdb   CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS filedb         CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS boarddb        CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS notificationdb CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- earlybird 계정(docker-compose 의 MYSQL_USER)에 전체 서비스 DB 권한 부여
GRANT ALL PRIVILEGES ON orderdb.*        TO 'earlybird'@'%';
GRANT ALL PRIVILEGES ON projectdb.*      TO 'earlybird'@'%';
GRANT ALL PRIVILEGES ON paymentdb.*      TO 'earlybird'@'%';
GRANT ALL PRIVILEGES ON userdb.*         TO 'earlybird'@'%';
GRANT ALL PRIVILEGES ON cartdb.*         TO 'earlybird'@'%';
GRANT ALL PRIVILEGES ON settlementdb.*   TO 'earlybird'@'%';
GRANT ALL PRIVILEGES ON filedb.*         TO 'earlybird'@'%';
GRANT ALL PRIVILEGES ON boarddb.*        TO 'earlybird'@'%';
GRANT ALL PRIVILEGES ON notificationdb.* TO 'earlybird'@'%';

FLUSH PRIVILEGES;
