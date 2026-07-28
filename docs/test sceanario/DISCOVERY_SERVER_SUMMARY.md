# 서비스 명부(discovery-server) 소개 — 발표용 요약

## 요약

- 어떤 서비스가 어느 IP·포트에 떠 있는지 기록하는 "전화번호부" — 서비스들이 서로를 호출할 때 주소를 하드코딩하지 않고 이름으로 찾을 수 있게 한다
- 커스텀 코드 없음 — Netflix Eureka Server 의 내장 기능(`@EnableEurekaServer`)만으로 동작, 우리가 직접 짠 로직은 없다

## 하는 일

`gateway-server`와 각 business 서비스(`user-service`, `order-service`, `project-service` 등)는 기동하면서 자기 자신(이름·IP·포트)을 discovery-server 에 등록한다. 이후 다른 서비스를 호출할 때는 `lb://SERVICE-NAME` 형태로 이름만 알면 되고, discovery-server 가 그 이름에 해당하는 실제 주소를 알려준다 — 서비스가 재시작해서 포트가 바뀌어도 호출하는 쪽 코드는 그대로다.

discovery-server 자기 자신은 다른 서버에 등록하거나 명부를 받아오지 않는다 (`eureka.client.register-with-eureka=false`, `fetch-registry=false`) — 자기가 명부 그 자체이기 때문이다. `config-server`는 이 명부에 등록하지 않는다 (Eureka client 의존성이 없음) — 다른 서비스가 config-server를 이름으로 찾을 일이 없고, 고정 주소(`http://localhost:8888`)로 직접 호출되기 때문이다.

코드: [`DiscoveryServiceApplication`](../../discovery-server/src/main/java/com/growmighty/lectures/firstday/discovery/DiscoveryServiceApplication.java) — `@EnableEurekaServer` 하나가 전부. 설정: [`application.properties`](../../discovery-server/src/main/resources/application.properties)

## 흐름

```
discovery-server 기동 (:8761)
→ gateway-server, user-service 등 각 서비스가 기동하며 자기 이름·주소를 등록
→ 한 서비스가 다른 서비스를 호출할 때 lb://SERVICE-NAME 으로 요청
→ discovery-server 가 등록된 실제 주소로 안내 (로드밸런싱 포함)
→ 서비스가 내려가면 등록에서 빠지고, 다시 기동하면 재등록된다
```

기동 순서상 `config-server` 다음, `gateway-server`와 business 서비스들보다 먼저 떠 있어야 한다 — 명부가 없으면 등록도, 서로 찾기도 할 수 없다.

## 왜 테스트가 없나

이 서버에도 우리가 작성한 로직이 없다 — 있는 건 `@EnableEurekaServer` 애노테이션과 설정 몇 줄뿐이다. 여기에 테스트를 추가하면 Netflix Eureka Server 자체의 등록/조회 동작을 다시 검증하는 셈이라, 실질적인 회귀 방지 효과가 크지 않다고 판단해 테스트는 작성하지 않았다.
