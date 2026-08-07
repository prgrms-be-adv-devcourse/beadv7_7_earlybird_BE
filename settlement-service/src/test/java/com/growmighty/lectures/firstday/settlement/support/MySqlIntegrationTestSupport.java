// TODO(settlement-plan): Include new fact, Inbox, Outbox, run, and reconciliation tables in deterministic cleanup and fixtures.
package com.growmighty.lectures.firstday.settlement.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MySQLContainer;

/**
 * Settlement 통합 테스트가 JVM 안에서 공유하는 MySQL 컨테이너다.
 *
 * <p>테스트 클래스마다 컨테이너를 다시 시작하면 Spring 테스트 컨텍스트 캐시가
 * 종료된 컨테이너의 포트를 계속 참조할 수 있으므로, JVM당 하나만 시작한다.
 */
public abstract class MySqlIntegrationTestSupport {

    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    static {
        MYSQL.start();
    }
}
