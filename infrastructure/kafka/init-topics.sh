#!/bin/sh
# 토픽 생성을 앱 코드에서 완전히 분리해 이 스크립트 하나로 중앙화한다.
# kafka-topics-init 컨테이너가 kafka(healthy) 뒤에 실행되도록 docker-compose 쪽에서
# depends_on: condition: service_healthy 로 순서를 보장하므로, 여기선 브로커 준비를
# 기다리는 재시도 로직이 필요 없다.
set -e

BOOTSTRAP=kafka:29092
PARTITIONS=3
REPLICATION_FACTOR=1

# 카프카 이벤트/커맨드 명세 기준 토픽 5개 + 각각의 DLT(Dead Letter Topic).
# DLT도 auto.create.topics.enable=false 아래에서는 명시적으로 만들어둬야 한다 —
# 브로커는 DeadLetterPublishingRecoverer의 발행 요청과 일반 발행 요청을 구분하지 않는다.
TOPICS="
project.status-changed.v1
project.status-changed.v1.DLT
order.payment-status-changed.v1
order.payment-status-changed.v1.DLT
payment.single-result.v1
payment.single-result.v1.DLT
payment.bulk-cancel-command.v1
payment.bulk-cancel-command.v1.DLT
payment.bulk-cancel-result.v1
payment.bulk-cancel-result.v1.DLT
"

for topic in $TOPICS; do
  echo "creating topic: $topic"
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
    --topic "$topic" --partitions "$PARTITIONS" --replication-factor "$REPLICATION_FACTOR"
done

echo "토픽 생성 완료"