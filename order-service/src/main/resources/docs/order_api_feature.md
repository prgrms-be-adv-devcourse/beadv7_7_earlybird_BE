# Order API 기능 명세

## 1. 문서 목적

본 문서는 현재 `OrderApiService` 리뷰 결과를 기준으로 주문 생성, 내 주문 목록 조회, 주문 상세 조회 기능의 통신 방식과 데이터 형식을 정리한다.

다음 사항을 전제로 한다.

- 네트워크 오류, 타임아웃, 재시도에 의한 중복 요청은 발생하지 않는다.
- Project, Reward, Payment, Cart 등 타 도메인과의 연동은 정상적으로 동작한다.
- `GET /orders/me`는 페이징을 사용하지 않고 사용자의 전체 주문 목록을 반환한다.
- `Order`와 `OrderItem`은 일대다 관계이다.
- 하나의 주문은 하나 이상의 주문 항목을 가질 수 있다.
- 동일 주문 요청 안에서 같은 `rewardId`가 중복되는 경우는 허용하지 않는다.

---

## 2. 공통 통신 규격

| 구분 | 내용 |
|---|---|
| 통신 방식 | HTTP REST API |
| 데이터 형식 | JSON |
| 요청 Content-Type | `application/json` |
| 응답 Content-Type | `application/json` |
| 사용자 식별 | Gateway가 전달한 `X-User-Id` 헤더 사용 |
| 사용자 역할 | Gateway가 전달한 `X-User-Role` 헤더 사용 |

### 2.1 공통 성공 응답 래퍼

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

