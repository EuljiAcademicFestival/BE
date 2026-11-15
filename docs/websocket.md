# 3D 학술제 WebSocket 명세

## 범위와 실행

같은 3D 공간에 접속한 사용자의 입장·퇴장·위치·방향을 동기화한다.
기본 공간은 `festival` 하나, 인증된 회원 최대 200명, 이동 전송은 클라이언트당 초당 10회다.
기존 로그인·방명록·좋아요·순위 REST API는 유지한다.

- 개발 연결: `ws://localhost:8080/ws/festival`
- HTTPS 배포: `wss://서버도메인/ws/festival`
- 로그인 후 받은 기존 액세스 JWT를 사용한다. 토큰을 URL 쿼리에 넣지 않는다.
- 브라우저 기본 WebSocket API로 연결하며 STOMP·SockJS는 사용하지 않는다.
- 새로운 의존성이 있으므로 IntelliJ Gradle 동기화와 서버 재시작이 필요하다.
- Swagger UI는 REST 테스트용이다. WebSocket은 프런트엔드 클라이언트나 별도 스크립트로 테스트한다.

## 연결과 인증

연결 직후 서버:

```json
{"type":"AUTH_REQUIRED","data":{"timeoutMs":5000}}
```

클라이언트가 5초 이내 전송:

```json
{"type":"AUTH","accessToken":"로그인으로 받은 JWT"}
```

서버는 JWT 서명·용도·만료를 검증하고 회원 프로필을 보통 한 번 조회한다.
입장과 프로필 수정 커밋이 겹치면 이전 프로필을 전파하지 않도록 최대 3회까지 조회를 재시도한다.
인증 전에는 다른 접속자의 상태를 받거나 이동할 수 없다. 같은 회원의 두 번째 연결은 거부한다.
주기적인 JWT 만료 검증은 로컬 검증이며 DB를 조회하지 않는다. 만료 후 약 1초 이내 연결을 종료한다.
JWT 정책상 접속 후 회원 삭제가 즉시 반영되는 구조는 아니며 별도 강제 퇴장·폐기 기능은 없다.

성공 응답 예시:

```json
{
  "type":"WELCOME",
  "data":{
    "memberId":15,
    "roomId":"festival",
    "serverTime":1794730000000,
    "players":[
      {"identity":{"memberId":15,"characterId":3,"name":"김을지","grade":"2학년"},
       "sequence":-1,"pose":{"x":0,"y":0,"z":0,"yaw":0}}
    ]
  }
}
```

`players`는 본인과 기존 접속자 전원이다. 신규 접속자의 기본 좌표는 원점이다.
실제 맵의 스폰·지면·충돌 처리는 프런트엔드에서 구현하고 유효한 첫 위치를 보내야 한다.
`serverTime`은 UTC epoch milliseconds, `yaw`는 라디안이다.

## 입장·퇴장·프로필

다른 접속자에게 신규 회원 입장:

```json
{"type":"PLAYER_JOINED","data":{"identity":{"memberId":16,"characterId":2,"name":"홍길동","grade":"게스트"},"sequence":-1,"pose":{"x":0,"y":0,"z":0,"yaw":0}}}
```

접속 종료·시간 초과·JWT 만료 시:

```json
{"type":"PLAYER_LEFT","data":{"memberId":16}}
```

REST 프로필 변경이 커밋되면 모든 인증된 접속자에게:

```json
{"type":"PLAYER_UPDATED","data":{"memberId":16,"characterId":6,"name":"새이름","grade":"졸업생"}}
```

롤백된 수정은 전파하지 않는다. 같은 프로필로 방명록 본문만 수정하면 프로필 이벤트는 발생하지 않는다.
프런트엔드는 회원 ID를 키로 캐릭터를 관리하고 입장·퇴장·프로필 이벤트를 적용한다.

## 위치·방향

클라이언트가 이동 중 100ms 간격으로 전송:

```json
{"type":"MOVE","sequence":1,"x":3.2,"y":0,"z":-4.8,"yaw":1.57}
```

