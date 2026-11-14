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

프로젝트 루트에 `.env.example`을 복사해 `.env`를 만들고 `DB_PASSWORD`를 입력합니다.
로컬 `.env`가 이미 있으면 그대로 사용합니다. `.env`와 `.env.*`는 Git에서 제외하며,
비밀번호를 비워둔 `.env.example`만 공유합니다.

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
기존 V1 파일과 회원·방명록 데이터는 유지합니다.

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
API 문서 JSON은 `/v3/api-docs`에서 확인할 수 있습니다.
Swagger UI와 문서는 인증 없이 열리지만 회원가입·로그인 외 API의 인증은 유지됩니다.

## 로그 수집

Gymjjak과 같은 Logback → Promtail → Loki → Grafana 방식으로 구성했습니다.
일반·에러·성능 로그, 요청 traceId, 실행 명령과 Grafana 설정은
[로컬 로그 수집 안내](monitoring/README.md)를 참고합니다.

## k6 부하 테스트

1 VU 실행 확인, 조회·혼합·좋아요 집중 부하와 결과 해석은
[k6 실행 안내](k6/README.md)를 참고합니다. 전용 테스트 계정을 생성하므로 테스트 DB 사용을 권장합니다.