### 2.2 공통 실패 응답 예시

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "ERROR_CODE",
    "message": "오류 메시지"
  }
}
```

---

# 3. 주문 생성

## 3.1 기능 개요

다수의 reward를 하나의 주문으로 생성한다.

요청된 각 reward는 별도의 `OrderItem`으로 생성되며, 모든 `OrderItem`은 하나의 `Order`에 연결된다.

현재 구조에서는 다음 흐름으로 처리된다.

1. 요청 항목을 순회한다.
2. 각 reward에 대해 하나의 `OrderItem`을 생성한다.
3. 생성된 `OrderItem`을 `Order`의 항목 컬렉션에 추가한다.
4. 각 `OrderItem`에 부모 `Order` 참조를 설정한다.
5. `Order` 저장 시 cascade 설정을 통해 모든 `OrderItem`을 함께 저장한다.
6. 전체 항목 금액과 배송비를 계산한다.
7. 주문 생성 결과와 생성된 주문 항목을 응답한다.

## 3.2 통신 정보

| 구분 | 내용 |
|---|---|
| 통신 타입 | HTTP REST |
| Method | `POST` |
| URL | `/orders` |
| Request Body | 있음 |
| Response Body | 있음 |

## 3.3 Request Header

```http
X-User-Id: 1
Content-Type: application/json
```

| 헤더 | 타입 | 필수 | 설명 |
|---|---:|---:|---|
| `X-User-Id` | Long | Y | 실제 주문 소유자로 사용되는 요청 사용자 ID |
| `Content-Type` | String | Y | `application/json` |

## 3.4 Request Body

```json
{
  "userId": 1,
  "requests": [
    {
      "rewardId": 101,
      "quantity": 1,
      "expectedUnitPrice": 10000
    },
    {
      "rewardId": 102,
      "quantity": 2,
      "expectedUnitPrice": 15000
    }
  ],
  "receiverName": "홍길동",
  "receiverPhone": "010-1234-5678",
  "shippingAddress": "서울특별시 강남구 테헤란로 1",
  "zipCode": "06234",
  "expectedItemsAmount": 40000,
  "expectedTotalAmount": 43000
}
```

### 3.4.1 Request 필드

| 필드 | 타입 | 필수 | 설명 |
|---|---:|---:|---|
| `userId` | Long | Y | 현재 요청 DTO에서 필수이나, 실제 주문 소유권은 `X-User-Id`를 기준으로 처리 |
| `requests` | Array | Y | 주문할 reward 목록. 최소 1개 이상 필요 |
| `requests[].rewardId` | Long | Y | 주문할 reward ID |
| `requests[].quantity` | Integer | Y | 주문 수량. 1 이상의 양수 |
| `requests[].expectedUnitPrice` | BigDecimal | Y | 클라이언트가 예상한 reward 단가 |
| `receiverName` | String | Y | 수령인 이름. null 또는 공백 불가 |
| `receiverPhone` | String | Y | 수령인 전화번호. null 또는 공백 불가 |
| `shippingAddress` | String | Y | 배송 주소. null 또는 공백 불가 |
| `zipCode` | String | Y | 우편번호. null 또는 공백 불가 |
| `expectedItemsAmount` | BigDecimal | Y | 클라이언트가 예상한 상품 합계 금액 |
| `expectedTotalAmount` | BigDecimal | Y | 클라이언트가 예상한 배송비 포함 최종 금액 |

### 3.4.2 요청 검증 기준

- `requests`는 비어 있을 수 없다.
- `rewardId`는 필수이다.
- `quantity`는 필수이며 양수여야 한다.
- `expectedUnitPrice`는 필수이다.
- 같은 요청 안에서 동일한 `rewardId`가 중복되면 주문을 거부한다.
- 요청 금액과 서버에서 계산한 금액이 일치하는지 검증한다.
- Request Body의 `userId`보다 `X-User-Id`가 실제 사용자 식별 기준으로 우선한다.

## 3.5 Response Body

주문 생성 응답에는 다음 정보가 포함. -> 양식 논의 필요

- 주문 생성 성공 결과
- 주문 기본 정보
- 주문 금액 정보
- 배송 정보
- 생성된 모든 주문 항목

```json
{
  "success": true,
  "data": {
    "result": {
      "code": "ORDER_CREATED",
      "message": "주문이 정상적으로 생성되었습니다."
    },
    "id": 1001,
    "status": "PAID",
    "itemsAmount": 40000,
    "shippingFee": 3000,
    "totalAmount": 43000,
    "receiverName": "홍길동",
    "receiverPhone": "010-1234-5678",
    "shippingAddress": "서울특별시 강남구 테헤란로 1",
    "zipCode": "06234",
    "orderItems": [
      {
        "id": 5001,
        "name": "얼리버드 리워드",
        "price": 10000,
        "projectId": 10,
        "rewardId": 101,
        "quantity": 1,
        "subtotal": 10000
      },
      {
        "id": 5002,
        "name": "스페셜 리워드",
        "price": 15000,
        "projectId": 10,
        "rewardId": 102,
        "quantity": 2,
        "subtotal": 30000
      }
    ]
  },
  "error": null
}
```

### 3.5.1 Response 필드

| 필드 | 타입 | 설명 |
|---|---:|---|
| `result.code` | String | 주문 생성 성공 결과 코드 |
| `result.message` | String | 주문 생성 성공 안내 메시지 |
| `id` | Long | 생성된 주문 ID |
| `status` | String | 주문 상태 |
| `itemsAmount` | BigDecimal | 모든 주문 항목의 합계 금액 |
| `shippingFee` | BigDecimal | 배송비 |
| `totalAmount` | BigDecimal | 항목 금액과 배송비를 합한 최종 금액 |
| `receiverName` | String | 수령인 이름 |
| `receiverPhone` | String | 수령인 전화번호 |
| `shippingAddress` | String | 배송 주소 |
| `zipCode` | String | 우편번호 |
| `orderItems` | Array | 생성된 주문 항목 목록 |
| `orderItems[].id` | Long | 주문 항목 ID |
| `orderItems[].name` | String | 주문 시점 reward 이름 스냅샷 |
| `orderItems[].price` | BigDecimal | 주문 시점 reward 단가 스냅샷 |
| `orderItems[].projectId` | Long | reward가 속한 프로젝트 ID |
| `orderItems[].rewardId` | Long | reward ID |
| `orderItems[].quantity` | Integer | 주문 수량 |
| `orderItems[].subtotal` | BigDecimal | `price × quantity`로 계산된 항목별 금액 |

## 3.6 기능 판정

- 서로 다른 다수의 reward를 하나의 주문으로 저장할 수 있다.
- 요청 항목마다 하나의 `OrderItem`이 생성된다.
- `Order`와 `OrderItem`의 연관관계가 설정된 후 함께 저장된다.
- 전체 항목 금액은 모든 `OrderItem`의 소계를 합산한다.
- 배송비는 주문 단위로 한 번 적용된다.
- 생성 응답에서 주문 항목과 성공 결과를 함께 제공한다.

---

# 4. 내 주문 목록 조회

## 4.1 기능 개요

현재 로그인한 사용자의 전체 주문 내역을 조회한다.

현재 조회는 주문 상태를 별도로 필터링하지 않으므로 저장된 주문이라면 상태와 관계없이 포함될 수 있다.

## 4.2 통신 정보

| 구분 | 내용 |
|---|---|
| 통신 타입 | HTTP REST |
| Method | `GET` |
| URL | `/orders/me` |
| Request Body | 없음 |
| Response Body | 있음 |
| 페이징 | 사용하지 않음 |

## 4.3 Request Header

```http
X-User-Id: 1
X-User-Role: USER_ROLE
```

| 헤더 | 타입 | 필수 | 설명 |
|---|---:|---:|---|
| `X-User-Id` | Long | Y | 주문 목록을 조회할 사용자 ID |
| `X-User-Role` | String | Y | 사용자 권한 |

## 4.4 Query Parameter

```text
GET /orders/me?userId=1
```

| 파라미터 | 타입 | 필수 | 설명 |
|---|---:|---:|---|
| `userId` | Long | Y | `X-User-Id`와 동일해야 하는 사용자 ID |

현재 `/me` 경로임에도 `userId` 쿼리 파라미터를 함께 요구한다.

## 4.5 Request Body

없음.

## 4.6 Response Body

```json
{
  "success": true,
  "data": [
    {
      "id": 1001,
      "status": "PAID",
      "itemsAmount": 40000,
      "shippingFee": 3000,
      "totalAmount": 43000,
      "receiverName": "홍길동",
      "receiverPhone": "010-1234-5678",
      "shippingAddress": "서울특별시 강남구 테헤란로 1",
      "zipCode": "06234"
    },
    {
      "id": 1002,
      "status": "PAYMENT_FAILED",
      "itemsAmount": 20000,
      "shippingFee": 3000,
      "totalAmount": 23000,
      "receiverName": "홍길동",
      "receiverPhone": "010-1234-5678",
      "shippingAddress": "서울특별시 강남구 테헤란로 1",
      "zipCode": "06234"
    }
  ],
  "error": null
}
```

### 4.6.1 Response 필드

| 필드 | 타입 | 설명 |
|---|---:|---|
| `data` | Array | 사용자의 전체 주문 목록 |
| `data[].id` | Long | 주문 ID |
| `data[].status` | String | 주문 상태 |
| `data[].itemsAmount` | BigDecimal | 주문 항목 합계 금액 |
| `data[].shippingFee` | BigDecimal | 배송비 |
| `data[].totalAmount` | BigDecimal | 최종 주문 금액 |
| `data[].receiverName` | String | 수령인 이름 |
| `data[].receiverPhone` | String | 수령인 전화번호 |
| `data[].shippingAddress` | String | 배송 주소 |
| `data[].zipCode` | String | 우편번호 |

## 4.7 조회 규칙

- `X-User-Id`와 `userId` 쿼리 파라미터가 일치해야 한다.
- 사용자 ID가 일치하지 않으면 조회를 거부한다.
- 해당 사용자의 주문만 조회한다.
- 조회 결과가 없으면 빈 배열을 반환한다.
- 페이징은 사용하지 않는다.
- 현재 명시적인 정렬 조건은 없다.
- 현재 명시적인 주문 상태 필터는 없다.
- 목록 응답에는 `orderItems`를 포함하지 않고 주문 요약만 제공한다.

## 4.8 기능 판정

- 본인의 주문 목록을 조회할 수 있다.
- 다른 사용자의 주문이 목록에 포함되지 않도록 사용자 ID를 검증한다.
- 사용자의 모든 주문을 한 번에 반환한다.
- 데이터가 많아질 경우 응답 크기가 커질 수 있으나, 본 문서에서는 페이징이 없다고 가정한다.

---

# 5. 주문 상세 조회

## 5.1 기능 개요

주문 ID를 기준으로 하나의 주문을 조회한다.

주문 기본 정보와 함께 해당 주문에 속한 모든 `order_items`를 반환한다.

`Order`와 `OrderItem`의 관계는 다음과 같다.

```text
Order 1 : N OrderItem
```

- 하나의 `Order`는 여러 `OrderItem`을 가진다.
- 하나의 `OrderItem`은 반드시 하나의 `Order`에 속한다.
- `order_items.order_id`는 부모 주문의 ID를 참조한다.

## 5.2 통신 정보

| 구분 | 내용 |
|---|---|
| 통신 타입 | HTTP REST |
| Method | `GET` |
| URL | `/orders/{orderId}` |
| Request Body | 없음 |
| Response Body | 있음 |

## 5.3 Request Header

```http
X-User-Id: 1
```

| 헤더 | 타입 | 필수 | 설명 |
|---|---:|---:|---|
| `X-User-Id` | Long | Y | 주문 소유권을 검증할 사용자 ID |

## 5.4 Path Parameter

```text
GET /orders/1001
```

| 파라미터 | 타입 | 필수 | 설명 |
|---|---:|---:|---|
| `orderId` | Long | Y | 조회할 주문 ID |

## 5.5 Query Parameter

```text
GET /orders/1001?userId=1
```

| 파라미터 | 타입 | 필수 | 설명 |
|---|---:|---:|---|
| `userId` | Long | N | 전달된 경우 `X-User-Id`와 일치해야 함 |

## 5.6 Request Body

없음.

## 5.7 Response Body

```json
{
  "success": true,
  "data": {
    "id": 1001,
    "status": "PAID",
    "itemsAmount": 40000,
    "shippingFee": 3000,
    "totalAmount": 43000,
    "receiverName": "홍길동",
    "receiverPhone": "010-1234-5678",
    "shippingAddress": "서울특별시 강남구 테헤란로 1",
    "zipCode": "06234",
    "orderItems": [
      {
        "id": 5001,
        "name": "얼리버드 리워드",
        "price": 10000,
        "projectId": 10,
        "rewardId": 101,
        "quantity": 1,
        "subtotal": 10000
      },
      {
        "id": 5002,
        "name": "스페셜 리워드",
        "price": 15000,
        "projectId": 10,
        "rewardId": 102,
        "quantity": 2,
        "subtotal": 30000
      }
    ]
  },
  "error": null
}
```

## 5.8 Order Response 필드

| 필드 | 타입 | 설명 |
|---|---:|---|
| `id` | Long | 주문 ID |
| `status` | String | 주문 상태 |
| `itemsAmount` | BigDecimal | 주문 항목 합계 금액 |
| `shippingFee` | BigDecimal | 배송비 |
| `totalAmount` | BigDecimal | 최종 주문 금액 |
| `receiverName` | String | 수령인 이름 |
| `receiverPhone` | String | 수령인 전화번호 |
| `shippingAddress` | String | 배송 주소 |
| `zipCode` | String | 우편번호 |
| `orderItems` | Array | 주문에 속한 전체 주문 항목 |

## 5.9 OrderItem Response 필드

아래 필드는 제공된 `OrderItem` 엔티티의 컬럼을 기준으로 한다.

| 필드 | 엔티티 타입 | 응답 타입 | 설명 |
|---|---:|---:|---|
| `id` | Long | Long | `IDENTITY` 방식으로 생성되는 주문 항목 ID |
| `order` | Order | 미노출 | 응답에서는 순환 참조 방지를 위해 부모 객체 전체를 반환하지 않음 |
| `order_id` | Long | 미노출 또는 `orderId` | DB에서 부모 주문을 참조하는 외래 키 |
| `name` | String | String | 주문 시점 reward 이름 스냅샷 |
| `price` | Money | BigDecimal | 주문 시점 reward 단가 스냅샷 |
| `projectId` | Long | Long | reward가 속한 프로젝트 ID |
| `rewardId` | Long | Long | 재고 차감 및 복원 기준이 되는 reward ID |
| `quantity` | Integer | Integer | reward 주문 수량 |
| `subtotal` | 계산값 | BigDecimal | `price × quantity`로 계산한 항목별 금액 |


응답에서는 `OrderItem.order` 객체를 그대로 포함하지 않는다. 부모 주문 정보는 최상위 `data`에 이미 포함되므로, 하위 `orderItems`에서는 항목 자체의 데이터만 반환한다.

## 5.10 조회 및 권한 검증 규칙

- `orderId`로 주문을 조회한다.
- 주문이 존재하지 않으면 Not Found 오류를 반환한다.
- 조회된 주문의 사용자 ID와 `X-User-Id`가 일치하는지 검증한다.
- 다른 사용자의 주문이면 접근을 거부한다.
- `userId` 쿼리 파라미터가 전달된 경우 `X-User-Id`와 일치해야 한다.
- 주문에 연결된 모든 `OrderItem`을 `orderItems` 배열로 반환한다.

## 5.11 기능 판정

- 주문 ID를 통한 단건 조회가 가능하다.
- 요청 사용자와 주문 소유자를 비교해 다른 사용자의 주문 접근을 차단한다.
- 주문 상세 응답에는 주문 기본 정보뿐 아니라 모든 주문 항목을 포함한다.
- 각 주문 항목은 주문 당시의 reward 이름, 단가, 프로젝트 ID, reward ID, 수량을 제공한다.

---

# 6. API 요약

| 기능 | 통신 타입 | Method | URL | Request | Response |
|---|---|---|---|---|---|
| 주문 생성 | HTTP REST | `POST` | `/orders` | 사용자 정보, 배송 정보, 다수 reward 목록, 예상 금액 | 생성 성공 결과, 주문 정보, 전체 `orderItems` |
| 내 주문 목록 조회 | HTTP REST | `GET` | `/orders/me` | `X-User-Id`, `X-User-Role`, `userId` query | 페이징 없는 주문 요약 배열 |
| 주문 상세 조회 | HTTP REST | `GET` | `/orders/{orderId}` | `orderId`, 사용자 식별 헤더, 선택적 `userId` query | 주문 상세 정보와 전체 `orderItems` |

---

# 7. 최종 정리

## `POST /orders`

- 다수의 서로 다른 reward를 하나의 주문으로 저장할 수 있다.
- 각 reward는 별도의 `OrderItem`으로 저장된다.
- 응답에는 주문 생성 성공 결과와 생성된 모든 주문 항목을 포함한다.

## `GET /orders/me`

- 현재 사용자의 전체 주문 내역을 조회한다.
- 현재 기준 페이징 기능 X -> 추후 논의 필요하다고 생각되는 부분.
- 응답은 주문 요약 목록이며 `orderItems`는 포함하지 않는다.

## `GET /orders/{orderId}`

- 하나의 주문을 상세 조회한다.
- 주문 소유권을 검증한다.
- `Order`와 `OrderItem`은 일대다 관계이다.
- 응답에는 주문 기본 정보와 하위 `orderItems` 전체를 포함한다.