- `sequence`는 연결마다 0부터 시작하는 증가 정수다. 이전·중복 sequence는 무시한다.
- 본인 ID는 서버가 연결에 결합한다. MOVE에 `memberId`나 다른 필드를 추가하면 거부한다.
- 좌표·방향은 유한한 숫자여야 한다. 좌표의 절댓값은 기본 1000 이하이며 `yaw`는 서버가 정규화한다.
- 서버가 보내는 `WELCOME`·`PLAYER_JOINED`·`STATE`의 `pose`는 x·y·z·yaw 모두 소수점 최대 3자리로 반올림한다. 숫자 타입이며 불필요한 끝자리 0은 붙이지 않는다. 반올림 오차는 축별 최대 0.0005, yaw는 최대 0.0005라디안이다.
- 정밀도는 거리와 무관하게 동일하다. 서버 내부 원본 좌표는 유지하며, 모든 접속자의 위치와 기존 전송 주기를 유지한다. 클라이언트가 보내는 MOVE를 미리 반올림할 필요는 없다.
- 서버는 변경된 상태를 100ms 고정 지연 주기로 모아 전송한다. 서버 부하에 따라 실제 간격은 늘 수 있다.
- 클라이언트는 렌더링 프레임마다 보간해 움직임을 부드럽게 표현한다. 렌더링 FPS와 네트워크 주기는 다르다.
- 이동을 멈출 때 최종 위치도 전송한다. 위치가 변하지 않으면 서버가 계속 STATE를 전송하지는 않는다.

서버 위치 프레임:

```json
{"type":"STATE","data":{"revision":42,"serverTime":1794730000100,"players":[{"memberId":15,"sequence":1,"pose":{"x":3.2,"y":0,"z":-4.8,"yaw":1.57}}]}}
```

STATE의 players는 해당 시점의 **전체 접속자 위치**다. 변경된 회원만 담는 delta가 아니다.
프로필 문자열을 매 위치 프레임마다 반복하지 않는다. 프런트엔드는 STATE로 알려진 캐릭터의 위치를 갱신하고,
최신 revision·sequence보다 오래된 값은 적용하지 않는다. 캐릭터 생성·제거는 입장·퇴장 이벤트로 처리한다.
느린 수신자에게 아직 보내지 못한 위치 프레임은 최신 전체 프레임으로 교체한다.

## 좋아요 수·순위 실시간 반영

좋아요 등록·취소 요청은 기존 REST `PUT/DELETE /api/guestbook/{memberId}/like`로 처리한다.
트랜잭션 커밋 후 실제 변경이 발생한 회원 ID만 메모리에 기록하며, 기본 200ms 간격으로 합친다.
중복 등록·이미 취소된 요청과 롤백된 변경은 전파하지 않는다. 커밋 콜백 안에서 DB 재조회나 소켓 전송을 하지 않는다.

서버는 한 배치에서 변경 회원의 최신 좋아요 수와 1~3위 전원의 순위를 **SELECT 한 문장**으로 읽는다.
동점자가 3명보다 많아도 전원 포함한다. 읽기가 끝나 DB 연결을 반환한 뒤 인증된 접속자에게 전송한다.
같은 회원이 배치 안에서 여러 번 바뀌면 중간 값 대신 조회 시점의 최신 값을 보낸다.

```json
{
  "type":"ENGAGEMENT_STATE",
  "data":{
    "revision":12,
    "serverTime":1794730000200,
    "full":false,
    "counts":[{"memberId":15,"likeCount":6}],
    "rankings":[{"memberId":15,"grade":"2학년","name":"김을지","guestbookMessage":"환영합니다","likeCount":6,"rank":1}]
  }
}
```

- `counts`: 좋아요 수의 **절댓값**이다. 클라이언트에서 +1/-1을 누적하지 말고 받은 값으로 교체한다.
  일반 배치에는 변경 회원과 현재 순위권 회원이 들어갈 수 있다.
