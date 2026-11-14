# k6 로컬 부하 테스트

## 준비

k6 설치: `brew install k6`

앱, MySQL, Prometheus, Grafana를 실행합니다. Loki와 Promtail은 로그 분석 시 함께 실행합니다.
Prometheus의 `/targets`에서 `spring-actuator`가 UP인지 확인합니다.

스크립트는 `k6-user-1@loadtest.invalid`부터 최대 VU 수 + 1개의 전용 계정을 준비합니다.
가입한 계정은 다음 실행부터 재사용하고, 로그인 토큰은 실행마다 새로 받습니다.
실제 회원의 이메일·비밀번호는 필요하지 않습니다. 계정과 방명록은 테스트 후에도 남습니다.
혼합 테스트는 테스트 계정의 프로필·방명록과 테스트 계정 사이의 좋아요를 변경합니다.
중단 시 마지막 좋아요가 남을 수 있습니다. 가입·프로필 변경이 허용되는 테스트 전용 DB를 권장합니다.
서버가 연결한 DB에 데이터가 저장되므로 `BASE_URL`이 가리키는 앱의 DB 연결을 먼저 확인합니다.
운영 데이터에 테스트 사용자가 섞이면 목록과 순위에 표시됩니다.

`setup()`에서 회원가입·로그인하고, 본 부하는 조회와 좋아요·프로필 API에만 줍니다.
따라서 이 결과는 가입/로그인 폭주나 PBKDF2 처리량의 측정 결과가 아닙니다.

## 랜덤 더미데이터를 SQL로 미리 준비

Workbench의 개발 DB 연결에서 [seed-random-data.sql](seed-random-data.sql)을 열고 전체 실행합니다.
기본 DB 이름은 `eulji_festival`이며 다른 테스트 DB를 쓰면 파일의 `USE` 문을 바꿉니다.
기존 k6 실행을 중지한 상태에서 적용합니다. Flyway 마이그레이션에 넣지 않습니다.

- `k6-user-1@loadtest.invalid`부터 `k6-user-201@loadtest.invalid`까지 201명과 방명록을 생성합니다.
- k6와 같은 테스트 비밀번호를 사용하며 회원마다 별도의 salt를 가진 PBKDF2 해시를 저장합니다.
- 이름·캐릭터·학년·방명록은 분산되고 작성 시각은 실행 시각 기준 최근 7일 안으로 설정합니다.
- 인기 회원은 무작위로 뽑고, 좋아요는 140/100/100개로 상위 및 공동 순위를 재현합니다.
  나머지는 0~69개이며 하위 20명은 0개로 설정합니다. 누가 좋아요를 누르는지도 무작위입니다.
- 자기 좋아요와 중복 좋아요는 만들지 않으며 실제 기록 수로 `like_count`를 계산합니다.
- 재실행하면 지정한 테스트 계정의 프로필·방명록과 해당 계정 사이의 좋아요를 다시 설정합니다.
  일반 회원과의 좋아요 관계는 유지하므로 외부 좋아요가 있으면 총개수는 생성 목표보다 클 수 있습니다.
- SQL은 트랜잭션으로 묶습니다. 오류가 나면 계속 실행하지 말고 `ROLLBACK;`을 실행한 후
  `SET SESSION sql_safe_updates = @k6_previous_safe_updates;`로 세션 설정을 복구합니다.

결과에서 `test_member_count=201`, `test_guestbook_count=201`인지 확인하고,
마지막 좋아요 집계 불일치 쿼리의 결과가 0행인지 확인합니다.
그 다음 `PROFILE=capacity bash scripts/run-k6.sh`로 같은 계정을 재사용합니다.
혼합 부하의 프로필 수정과 좋아요 등록·취소로 데이터는 변경될 수 있으므로,
같은 초기 조건에서 비교하려면 테스트 사이에 SQL을 다시 실행합니다.
재현 가능한 분포가 필요하면 SQL의 `@k6_seed`를 UUID 대신 고정 문자열로 설정합니다.

## 1. 실행 확인: 1 VU, 15초

Backend 프로젝트 루트에서:

```bash
bash scripts/run-k6.sh
```

