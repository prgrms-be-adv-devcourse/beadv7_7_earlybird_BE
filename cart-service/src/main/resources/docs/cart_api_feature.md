# Cart API 기능 및 데이터 형식 정리

## 1. 전체 API 요약

| 기능 | 통신 타입 | URL | 핵심 동작 |
|---|---|---|---|
| 장바구니 항목 추가 | `POST` | `/users/{userId}/cart/items` | 여러 Reward를 추가하며, 기존 Reward는 수량을 증가시킨다. |
| 내 장바구니 조회 | `GET` | `/users/{userId}/cart` | Cart의 전체 항목과 프로젝트별 금액 정보를 반환한다. |
| 장바구니 항목 수정 | `PATCH` | `/users/{userId}/cart/items` | 기존 Reward는 수량을 교체하고, 없는 Reward는 새로 추가한다. |
| 특정 Reward 삭제 | `DELETE` | `/users/{userId}/cart/items/{rewardId}` | 지정한 Reward에 해당하는 CartItem만 삭제한다. |
| 장바구니 전체 비우기 | `DELETE` | `/users/{userId}/cart` | CartItem을 모두 삭제하고 Cart 엔티티는 유지한다. |

> `POST`와 `PATCH`는 서로 다른 여러 `rewardId`를 한 요청에서 처리할 수 있다. 다만 동일 요청 안에 같은 `rewardId`가 중복되면 병합하지 않고 요청을 거부 -> 추후 정책 변경 시 바뀔수 있음

---

## 2. 공통 통신 규칙

### 2.1 공통 성공 Response Wrapper

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `success` | `Boolean` | 요청 성공 여부 |
| `data` | `Object` 또는 `null` | 실제 응답 데이터 |
| `error` | `Object` 또는 `null` | 성공 시 `null` |

### 2.2 공통 실패 Response Wrapper