- `rankings`: 항상 현재 1~3위 전원의 **전체 목록**이다. 기존 순위 목록을 교체한다.
  이전에 순위가 있었더라도 이 목록에 없으면 상세·목록의 순위를 null로 지워야 한다.
- `likedByMe`는 포함하지 않는다. 버튼을 누른 회원은 기존 REST 응답의 `likedByMe`로 본인 선택 상태만 갱신한다.
- 알림창이나 토스트를 띄우기 위한 메시지가 아니다. 방명록·상세·중앙 순위의 표시 데이터를 갱신한다.
- `revision`은 좋아요·순위 프레임 전용 증가 번호다. 같은 연결에서 이미 적용한 번호 이하의 프레임은 무시한다.
  위치 STATE의 revision과 별개이며, 재연결 시 이전 연결의 비교 번호를 초기화한다.
- 인기순 목록은 좋아요 수가 바뀌면 정렬·페이지 경계도 달라진다. 표시 값은 갱신하되 순서 재조회가 필요하면
  기존 커서를 초기화해 REST로 첫 페이지부터 다시 받는다. WebSocket 프레임으로 커서를 새로 만들지 않는다.

### 최초 접속과 재동기화

클라이언트는 REST로 목록·상세 등 초기 화면 데이터와 개인별 선택 상태를 읽는다.
WebSocket의 AUTH 성공 후에는 서버가 전체 counts와 순위 목록을 한 번 조회해 `full:true`로 전송한다.
화면 렌더링 시 수신한 공통 counts·rankings를 초기 REST 데이터에 적용한다.
실시간 프레임의 revision으로 공통 상태를 관리하고, 늦게 도착한 초기 REST 응답으로 이를 덮어쓰지 않는다.

최초 동기화만 필요한 경우 결과는 해당 연결에만 보낸다. 같은 배치에 좋아요 변경도 있으면
전체 스냅샷을 공통 갱신 프레임으로 전송할 수 있다. WebSocket으로 개인별 좋아요 상태를 동기화하지 않는다.
재연결하면 REST로 `likedByMe`를 다시 확인하고 AUTH·WELCOME·전체 ENGAGEMENT_STATE부터 처리한다.
연결 중 명시적으로 전체 공통 상태를 다시 받고 싶다면 다음 메시지를 보낸다.

```json
{"type":"SYNC_ENGAGEMENT"}
```

배치는 고정 지연 200ms 기준이며 DB 대기·쿼리·전송 시간이 추가될 수 있다. 모든 화면이 같은 순간에
원자적으로 바뀌는 보장은 아니다. 버튼을 누른 본인은 REST 성공 응답으로 먼저 반영하고,
공통 숫자·순위는 이후 최신 스냅샷으로 일치시킨다.

SQL 조회가 실패하면 변경을 보존해 다음 배치에서 재시도한다. 대기 중 ID는 최대 2048개이며
그보다 많은 서로 다른 회원이 바뀌면 ID를 계속 쌓지 않고 전체 재동기화로 전환한다.
조회 중 새로 커밋된 변경도 다음 배치에 남긴다. 아무 변경·재동기화 요청이 없거나 인증된 접속자가 없으면
주기적인 DB 조회를 하지 않는다. 배치 작업은 위치 전송과 다른 스케줄러 스레드에서 실행할 수 있도록 구성했다.

ENGAGEMENT_STATE는 delta 누락을 피하기 위해 최신 위치 프레임처럼 임의로 버리지 않는다.
기존의 최대 16개 제어 메시지 큐와 1초 송신 제한을 적용하며, 느린 수신자는 종료 후 전체 동기화한다.
DB를 직접 수정한 값은 자동 감지하지 않는다. 필요한 경우 SYNC_ENGAGEMENT나 재연결로 다시 읽는다.

## 유휴 연결과 제한

움직이지 않아도 클라이언트는 약 10초마다 전송:

```json
{"type":"PING"}
```

서버 응답:

```json
{"type":"PONG","data":{"serverTime":1794730000000}}
```

