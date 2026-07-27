# Settlement service

## 토스 지급대행 테스트 환경 smoke test

`tossPayoutSmokeTest`는 토스페이먼츠 테스트 환경의 `POST /v2/payouts`를 실제로 호출하는 opt-in 테스트다. 기본 `test` 태스크와 CI에서는 실행하지 않는다. 라이브 키는 애플리케이션 설정 검증에서 거부한다.

실행 전에 다음 조건을 준비한다.

- 지급대행 계약이 연결된 `test_sk` 테스트 시크릿 키
- 같은 상점의 64자리 16진수 보안 키
- 지급 가능한 테스트 법인 셀러의 토스 `id`
- 현재 가용 잔액 이하의 양수 지급액
- 실행일 다음 날 오전 9시 이후부터 1년 이내에 있는 미래 영업일

민감한 값은 파일이나 명령 이력에 기록하지 말고 IDE 또는 비밀 저장소에서 아래 환경 변수로 주입한다.

| 환경 변수 | 값 |
|---|---|
| `TOSS_PAYOUT_SMOKE_SECRET_KEY` | 테스트 시크릿 키 |
| `TOSS_PAYOUT_SMOKE_SECURITY_KEY` | 64자리 16진수 보안 키 |
| `TOSS_PAYOUT_SMOKE_SELLER_ID` | 테스트 셀러 `id` |
| `TOSS_PAYOUT_SMOKE_PAYOUT_DATE` | `yyyy-MM-dd` 지급 예정일 |
| `TOSS_PAYOUT_SMOKE_AMOUNT` | KRW 지급액 |

작업공간 루트에서 다음 명령을 실행한다.

```shell
./gradlew :settlement-service:tossPayoutSmokeTest
```

테스트는 암호화된 예약 지급 요청이 `REQUESTED`로 접수되고 토스 지급 식별자가 반환되는지를 확인한다. 필수 환경 변수가 없으면 명시적인 오류로 종료되며, 시크릿 키·보안 키·암호화 전 요청 본문은 출력하지 않는다.
