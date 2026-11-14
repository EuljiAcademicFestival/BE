# BE

## 개발 환경

Gymjjak 백엔드와 같은 Java 17, Spring Boot 3.5.14, Gradle 8.14.4,
springdoc-openapi 2.8.16을 사용합니다.

## 로컬 DB 연결

MySQL 8.0.16 이상을 사용합니다. 빈 `eulji_festival` DB와 해당 DB에 접근할
`eulji_app` 계정을 먼저 준비합니다. 계정에는 데이터 조회·변경 및 Flyway의
테이블 생성·변경에 필요한 권한이 있어야 합니다.

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `DB_URL` | `jdbc:mysql://127.0.0.1:3306/eulji_festival?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true` | JDBC 연결 URL |
| `DB_USERNAME` | `eulji_app` | DB 계정 |
| `DB_PASSWORD` | 없음, 필수 | DB 비밀번호 |
| `ACCESS_TOKEN_TTL` | `PT1H` | 액세스 토큰 유효 기간, ISO 8601 Duration |
| `JWT_SECRET` | 없음, 필수 | 최소 32바이트 난수 키를 Base64로 인코딩한 값 |
| `JWT_ISSUER` | `eulji-academic-festival` | JWT 발급자 및 검증 대상 발급자 |

프로젝트 루트에 `.env.example`을 복사해 `.env`를 만들고 `DB_PASSWORD`를 입력합니다.
로컬 `.env`가 이미 있으면 그대로 사용합니다. `.env`와 `.env.*`는 Git에서 제외하며,
비밀번호를 비워둔 `.env.example`만 공유합니다.

JWT 비밀키도 `.env`의 `JWT_SECRET`에 설정합니다. 이번 전환 작업에서는 로컬 `.env`에
난수 키를 생성했습니다. 새 환경에서는 `openssl rand -base64 32`로 생성해 넣습니다.
키를 Git에 올리지 않고, 서버를 여러 대 실행하면 같은 서비스의 키·발급자 설정을 맞춥니다.
실행할 때마다 키를 새로 만들지 않습니다. 키를 바꾸면 기존 JWT의 인증이 실패합니다.

IntelliJ에서는 **Run → Edit Configurations → BackendApplication → Environment variables**에서
**Browse for .env files and scripts** 버튼을 눌러 프로젝트 루트의 `.env`를 선택합니다.
환경변수 항목이 보이지 않으면 **Modify options → Environment variables**를 켭니다.
이전에 직접 입력한 같은 이름의 DB 환경변수는 삭제해 설정을 한곳에서 관리합니다.
파일 선택 기능이 없는 구버전에서는 EnvFile 플러그인을 설치하고 해당 실행 설정의
**EnvFile → Enable EnvFile → +**에서 `.env`를 추가합니다.
Spring Boot 자체는 `.env`를 자동으로 읽지 않으므로 IntelliJ에서 파일 로딩 설정이 필요합니다.

앱이 시작되면 Flyway가 `src/main/resources/db/migration`의 SQL을 적용합니다.
Hibernate는 `ddl-auto: validate`로 스키마를 검증합니다.
`V1__init.sql`은 Notion 학술제 DB 초안의 회원·방명록·좋아요 테이블을 생성합니다.
`V2__create_access_tokens.sql`은 액세스 토큰 해시와 만료 시각을 저장하는 테이블을 추가합니다.
JWT 전환 후 이 테이블은 인증과 발급에 사용하지 않습니다. 기존 마이그레이션과 데이터는 보존합니다.

[헥사고날 구조, 구현한 9개 API, 정책과 테스트 안내](docs/architecture.md)를 참고합니다.

Workbench에서 다음 쿼리로 적용 결과를 확인합니다.

