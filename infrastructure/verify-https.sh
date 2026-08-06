#!/usr/bin/env bash
# 배포된 gateway-server 의 HTTPS(Caddy) 설정이 올바로 동작하는지 확인한다 (이슈 #105 DoD).
# EC2 배포 후 수동으로 실행 - 실제 공인 인증서 발급/도메인 라우팅은 로컬 테스트로는 검증할 수 없다.
#
# 사용법: ./verify-https.sh [도메인]

set -euo pipefail

DOMAIN="${1:-earlybird-team5.duckdns.org}"
FAIL=0

echo "1) HTTP -> HTTPS 리다이렉트 확인"
redirect_status=$(curl -s -o /dev/null -w '%{http_code}' "http://${DOMAIN}/")
if [[ "$redirect_status" == 3* ]]; then
  echo "   OK ($redirect_status)"
else
  echo "   FAIL: http://${DOMAIN}/ 이 리다이렉트(3xx)가 아님 (받은 상태: $redirect_status)"
  FAIL=1
fi

echo "2) HTTPS 인증서 체인 유효성 확인"
# curl 은 기본적으로(-k 없이) 인증서 체인이 신뢰되지 않으면 연결 자체를 실패시킨다(SSL 관련 종료 코드).
# -f 를 쓰지 않는다 - 여기서는 TLS 계층 성공 여부만 확인하며, 라우팅된 서비스의 HTTP 응답 코드
# (4xx/5xx 포함, 예: 인증 헤더 누락으로 인한 400)는 이 체크와 무관하다.
if curl -s -o /dev/null "https://${DOMAIN}/"; then
  echo "   OK: TLS 핸드셰이크 성공, 인증서 체인 신뢰됨"
else
  curl_exit=$?
  echo "   FAIL: HTTPS 연결 자체가 실패함 (curl 종료 코드: $curl_exit - 인증서/네트워크 문제)"
  FAIL=1
fi

echo "3) 인증서 발급자 / 유효기간"
echo | openssl s_client -connect "${DOMAIN}:443" -servername "${DOMAIN}" 2>/dev/null \
  | openssl x509 -noout -issuer -dates

exit $FAIL