- 45초간 유효한 MOVE·PING이 없으면 종료한다. 이전 sequence를 반복하는 MOVE는 활동으로 인정하지 않는다.
- 입력은 JSON 텍스트만 허용하며 최대 8192바이트, 연결별 기본 초당 30개 메시지다. 이동은 초당 10개를 권장한다.
- 인증 전 연결까지 포함한 상한은 `max-players + 32`다. 인증된 공간 정원은 별도로 200명이다.
- 연결별 미전송 제어 메시지는 최대 16개, 위치 프레임은 최신 1개만 보관한다.
- 실제 송신은 비동기이며 연결별 동시에 하나만 진행한다. 송신 제한 시간은 1초다.
- 잘못된 인증·메시지·좌표·과도한 빈도·만료는 1008, 정원·느린 수신자는 1013으로 종료한다.
  잘못된 JSON은 1007, 바이너리는 1003이다. 종료 reason은 `AUTH_INVALID`, `SPACE_FULL` 등 고정 코드다.
- 종료 시 재연결 여부를 판단한다. JWT가 만료됐으면 재로그인하고, 재연결 후 AUTH·WELCOME부터 다시 처리한다.

## 설정

| 환경변수 | 기본값 | 의미 |
| --- | --- | --- |
| WS_MAX_PLAYERS | 200 | 한 JVM의 공간 정원 |
| WS_COORDINATE_LIMIT | 1000 | x·y·z 절댓값 범위 |
| WS_MAX_MESSAGES_PER_SECOND | 30 | 연결별 입력 빈도 상한 |
| WS_ALLOWED_ORIGINS | localhost·127.0.0.1의 3000, 5173 HTTP origin | 허용할 브라우저 출처의 쉼표 목록 |

실제 프런트엔드 주소가 다르면 WS_ALLOWED_ORIGINS를 맞춘다. Docker 운영에서는 HTTPS 프런트엔드 origin과
WSS 업그레이드를 프록시에 설정해야 한다. Origin 검사는 JWT 인증을 대체하지 않는다.
미설정 기본값은 application.yml을 따른다. 기존 .env를 덮어쓸 필요는 없다.

## 브라우저 연결 예시

```javascript
const socket = new WebSocket('ws://localhost:8080/ws/festival');
let sequence = 0;
socket.onopen = () => socket.send(JSON.stringify({type:'AUTH', accessToken}));
socket.onmessage = ({data}) => {
  const event = JSON.parse(data);
  // WELCOME: 전체 캐릭터 생성, PLAYER_JOINED/LEFT/UPDATED: 명단 갱신
  // STATE: 알려진 캐릭터 위치를 저장하고 requestAnimationFrame에서 보간
  handlePresenceEvent(event);
};
// WELCOME 이후 이동 중 100ms마다 호출한다.
function sendPose({x,y,z,yaw}) {
  if (socket.readyState === WebSocket.OPEN)
    socket.send(JSON.stringify({type:'MOVE',sequence:sequence++,x,y,z,yaw}));
}
// WELCOME 이후 약 10초마다 PING. 연결 종료 시 타이머를 해제한다.
```

## 검증·부하테스트

실제 임의 포트 WebSocket 통합 테스트에서 입장·이동·퇴장, 인증 전 수신 차단, 잘못된 JWT·Origin·ID 위조,
중복 접속을 검증한다. 단위 테스트는 200명 상태 관리, 이동 중 identity 조회 없음, 만료·빈도·느린 수신자,
프로필 커밋·롤백을 검증한다. 이후 실제 200명 이동·좋아요 혼합 부하도 검증했다. 운영 환경의 장시간 안정성·최대 수용량은 별도 측정이 필요하다.