```sql
USE eulji_festival;
SHOW TABLES;
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

정상 적용 시 `members`, `guestbook_entries`, `guestbook_likes`,
`access_tokens`, `flyway_schema_history`가 보이고 버전 `1`, `2`의 `success`는 `1`입니다.
공유하거나 배포한 마이그레이션은 수정하지 않고 `V2__설명.sql`처럼 새 버전을 추가합니다.

## Swagger UI로 API 테스트

앱을 실행한 뒤 [Swagger UI](http://localhost:8080/swagger-ui/index.html)를 엽니다.

1. **인증 → POST /api/auth/signup → Try it out**에서 회원 정보를 입력하고 **Execute**합니다.
2. **POST /api/auth/login**에서 가입한 이메일과 비밀번호로 로그인합니다.
3. 응답의 `data.accessToken`을 복사합니다.
4. 화면 오른쪽 위 **Authorize**에서 **Bearer 접두사 없이 토큰 값만** 입력하고 **Authorize → Close**합니다.
5. 프로필·캐릭터·방명록·순위 API의 **Try it out → Execute**로 테스트합니다.

조회에 사용할 `memberId`는 회원가입 또는 로그인 응답의 값을 사용합니다.
Swagger의 `visitor@example.com`은 입력 예시이므로 이미 가입했다면 다른 이메일로 테스트하거나 로그인합니다.
토큰의 기본 유효 기간은 1시간이며 만료되면 다시 로그인하고 Authorize 값을 교체합니다.
JWT 전환 전 발급된 랜덤 토큰은 사용할 수 없으므로 다시 로그인합니다.
JWT는 서명·발급자·용도·만료를 서버에서 검증하며 인증에 DB 조회나 트랜잭션을 사용하지 않습니다.
DB의 토큰 행 삭제나 회원 삭제로 이미 발급된 JWT가 즉시 무효화되지는 않습니다.
개별 기능에서 회원 존재가 필요한 경우에는 해당 기능의 DB 처리 결과를 따릅니다.
Refresh Token과 토큰 갱신 API는 추가하지 않았습니다.
API 문서 JSON은 `/v3/api-docs`에서 확인할 수 있습니다.
Swagger UI와 문서는 인증 없이 열리지만 회원가입·로그인 외 API의 인증은 유지됩니다.

## 로그 수집

Gymjjak과 같은 Logback → Promtail → Loki → Grafana 방식으로 구성했습니다.
일반·에러·성능 로그, 요청 traceId, 실행 명령과 Grafana 설정은
[로컬 로그 수집 안내](monitoring/README.md)를 참고합니다.

## k6 부하 테스트

1 VU 실행 확인, 조회·혼합·좋아요 집중 부하와 결과 해석은
[k6 실행 안내](k6/README.md)를 참고합니다. 전용 테스트 계정을 생성하므로 테스트 DB 사용을 권장합니다.

## 캐릭터 이름표 캐시

`GET /api/characters?memberIds=...`는 회원 ID별 이름·학년을 Caffeine 로컬 캐시에 저장합니다.
캐시 적중 시 조회 서비스의 트랜잭션에 진입하지 않아 DB 연결을 빌리지 않습니다.
캐시에 없는 회원들은 기존 트랜잭션의 IN 쿼리 한 번으로 묶어 조회합니다.
이름·학년이 바뀌면 프로필 수정 커밋 후 해당 회원을 무효화하며, 롤백되면 유지합니다.

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `CHARACTER_LABEL_CACHE_TTL` | `PT5M` | 저장 후 만료 시간 |
| `CHARACTER_LABEL_CACHE_MAXIMUM_SIZE` | `10000` | 저장할 최대 회원 수 |

`cache_gets_total{cache="characterLabels",result="hit"}` / `result="miss"`와
`cache_size{cache="characterLabels"}`를 Prometheus에서 확인할 수 있습니다.
적중률은 API 요청 단위가 아니라 조회한 회원 키 단위입니다.
캐시는 서버 한 대의 메모리에 있으며 재시작 시 비워집니다. 여러 서버의 무효화는 공유하지 않습니다.
DB 직접 수정·삭제처럼 애플리케이션을 거치지 않는 변경은 만료 전까지 이전 값이 보일 수 있습니다.
