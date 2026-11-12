# BE

## 로컬 DB 연결

MySQL 8.0.16 이상을 사용합니다. 빈 `eulji_festival` DB와 해당 DB에 접근할
`eulji_app` 계정을 먼저 준비합니다. 계정에는 데이터 조회·변경 및 Flyway의
테이블 생성·변경에 필요한 권한이 있어야 합니다.

| 환경변수 | 기본값 | 설명 |
| --- | --- | --- |
| `DB_URL` | `jdbc:mysql://127.0.0.1:3306/eulji_festival?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true` | JDBC 연결 URL |
| `DB_USERNAME` | `eulji_app` | DB 계정 |
| `DB_PASSWORD` | 없음, 필수 | DB 비밀번호 |

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
방명록의 사용자에게 보이는 글자 기준 1~100자 검증과 좋아요 집계의 동시성 처리는
후속 애플리케이션 구현에서 적용해야 합니다.

Workbench에서 다음 쿼리로 적용 결과를 확인합니다.

```sql
USE eulji_festival;
SHOW TABLES;
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

정상 적용 시 `members`, `guestbook_entries`, `guestbook_likes`,
`flyway_schema_history`가 보이고 버전 `1`의 `success`는 `1`입니다.
공유하거나 배포한 마이그레이션은 수정하지 않고 `V2__설명.sql`처럼 새 버전을 추가합니다.