최초 준비 계정은 2개입니다. VU는 가상 사용자를 의미합니다.
한 VU가 여러 요청을 보내므로 10 VU는 초당 10요청이라는 뜻이 아닙니다.
기본 조회 요청 5개를 병렬로 보내고 1초 쉰 뒤 반복합니다.
다음 페이지 커서가 있을 때는 인기순의 다음 페이지도 조회합니다.

## 2. 조회 부하: 최대 10 VU

```bash
PROFILE=load VUS=10 bash scripts/run-k6.sh
```

부하 단계는 30초 동안 최대 5명, 다음 30초 동안 지정 VU까지 증가,
2분 유지, 30초 동안 0명으로 감소입니다. 정상 시나리오 길이는 3분 30초이며,
준비 시간과 진행 중 요청 정리 시간은 별도입니다.

조회 대상:

- 방명록 인기순·최신순·인기순 다음 페이지
- 캐릭터 이름표·상세
- 공동 순위를 포함한 상위 순위

## 3. 조회 + 좋아요 + 프로필 수정

```bash
PROFILE=load WORKLOAD=mixed VUS=10 bash scripts/run-k6.sh
```

각 VU는 별도 계정으로 조회 후 좋아요 등록·취소를 순서대로 수행하고,
10회 반복마다 본인 프로필을 수정합니다. 요청 성공 코드뿐 아니라 응답의 필드도 검사합니다.

## 4. 같은 방명록에 좋아요 집중

```bash
PROFILE=load WORKLOAD=hotspot VUS=10 bash scripts/run-k6.sh
```

여러 VU가 하나의 테스트 방명록에 좋아요 등록·취소를 보내며, 방명록 행 잠금 경합을 관찰합니다.
같은 계정을 모든 VU가 공유하는 방식이 아닙니다.
처음에는 10명부터 시작하고 25명, 50명, 100명처럼 결과를 보면서 늘립니다.
최대 VU 값은 예상 행사 동시 사용자에 맞춰 결정합니다.

## 5. 행사 목표: 동시 사용자 200명

목표는 전체 가입자 수가 아니라 로그인한 200명이 API를 함께 사용하는 상황입니다.

```bash
PROFILE=capacity bash scripts/run-k6.sh
```

기본 대상은 `http://127.0.0.1:8080`, 최대 200 VU, 워크로드는 `mixed`입니다.
테스트 계정과 방명록 201개를 준비하거나 재사용합니다. 200개 계정은 VU별로 사용하고,
나머지 계정도 조회/좋아요 대상에 사용합니다. 가입·로그인은 준비 단계에서 순차 실행하므로
200명이 동시에 로그인하는 부하는 이 프로필에 포함하지 않습니다.

| 단계 | 변화 | 유지 시간 |
| --- | --- | --- |
| 1 | 30초 동안 0 → 10명 | 30초 |
| 2 | 30초 동안 10 → 50명 | 30초 |
| 3 | 30초 동안 50 → 100명 | 30초 |
| 4 | 30초 동안 100 → 200명 | 3분 |
| 종료 | 30초 동안 200 → 0명 | - |

본 부하는 7분이며 계정 준비와 마지막 요청 정리 시간은 별도입니다.
`HOLD_DURATION=5m`처럼 지정하면 최대 부하 유지 시간만 변경합니다.
`VUS=50 PROFILE=capacity`처럼 낮은 목표를 주면 그보다 큰 중간 단계는 생략합니다.

같은 200명 증가 단계에서 좋아요 잠금 경합을 따로 보려면 다음처럼 실행합니다.

```bash
PROFILE=capacity WORKLOAD=hotspot bash scripts/run-k6.sh
```

테스트 계정이 201개 준비되더라도 현실적인 전체 회원 수와 좋아요 분포가 자동으로
재현되는 것은 아닙니다. 큰 데이터셋에 대한 조회 성능은 별도의 데이터 준비 후 비교합니다.

## 결과와 임시 성공 기준

기본 기준은 확정된 서비스 SLO가 아니라 병목 탐색용 임시 기준입니다.

- 본 부하 HTTP 실패율 1% 미만
- 응답 계약 실패율 1% 미만
- checks 성공률 99% 초과
- 전체 HTTP 응답 시간 p95 < 500ms, p99 < 1000ms
- 개별 API 응답 시간 p95 < 500ms (요청이 실제로 발생한 항목만 해석)