2026-11-15 검증: 실제 임의 포트 WebSocket 테스트와 분리된 MySQL의 기존 REST 회귀 테스트를 포함해
전체 72개 통과, 실패·오류·건너뜀 0개, bootJar 빌드 성공. 좋아요 배치·롤백·중복 요청·동점 순위와
실제 두 소켓의 공통 변경 수신까지 검증했다. k6 WebSocket 스크립트는 inspect로
로딩·설정을 확인했다. 이후 사용자 8080에서 200명·3분 혼합 부하를 완료했다.
이동 반영 p95 107ms, 좋아요 수 반영 p95 206ms, REST·클라이언트 오류와 좋아요 집계 불일치 0건이었다.
종료 구간 서버 메시지 거부 28건, 총 수신량 약 7.76GB와 측정 한계는
[실제 부하 결과](../k6/websocket-load-test-result.md)에 별도로 기록했다.

서버 재시작 후 먼저 2명으로 확인한다.

```bash
VUS=2 WS_DURATION_SECONDS=15 k6 run k6/websocket.js
```

그 다음 최대 200명, 초당 10회, 3분 유지:

```bash
VUS=200 WS_DURATION_SECONDS=180 k6 run k6/websocket.js
```

이동과 함께 VU당 초당 1회 좋아요 등록·취소를 번갈아 실행하려면:

```bash
VUS=200 WS_DURATION_SECONDS=180 WS_WITH_LIKES=true k6 run k6/websocket.js
```

`festival_ws_engagement_echo_ms`는 REST 좋아요 요청 시작부터 해당 숫자가 반영된 프레임을 받을 때까지의
시간 표본이다. 중복 요청처럼 실제 변경이 없으면 새 프레임이 없을 수 있어 모든 요청의 지연 표본은 아니다.

기존 k6 전용 계정으로 로그인한다. 계정이 없으면 개발 DB에 생성하므로 BASE_URL과 DB를 확인한다.
준비 로그인은 순차 실행이고 본 부하는 연결 유지·이동·상태 수신이다. 기본은 바로 지정한 VU로 연결한다.
기존 REST 혼합 부하와 다른 시나리오다. 결과의 setup_data는 제거해 토큰을 결과에 저장하지 않는다.
WS_ORIGIN으로 허용된 프런트엔드 origin을 맞출 수 있다. 토큰을 외부 테스트 사이트에 붙여넣지 않는다.

Grafana/Prometheus 지표:

```promql
festival_websocket_connections
festival_websocket_players
rate(festival_websocket_messages_total{result="accepted",type="MOVE"}[1m])
rate(festival_engagement_flushes_total{result="success"}[1m])
festival_engagement_flushes_total{result="failure"}
```

기존 HTTP 요청 지표는 WebSocket의 MOVE 횟수를 나타내지 않는다. k6의 join·이동 반영 지연과
앱 CPU·메모리·네트워크, 연결 종료·메시지 거부를 함께 확인한다.

## 현재 한계

- 한 Spring Boot JVM 안의 공간이다. 재시작하면 위치·접속 상태는 사라진다. Redis나 메시지 브로커는 추가하지 않았다.
- Docker 한 컨테이너로 실행할 수 있지만 앱 컨테이너를 여러 개로 복제하면 각자 다른 공간이 된다.
  여러 인스턴스가 같은 공간을 공유하려면 분산 상태·이벤트 전파 설계가 추가로 필요하다.
- 위치는 클라이언트가 제시한 값을 범위 검증해 공유한다. 이동 속도·맵 충돌·순간이동 방지 같은 권위 서버는 아니다.
- 모두 움직이면 전체 위치 스냅샷의 전송량이 커진다. 200명 실제 측정 후 필요하면 관심 영역·delta 전송을 검토한다.
- 좋아요 수·순위의 공통 상태는 실시간 반영하지만 쓰기 요청과 개인별 likedByMe는 기존 REST를 사용한다.
  방명록 본문 수정 등의 모든 데이터 변경을 실시간 전파하는 범위는 아니다.

참고: [Spring WebSocket](https://docs.spring.io/spring-framework/reference/6.2/web/websocket/server.html),
[k6 WebSockets](https://grafana.com/docs/k6/latest/using-k6/protocols/websockets/).
