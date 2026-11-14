# 로컬 Loki · Promtail 로그 수집

Gymjjak처럼 `Spring Boot → 파일 로그 → Promtail → Loki → Grafana` 구조를 사용합니다.
이 문서는 모든 프로그램을 같은 Mac에서 직접 실행하는 환경 기준입니다.

## 1. 실행 파일 준비

- Loki: [공식 릴리스](https://github.com/grafana/loki/releases)의 macOS 실행 파일
- Promtail: [3.5.7 릴리스](https://github.com/grafana/loki/releases/tag/v3.5.7)의 macOS 실행 파일
- Apple Silicon은 `darwin-arm64`, Intel은 `darwin-amd64` 파일을 선택합니다.
- `logcli`나 `loki-canary`는 Loki 서버 실행 파일이 아닙니다.
- 압축 해제 후 실행 권한이 없으면 해당 파일에 `chmod +x /절대경로/실행파일`을 적용합니다.

Loki와 Promtail 실행 파일은 프로젝트에서 자동 다운로드하거나 설치하지 않습니다.
현재 설정은 Loki의 TSDB/v13와 filesystem 저장소를 사용합니다.

## 2. 앱 실행

IntelliJ에서 Gradle을 동기화하고 기존 실행 설정의 `.env`를 사용해 앱을 실행합니다.
Working directory는 Backend 프로젝트 루트로 설정합니다.

`src/main/resources/logback-spring.xml`이 콘솔 출력과 아래 파일 생성을 담당합니다.

| 로그 파일 | 용도 | 보관 정책 |
| --- | --- | --- |
| `logs/eulji-festival.log` | 앱 일반 로그 및 API 요청 완료 로그 | 10MB 단위 회전, 30일, 보관 파일 1GB |
| `logs/eulji-festival-error.log` | ERROR 레벨 로그, 예외 스택 | 10MB 단위 회전, 30일, 보관 파일 500MB |
| `logs/eulji-festival-performance.log` | 애플리케이션 서비스 메서드 처리 시간 | 10MB 단위 회전, 30일, 보관 파일 1GB |

로그 파일 경로를 바꾸려면 앱의 `LOG_PATH` 환경변수를 지정하고 Promtail의 `__path__`도 맞춥니다.
prod 프로필에서는 콘솔 출력을 생략하고 앱 INFO/외부 WARN 이상을 파일에 남깁니다.
Gymjjak의 상세 DEBUG 출력 대신 INFO를 기본으로 사용합니다.

API 요청에는 서버가 생성한 `X-Trace-Id` 응답 헤더가 추가됩니다.
같은 traceId가 일반 로그와 성능 로그에 기록됩니다. 요청·응답 본문, Authorization 헤더,
서비스 인수·반환값은 자동 로그에 기록하지 않습니다. 서비스 메서드가 실패해도 실행 시간을 기록합니다.
Gymjjak은 get/find 접두사 메서드를 측정하지만, 학술제는 실제 서비스 명명에 맞춰 모든 public
application service 메서드를 측정합니다. 성능 파일의 시간은 메서드 시간이며 HTTP 전체 지연은
k6 또는 Prometheus로 함께 확인합니다. Actuator·Swagger 경로는 요청 완료 로그에서 제외합니다.

## 3. Loki 실행

Backend 프로젝트 루트의 터미널에서 다운로드한 실행 파일의 실제 경로를 전달합니다.
아래 `/절대경로/` 부분은 받은 파일의 위치로 바꿉니다.

```bash
bash scripts/run-loki.sh /절대경로/loki-darwin-arm64
```

스크립트는 프로젝트 루트로 이동한 뒤 `monitoring/loki/loki-config.yml`을 사용합니다.
이후 터미널을 켜 둡니다. Loki 데이터는 `monitoring/loki/data/`에 저장되고 7일 보관됩니다.
로컬 전용 HTTP 주소는 `127.0.0.1:3100`, gRPC 포트는 `9095`입니다.

```bash
curl http://127.0.0.1:3100/ready
```

초기화가 끝나면 `ready`가 반환됩니다.

## 4. Promtail 실행

새 터미널에서 Backend 프로젝트로 이동하고 실행합니다.

```bash
bash scripts/run-promtail.sh /절대경로/promtail-darwin-arm64
```

`monitoring/promtail/promtail-config.yml`이 일반·에러·성능 파일을 읽고
`http://127.0.0.1:3100/loki/api/v1/push`로 전송합니다.
`app=eulji-festival`, `env=local`, `service=backend`, `log_type` 라벨로 구분합니다.
일반·에러 로그는 `level` 라벨도 추출하고, Java 예외 스택은 multiline 단계로 묶습니다.
읽은 위치는 `monitoring/promtail/promtail-positions.yml`에 저장합니다.

`http://127.0.0.1:9080/ready`는 수집 준비 상태, `/targets`는 수집 대상 확인용입니다.
앱 실행 후 로그 파일이 생성되어야 파일 대상이 준비됩니다.

Loki와 Promtail은 각각 실행한 터미널에서 `Control + C`로 종료합니다.
로그·Loki 데이터·Promtail positions 파일은 Git에서 제외합니다.

## 5. Grafana 연결

Grafana에서 **Connections → Data sources → Add data source → Loki**를 선택합니다.

- 이름: `Loki`
- URL: `http://127.0.0.1:3100`
- **Save & test**로 연결 확인

그 다음 **Explore**에서 Loki를 선택하고 다음 쿼리를 실행합니다.

```logql
{app="eulji-festival", log_type="application"}
```

```logql
{app="eulji-festival", log_type="error"}
```

```logql
{app="eulji-festival", log_type="performance"}
```

일반 파일에도 ERROR가 포함되므로 전체 app 라벨로 조회하면 에러가 두 스트림에 보일 수 있습니다.
`log_type`으로 구분해서 조회합니다. API 요청을 보낸 후 새 로그가 도착하는지 확인합니다.

선택적으로 `monitoring/grafana/provisioning/datasources/loki.yml`을 Grafana의
실제 `conf/provisioning/datasources/` 폴더로 복사하고 Grafana를 재시작해 자동 등록할 수도 있습니다.
프로젝트에 예시 파일이 있는 것만으로 현재 설치된 Grafana가 자동으로 읽지는 않습니다.

## 참고

요청한 Gymjjak 구성과 같게 하기 위해 Promtail을 사용합니다.
Promtail은 지원이 종료된 도구이므로 이 설정은 기존 방식 재현용입니다.
신규 운영 배포 구성에서는 Alloy로의 전환을 검토합니다.

- [Loki 설정](https://grafana.com/docs/loki/latest/configure/)
- [Promtail 상태와 안내](https://grafana.com/docs/loki/latest/send-data/promtail/)
- [Loki 데이터 소스 설정](https://grafana.com/docs/grafana/latest/datasources/loki/configure/)