`p95`는 95%의 요청이 해당 시간 안에 완료됐다는 뜻입니다.
전체 지연과 API별 요청 수·p95를 함께 출력합니다. 0 requests인 API의 지연 값은
측정 결과가 아닙니다. 다음 페이지 커서가 없는 소규모 데이터에서는 커서 요청이 발생하지 않습니다.
회원가입·로그인 준비 요청은 `phase=setup`, 측정 요청은 `phase=workload`로 분리하여
성공 기준은 본 부하에만 적용합니다. k6의 일반 HTTP 총계에는 준비 요청도 포함될 수 있습니다.
인증·조회 실패를 성공으로 처리하지 않습니다. 기준 실패 시 k6는 0이 아닌 코드로 종료합니다.

요약 결과는 `k6/results/*-summary.json`에 저장되고 Git에서 제외합니다.
토큰·비밀번호·응답 본문은 결과 파일에 저장하지 않습니다.

선택적으로 k6 자체 웹 대시보드와 HTML 보고서를 켤 수 있습니다.

```bash
K6_WEB_DASHBOARD=true \
K6_WEB_DASHBOARD_EXPORT=k6/results/report.html \
PROFILE=load VUS=10 bash scripts/run-k6.sh
```

테스트 중 k6 웹 대시보드는 `http://localhost:5665`입니다.
이 대시보드는 Grafana와 별개이며, 테스트 결과를 자동으로 Prometheus에 전송하지 않습니다.
Grafana에서는 Spring Boot의 Prometheus 지표와 Loki 로그를 함께 관찰합니다.

## Grafana에서 함께 볼 항목

Prometheus 데이터 소스를 선택한 Explore에서 실행합니다.
처음에는 요청이 충분히 쌓인 뒤 1분 이상의 시간 범위를 사용합니다.

```promql
sum(rate(http_server_requests_seconds_count{uri=~"/api/.*"}[1m]))
```

API 전체 초당 요청 수입니다.

```promql
sum(rate(http_server_requests_seconds_count{uri=~"/api/.*",status=~"5.."}[1m]))
```

초당 5xx 응답 수입니다. 401/400도 확인하려면 status 조건을 바꿉니다.

```promql
hikaricp_connections_active
```

```promql
hikaricp_connections_pending
```

DB 활성 연결과 연결 대기 수입니다. 지표가 안 보이면 `/actuator/prometheus`에서 실제 이름과
라벨을 확인합니다. JVM 메모리, CPU, GC, Tomcat 스레드도 함께 봅니다.
Loki에서는 다음 쿼리로 오류 로그를 확인합니다.

```logql
{app="eulji-festival",log_type="error"}
```

## 설정값

| 환경변수 | 기본값 | 용도 |
| --- | --- | --- |
| `BASE_URL` | `http://127.0.0.1:8080` | 대상 앱 주소 |
| `PROFILE` | `smoke` | `smoke`, `load`, `capacity` |
| `WORKLOAD` | capacity는 `mixed`, 나머지는 `read` | `read`, `mixed`, `hotspot` |
| `VUS` | smoke 1 / load 10 / capacity 200 | 최대 가상 사용자 수 |
| `SMOKE_DURATION` | `15s` | 실행 확인 시간 |
| `HOLD_DURATION` | load `2m` / capacity `3m` | 최대 부하 유지 시간 |
| `PAUSE_SECONDS` | `1` | 반복 사이 대기 시간 |
| `P95_MS` | `500` | p95 임시 기준, p99 기준은 두 배 |

테스트를 강제 중단하려면 k6 터미널에서 Control + C를 누릅니다.
처음부터 강한 부하로 시작하지 말고, 1명 실행 확인 후 단계적으로 늘립니다.
한 Mac에서 앱·DB·모니터링·k6를 함께 실행하면 자원을 공유하므로,
로컬 결과만으로 배포 서버의 최대 수용 인원을 확정할 수 없습니다.
소수 테스트 계정만 있는 결과는 큰 데이터셋에서의 순위·목록 조회 성능을 대변하지 않습니다.

참고: [k6 시나리오](https://grafana.com/docs/k6/latest/using-k6/scenarios/),
[k6 성공 기준](https://grafana.com/docs/k6/latest/using-k6/thresholds/),
[k6 웹 대시보드](https://grafana.com/docs/k6/latest/results-output/web-dashboard/)
