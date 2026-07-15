package com.growmighty.lectures.firstday.settlement.batch;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;

/**
 * 배치 통합 테스트 공용 MySQL 컨테이너 (Testcontainers).
 *
 * <p>싱글턴 컨테이너 패턴: 두 통합 테스트 클래스가 동일한 Spring 컨텍스트를 공유하므로,
 * 컨테이너도 JVM 당 하나를 띄워 클래스 사이에서 재시작되지 않게 한다.
 * (@Container 방식은 클래스마다 컨테이너를 재시작해 캐시된 컨텍스트가 죽은 포트를 바라보게 된다.)
 * 종료는 Testcontainers(Ryuk)가 JVM 종료 시 정리한다.
 */
abstract class MySqlIntegrationTestSupport {

    // 로컬 docker-compose 와 동일한 버전을 쓴다.
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    static {
        MYSQL.start();
    }
}