```json
{
  "success": false,
  "data": null,
  "error": {
    "message": "오류 메시지",
    "errors": null
  }
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `success` | `Boolean` | 실패 시 `false` |
| `data` | `null` | 실패 시 데이터 없음 |
| `error.message` | `String` | 오류 메시지 |
| `error.errors` | `Object` 또는 `null` | 세부 오류 정보. 현재 수동 검증 오류는 일반적으로 `null`이다. |

---

# 3. 장바구니 항목 추가

## 3.1 통신 정보

| 항목 | 내용 |
|---|---|
| 통신 타입 | `POST` |
| URL | `/users/{userId}/cart/items` |
| Request Body | 있음 |
| 성공 상태 코드 | `200 OK` |
| Response Data | 변경 후 전체 Cart 정보 |

## 3.2 기능 설명

한 번의 요청으로 여러 Reward를 Cart에 추가한다.

- Cart에 존재하지 않는 Reward는 새로운 `CartItem`으로 추가된다.
- Cart에 이미 존재하는 Reward는 별도 행을 생성하지 않고 기존 수량에 요청 수량을 더한다.
- 서로 다른 여러 Reward를 한 번에 처리할 수 있다.
- 요청 전체를 검증한 후 Cart 변경을 수행한다.
- 동일 요청 안에 같은 `rewardId`가 두 번 이상 포함되면 요청을 거부한다.

### 기존 Reward 수량 처리

```text
변경 후 수량 = 기존 수량 + 요청 수량
```

예시:

```text
기존 수량: 2
요청 수량: 3
최종 수량: 5
```

## 3.3 Path Variable

| 이름 | 타입 | 설명 |
|---|---:|---|
| `userId` | `Long` | Cart를 소유한 사용자 ID |

## 3.4 Request Body 형식

Request DTO: `AddCartItemsRequest`

```json
{
  "projectId": 10,
  "items": [
    {
      "rewardId": 101,
      "quantity": 2
    },
    {
      "rewardId": 102,
      "quantity": 1
    }
  ]
}
```

### Request Field

| 필드 | 타입 | 필수 여부 | 검증 및 의미 |
|---|---|---|---|
| `projectId` | `Long` | 필수 | Reward가 속한 프로젝트 ID. `null` 불가 |
| `items` | `List<AddCartItemRequest>` | 필수 | 추가할 Reward 목록. `null` 또는 빈 배열 불가 |
| `items[].rewardId` | `Long` | 필수 | Reward ID. `null` 불가 |
| `items[].quantity` | `Integer` | 필수 | 추가할 수량. `1` 이상이어야 한다. |

### Request 처리 제약

- 요청 내 `rewardId`는 서로 달라야 한다.
- `quantity`는 양수여야 한다.
- 기존 수량과 요청 수량을 합한 최종 수량은 최대 허용 수량을 초과할 수 없다. -> 정책에 따라 변경 가능
- 현재 검토된 기준의 최대 수량은 `99`이다. -> 정책에 따라 변경 가능

## 3.5 Response Body 형식

Response DTO: `CartResponse`

```json
{
  "success": true,
  "data": {
    "cartId": 1,
    "userId": 1,
    "itemCount": 2,
    "items": [
      {
        "rewardId": 101,
        "quantity": 2
      },
      {
        "rewardId": 102,
        "quantity": 1
      }
    ],
    "projects": [
      {
        "projectId": 10,
        "projectName": "Example Project",
        "rewards": [
          {
            "cartItemId": 1,
            "rewardId": 101,
            "rewardName": "White Round",
            "quantity": 2,
            "unitPrice": 7500,
            "totalPrice": 15000
          }
        ],
        "itemsAmount": 15000,
        "shippingFee": 3000,
        "totalAmount": 18000
      }
    ],
    "totalItemsAmount": 15000,
    "totalShippingFee": 3000,
    "totalAmount": 18000
  },
  "error": null
}
```

response body의 경우 현재 기준으론 그렇고 추후 변경 가능한 부분

---

# 4. 내 장바구니 조회

## 4.1 통신 정보

| 항목 | 내용 |
|---|---|
| 통신 타입 | `GET` |
| URL | `/users/{userId}/cart` |
| Request Body | 없음 |
| 성공 상태 코드 | `200 OK` |
| Response Data | 사용자의 전체 Cart 정보 |

## 4.2 기능 설명

사용자에게 할당된 Cart와 모든 CartItem을 조회한다.

- Cart에 포함된 모든 Reward와 수량을 반환한다.
- Reward 외부 조회 결과를 이용하여 프로젝트별 Reward 정보를 구성한다.
- 프로젝트별 상품 금액, 배송비 및 총금액을 반환한다.
- 모든 사용자에게 Cart가 기본 할당되어 있으므로 빈 Cart도 정상 조회된다.

## 4.3 Path Variable

| 이름 | 타입 | 설명 |
|---|---:|---|
| `userId` | `Long` | 조회할 Cart의 사용자 ID |

## 4.4 Request 형식

Request Body는 없다.


## 4.5 일반 Response Body 형식

```json
{
  "success": true,
  "data": {
    "cartId": 1,
    "userId": 1,
    "itemCount": 2,
    "items": [
      {
        "rewardId": 101,
        "quantity": 2
      },
      {
        "rewardId": 102,
        "quantity": 1
      }
    ],
    "projects": [
      {
        "projectId": 10,
        "projectName": "Example Project",
        "rewards": [
          {
            "cartItemId": 1,
            "rewardId": 101,
            "rewardName": "White Round",
            "quantity": 2,
            "unitPrice": 7500,
            "totalPrice": 15000
          }
        ],
        "itemsAmount": 15000,
        "shippingFee": 3000,
        "totalAmount": 18000
      }
    ],
    "totalItemsAmount": 15000,
    "totalShippingFee": 3000,
    "totalAmount": 18000
  },
  "error": null
}
```

## 4.6 빈 Cart Response Body 형식

Cart 자체는 존재하지만 CartItem이 하나도 없는 경우이다.

```json
{
  "success": true,
  "data": {
    "cartId": 1,
    "userId": 1,
    "itemCount": 0,
    "items": [],
    "projects": [],
    "totalItemsAmount": 0,
    "totalShippingFee": 0,
    "totalAmount": 0
  },
  "error": null
}
```

## 4.7 주요 Response Field

| 필드 | 타입 | 설명 |
|---|---|---|
| `cartId` | `Long` | Cart ID |
| `userId` | `Long` | Cart 소유 사용자 ID |
| `itemCount` | `Integer` | Cart에 포함된 항목 수 |
| `items` | `List` | Reward ID와 수량 중심의 CartItem 목록 |
| `projects` | `List` | 프로젝트별로 그룹화된 Reward 상세 정보 |
| `totalItemsAmount` | 숫자형 | 전체 Reward 상품 금액 합계 |
| `totalShippingFee` | 숫자형 | 전체 배송비 합계 |
| `totalAmount` | 숫자형 | 상품 금액과 배송비를 포함한 전체 금액 |


---

# 5. 장바구니 항목 수정

## 5.1 통신 정보

| 항목 | 내용 |
|---|---|
| 통신 타입 | `PATCH` |
| URL | `/users/{userId}/cart/items` |
| Request Body | 있음 |
| 성공 상태 코드 | `200 OK` |
| Response Data | 변경 후 전체 Cart 정보 |

## 5.2 기능 설명

한 번의 요청으로 여러 Reward의 수량을 변경한다.

- 기존 Cart에 존재하는 Reward는 요청 수량으로 교체된다.
- 기존 Cart에 존재하지 않는 Reward는 새 CartItem으로 추가된다.
- 기존 Reward와 신규 Reward를 하나의 요청에 함께 포함할 수 있다.
- 동일 요청 안에 같은 `rewardId`가 중복되면 요청을 거부한다.

### 기존 Reward 수량 처리

```text
변경 후 수량 = 요청 수량
```

예시:

```text
기존 수량: 2
요청 수량: 5
최종 수량: 5
```

POST처럼 기존 수량에 더하지 않는다.

## 5.3 Path Variable

| 이름 | 타입 | 설명 |
|---|---:|---|
| `userId` | `Long` | Cart를 소유한 사용자 ID |

## 5.4 Request Body 형식

Request DTO: `UpdateCartItemsRequest`

```json
{
  "projectId": 10,
  "items": [
    {
      "rewardId": 101,
      "quantity": 5
    },
    {
      "rewardId": 102,
      "quantity": 3
    }
  ]
}
```

### Request Field

| 필드 | 타입 | 필수 여부 | 검증 및 의미 |
|---|---|---|---|
| `projectId` | `Long` | 필수 | Reward가 속한 프로젝트 ID. `null` 불가 |
| `items` | `List<UpdateCartItemRequest>` | 필수 | 수정할 Reward 목록. `null` 또는 빈 배열 불가 |
| `items[].rewardId` | `Long` | 필수 | Reward ID. `null` 불가 |
| `items[].quantity` | `Integer` | 필수 | 최종 적용할 수량. `1` 이상이어야 한다. |

### Request 처리 제약

- 같은 요청의 `rewardId`는 서로 달라야 한다.
- `quantity`는 `null`, `0`, 음수가 될 수 없다.
- 요청 수량은 최대 허용 수량 `99`를 초과할 수 없다. -> 최대 허용 수량의 경우 위에서 적은 것처럼 필요없다 판단되면 빼도 됨.

## 5.5 Response Body 형식

변경된 항목만 반환하는 것이 아니라 변경 후 전체 `CartResponse`를 반환한다.
-> 변경 가능

```json
{
  "success": true,
  "data": {
    "cartId": 1,
    "userId": 1,
    "itemCount": 2,
    "items": [
      {
        "rewardId": 101,
        "quantity": 5
      },
      {
        "rewardId": 102,
        "quantity": 3
      }
    ],
    "projects": [
      {
        "projectId": 10,
        "projectName": "Example Project",
        "rewards": [
          {
            "cartItemId": 1,
            "rewardId": 101,
            "rewardName": "White Round",
            "quantity": 5,
            "unitPrice": 7500,
            "totalPrice": 37500
          }
        ],
        "itemsAmount": 37500,
        "shippingFee": 3000,
        "totalAmount": 40500
      }
    ],
    "totalItemsAmount": 37500,
    "totalShippingFee": 3000,
    "totalAmount": 40500
  },
  "error": null
}
```

## 5.6 검토 결과

- 서로 다른 다수 Reward 수정: **정상**
- 기존 Reward 수량 교체: **정상**
- 존재하지 않는 Reward 추가: **정상**
- 기존 Reward와 신규 Reward 혼합 처리: **정상**
- 같은 요청 안의 중복 `rewardId`: **요청 거부**

---

# 6. 특정 Reward 삭제

## 6.1 통신 정보

| 항목 | 내용 |
|---|---|
| 통신 타입 | `DELETE` |
| URL | `/users/{userId}/cart/items/{rewardId}` |
| Request Body | 없음 |
| 성공 상태 코드 | `200 OK` |
| Response Data | 삭제 후 남아 있는 전체 Cart 정보 |

## 6.2 기능 설명

사용자의 Cart에서 URL에 지정된 `rewardId`에 해당하는 CartItem만 삭제한다.

- 지정된 Reward만 삭제된다.
- 다른 Reward는 유지된다.
- 삭제 후 남아 있는 전체 Cart 상태를 반환한다.
- 존재하지 않는 Reward를 삭제하려 하면 `400` 오류가 발생한다.

## 6.3 Path Variable

| 이름 | 타입 | 설명 |
|---|---:|---|
| `userId` | `Long` | Cart 소유 사용자 ID |
| `rewardId` | `Long` | 삭제할 Reward ID |

## 6.4 Request 형식

Request Body는 없다.


## 6.5 Response Body 형식

```json
{
  "success": true,
  "data": {
    "cartId": 1,
    "userId": 1,
    "itemCount": 1,
    "items": [
      {
        "rewardId": 102,
        "quantity": 1
      }
    ],
    "projects": [
      {
        "projectId": 10,
        "projectName": "Example Project",
        "rewards": [
          {
            "cartItemId": 2,
            "rewardId": 102,
            "rewardName": "Remaining Reward",
            "quantity": 1,
            "unitPrice": 10000,
            "totalPrice": 10000
          }
        ],
        "itemsAmount": 10000,
        "shippingFee": 3000,
        "totalAmount": 13000
      }
    ],
    "totalItemsAmount": 10000,
    "totalShippingFee": 3000,
    "totalAmount": 13000
  },
  "error": null
}
```

## 6.6 검토 결과

- 지정 Reward만 삭제: **정상**
- 다른 CartItem 유지: **정상**
- DB CartItem 행 삭제: **정상**
- 삭제 후 전체 Cart 반환: **정상**
- 존재하지 않는 Reward 삭제: **`400 Bad Request`**

---

# 7. 장바구니 전체 비우기

## 7.1 통신 정보

| 항목 | 내용 |
|---|---|
| 통신 타입 | `DELETE` |
| URL | `/users/{userId}/cart` |
| Request Body | 없음 |
| 성공 상태 코드 | `200 OK` |
| Response Data | `null` |

## 7.2 기능 설명

사용자의 Cart에 포함된 모든 CartItem을 삭제한다.


## 7.3 Path Variable

| 이름 | 타입 | 설명 |
|---|---:|---|
| `userId` | `Long` | 비울 Cart의 사용자 ID |

## 7.4 Request 형식

Request Body는 없다.

## 7.5 Response Body 형식

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

## 7.6 삭제 후 예상 조회 결과

전체 삭제 후 `GET /users/{userId}/cart`를 호출하면 Cart 엔티티는 유지되므로 다음과 같은 빈 Cart가 반환된다.

```json
{
  "success": true,
  "data": {
    "cartId": 1,
    "userId": 1,
    "itemCount": 0,
    "items": [],
    "projects": [],
    "totalItemsAmount": 0,
    "totalShippingFee": 0,
    "totalAmount": 0
  },
  "error": null
}
```

---

# 8. POST와 PATCH 수량 처리 차이

| 구분 | 기존 Reward가 존재하는 경우 | 기존 Reward가 없는 경우 |
|---|---|---|
| `POST /cart/items` | 기존 수량에 요청 수량을 더한다. | 요청 수량으로 신규 CartItem을 추가한다. |
| `PATCH /cart/items` | 기존 수량을 요청 수량으로 교체한다. | 요청 수량으로 신규 CartItem을 추가한다. |

예시:

```text
현재 수량: 2
POST 요청 수량: 3  → 최종 수량 5
PATCH 요청 수량: 3 → 최종 수량 3
```

---

# 9. 주의사항

## 9.1 동일 요청 내부의 중복 Reward

다음과 같은 요청은 지원하지 않는다.

```json
{
  "projectId": 10,
  "items": [
    {
      "rewardId": 101,
      "quantity": 2
    },
    {
      "rewardId": 101,
      "quantity": 3
    }
  ]
}
```

현재 동작:

- POST에서 `2 + 3`으로 병합하지 않는다.
- PATCH에서 마지막 수량을 적용하지 않는다.
- 요청을 검증 단계에서 거부한다.
- 요청 전체가 실패하며 일부 항목만 저장되는 형태로 처리되지 않는다.


---

# 10. 최종 결론

1. `POST /users/{userId}/cart/items`는 서로 다른 여러 Reward를 정상 저장한다.
2. POST에 기존 Reward가 포함되면 기존 수량에 요청 수량을 더한다.
3. `GET /users/{userId}/cart`는 Cart의 전체 내역을 정상 반환한다.
4. 빈 Cart는 오류가 아니라 빈 배열과 금액 `0`으로 반환된다.
5. `PATCH /users/{userId}/cart/items`는 서로 다른 여러 Reward를 정상 수정한다.
6. PATCH에 기존 Reward가 포함되면 수량을 요청 값으로 교체한다.
7. PATCH에 기존에 없던 Reward가 포함되면 새로운 CartItem으로 추가한다.
8. 특정 Reward 삭제는 해당 Reward만 삭제하고 나머지 CartItem을 유지한다.
9. POST와 PATCH 모두 동일 요청 내부의 중복 `rewardId`는 허용하지 않는다.
