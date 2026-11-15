# 종료 구간 WebSocket 메시지 거부 진단

2026-11-16. 정밀도 적용 후 200명 실행에서 거부 26건은 00:25:45.673 KST까지 0이었다가,
00:25:46.686에 연결·접속자가 모두 0인 표본에서 나타났다. 클라이언트 오류·조기 종료는 0이었다.

기존 `acceptMessage`는 채널이 이미 제거된 경우와 초당 30개 제한을 넘은 경우에 모두 false를 반환했다.
핸들러는 둘 다 RATE_LIMIT으로 처리하고 동일한 거부 카운터만 증가시켰다.
따라서 최종 MOVE와 연결 정리가 겹치면 실제 빈도 초과 없이도 거부가 집계되는 경로가 있었다.
기존 실행의 26건이 전부 이 경로였는지는 과거 지표만으로 확정할 수 없다.

## 변경 및 검증

- 입력 판정을 ACCEPTED / CONNECTION_CLOSED / RATE_LIMIT으로 분리했다.
- 이미 제거됐거나 실제로 닫힌 연결의 메시지는 CONNECTION_CLOSED로 집계하고 재종료하지 않는다.
- 실제 입력 제한 초과는 기존처럼 1008 RATE_LIMIT으로 종료한다.
- 기존 `festival_websocket_messages_total{result="rejected",type="invalid"}` 합계 지표는 유지한다.
- 사유별 `festival_websocket_rejections_total{reason="..."}`를 추가했다. 프로토콜 예외도 별도로 구분한다.
- 라벨은 고정된 코드만 사용하며 알 수 없는 값은 OTHER로 묶는다. ID·JWT·본문·임의 예외 문자열을 라벨에 넣지 않는다.
- 연결 정리 직후 마지막 MOVE, 닫혔지만 아직 맵에 남은 소켓, 실제 초당 제한 초과,
  인증 상태 경합·알 수 없는 사유를 테스트했다. 관련 26개 테스트와 bootJar 빌드 통과.

## 실제 재확인

실행 중인 사용자 8080 JVM에는 재시작 후 반영된다. 진단 수정 후 200명 재실행은 아직 하지 않았다.
전체 거부를 합계만 보지 않고 실행 전후 아래 카운터 차이를 확인한다.

```promql
festival_websocket_rejections_total
increase(festival_websocket_rejections_total[5m])
```

CONNECTION_CLOSED만 종료 구간에 늘고 RATE_LIMIT이 늘지 않으면 이번 재실행에서는 종료된 연결의
늦은 메시지임을 구분할 수 있다. AUTH_REQUIRED 등 다른 사유가 늘면 해당 경로를 따로 조사한다.
과거 거부 수치를 삭제하거나 오류 0건으로 다시 쓰지 않는다. 전송량·정밀도·주기는 변경하지 않았다.
