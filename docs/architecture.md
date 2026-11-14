# 학술제 백엔드 구조와 API 구현

Gymjjak의 `user` 패키지처럼 도메인별로 `domain`, `application`, `adapter`를 구성한다.
Controller와 인증 필터는 입력 포트를 호출하고, 서비스는 출력 포트를 통해 외부 기능을 사용한다.
도메인은 Spring/JPA/JDBC/HTTP에 의존하지 않는다.

```text
member/
  domain/model/                 Member, Profile
  application/
    command/, result/           유스케이스 입력과 결과
    port/in/                    회원가입·로그인·프로필·토큰 인증
    port/out/                   MemberPort, PasswordHashPort, AccessTokenPort
    service/                    트랜잭션과 처리 흐름
  adapter/
    in/web/                     HTTP 요청 검증과 명령 변환
    out/persistence/            MemberJpaEntity, Mapper, Spring Data 저장소
    out/security/               비밀번호 해싱, JWT 발급·검증

guestbook/
  domain/model/, policy/        방명록, 정렬, 커서, 글자 수 규칙
  application/
    port/in/, port/out/         방명록 명령·조회 포트
    service/, result/           좋아요 처리와 조회 결과
    cache/                      트랜잭션 바깥의 이름표 캐시 유스케이스 장식자
  adapter/in/web/               방명록·캐릭터·순위 API
  adapter/out/persistence/      JDBC 저장과 조회
  adapter/out/cache/            Caffeine 회원별 이름표 캐시, 커밋 후 무효화

global/
  domain/exception/             오류 코드와 예외
  adapter/in/web/, security/    공통 응답·예외 처리·Bearer 필터
  infrastructure/config/       SecurityFilterChain과 UTC Clock
```

회원은 JPA 엔티티와 도메인을 Mapper로 분리했다. 방명록 저장은 JDBC 어댑터로 구현했다.
순위·목록은 JDBC 조회 어댑터에서 조인과 `RANK()`를 사용한다. 영속성 기술은 출력 포트 뒤에
위치하며, 서비스는 JPA 저장소나 JDBC를 직접 참조하지 않는다. JPA와 JDBC 쓰기는 같은
DataSource와 JpaTransactionManager의 트랜잭션을 공유한다.

## 구현한 API와 명세

원본: [Notion Rest API 명세서](https://app.notion.com/p/41413f22e2028011aa9fcdb678f25666)

| 메서드 | 경로 | 명세 | 핵심 동작 |
| --- | --- | --- | --- |
| POST | `/api/auth/signup` | [회원가입](https://app.notion.com/p/41413f22e202817ea44df08a3c88f1ba) | 회원·방명록 원자적 저장, 이메일 중복 409 |
| POST | `/api/auth/login` | [로그인](https://app.notion.com/p/41413f22e20281148796f14d904894df) | 비밀번호 검증, 액세스 토큰 반환 |
| PUT | `/api/members/me/profile` | [프로필 수정](https://app.notion.com/p/41413f22e20281f29aa3e7af70f1724e) | 토큰의 본인 수정, 기존 좋아요와 작성 시각 유지 |
| GET | `/api/characters?memberIds=11,12` | [이름표](https://app.notion.com/p/41413f22e20281088285e9d108b4a340) | 없는 ID 제외, 결과가 없으면 빈 배열 |
| GET | `/api/characters/{memberId}` | [상세](https://app.notion.com/p/41413f22e2028167811adacd6442a70f) | 전역 좋아요 기준 순위, 1~3위 외 null |
| GET | `/api/rankings/top` | [순위](https://app.notion.com/p/41413f22e20281f8a921dae9193be40e) | 공동 순위를 포함한 1~3위 전원 반환 |
| GET | `/api/guestbook` | [방명록 목록](https://app.notion.com/p/41413f22e20281c5b476eff5b8c3909e) | 인기순 기본, 최신순, 커서, 작성 경과 시간 |
| PUT | `/api/guestbook/{memberId}/like` | [좋아요 등록](https://app.notion.com/p/41413f22e20281a993cae7a21f3a6d32) | 반복 등록에 중복 집계 없음 |
| DELETE | `/api/guestbook/{memberId}/like` | [좋아요 취소](https://app.notion.com/p/41413f22e2028119ae6ddbf3ab45bd9b) | 반복 취소에 중복 차감 없음 |

HTTP 상태와 `status`, `code`, `message`, `data` 응답 구조는 명세를 따른다.
실패 응답은 동일한 구조로 `data: null`을 반환한다. 회원가입·로그인 외 API는
`Authorization: Bearer {accessToken}`이 필요하다.

## 데이터 규칙과 동시성

- 가입은 회원 저장과 방명록 저장을 같은 트랜잭션으로 묶는다. 두 번째 저장 실패도 전체 롤백한다.
- 이메일은 앞뒤 공백 제거 후 소문자로 정규화하고 DB의 UNIQUE 제약으로 동시 가입 중복도 막는다.
- 비밀번호는 Spring Security의 PBKDF2 기본 권장 프리셋(v5_8)으로 매번 임의 salt를 넣어 해싱한다.
  명세에서 길이·문자 정책은 미정이므로 빈 값·공백만 거부하고 추가 조합 규칙은 강제하지 않는다.
- 방명록은 앞뒤 공백을 제거하고 Java 정규식 `\X`의 확장 grapheme cluster 기준 1~100자로 검증한다.
  결합 문자·국기·피부색·가족 이모지를 UTF-16 길이로 세지 않는다.
- 좋아요와 프로필 수정은 대상 방명록을 `SELECT ... FOR UPDATE`로 먼저 잠근다.
  좋아요는 사전 locking read 없이 실제 추가·삭제 여부로 집계를 변경하며, 기록과 집계 변경은 같은 트랜잭션이다.
- 이름표·상세·순위·방명록 목록은 읽기 전용 서비스 트랜잭션으로 조회한다.
  이 트랜잭션 제거 실험은 200 VU 두 실행에서 API 지연 개선을 확인하지 못해 채택하지 않았다.
- 이름표 조회는 트랜잭션 바깥의 캐시 유스케이스가 먼저 확인한다. 적중 시 DB 조회·연결을 생략하며,
  미스 회원들만 기존 조회 서비스에 일괄 위임한다. 이름·학년 수정은 커밋 후 무효화하고 롤백 시 유지한다.
  진행 중인 트랜잭션 안에서 호출되면 캐시를 우회해 미커밋 값이 캐시에 저장되지 않게 한다.
- DB의 DATETIME(6)은 UTC로 쓰고 읽는다. 응답은 UTC ISO 8601이며, 조회 요청마다 기준 시각을 한 번 잡는다.
- `RANK() OVER (ORDER BY like_count DESC)`로 동점이면 같은 순위, 다음 순위는 인원수만큼 건너뛴다.
  조회 필터와 페이지 경계는 전역 순위를 계산한 후 적용한다. 0개 좋아요도 같은 순위 규칙을 적용한다.
- 커서는 정렬 방식·좋아요 수·마이크로초 작성 시각·회원 ID를 포함한다. 정렬과 커서가 다르면 400이다.
  인기순은 좋아요 DESC, 작성 시각 DESC, 회원 ID ASC이고 최신순은 작성 시각 DESC, 회원 ID ASC이다.
  기본 크기는 20, 허용 범위는 1~100이다. 인기순은 실시간 데이터이므로 페이지 요청 사이 좋아요가
  바뀌면 항목의 위치가 바뀔 수 있다. 명세대로 재정렬 시 클라이언트가 커서를 초기화해야 한다.

## 명세에서 미정인 정책에 대한 구현 기본값

액세스 토큰은 HS256으로 서명한 JWT다. 발급자, 회원 ID(`sub`), 용도(`token_type=access`),
발급·만료 시각과 고유 ID를 포함한다. 인증은 서명·알고리즘·발급자·용도·시각·회원 ID를
검증하며 DB 조회와 트랜잭션을 사용하지 않는다. 유효 기간은 기본 1시간이다.
`ACCESS_TOKEN_TTL=PT2H`처럼 ISO 8601 Duration으로 변경할 수 있다. 토큰 원문·비밀번호는
응답 외 로그에 기록하지 않는다. 만료되면 다시 로그인한다. 토큰 갱신·로그아웃 API는 명세에 없다.
`JWT_SECRET`은 최소 32바이트 난수의 Base64 인코딩 값이며 필수 환경변수다.
기존 랜덤 토큰은 JWT 전환 후 거부되므로 다시 로그인해야 한다.
회원 삭제나 기존 `access_tokens` 행 삭제로 JWT가 즉시 폐기되지는 않는다.
이는 DB 조회 없는 JWT 전환으로 바뀐 인증 정책이며, 즉시 폐기가 필요하면 별도 상태 검증을 설계해야 한다.

기존 V1·V2 마이그레이션과 회원·방명록 데이터는 유지한다.
V2의 `access_tokens` 테이블은 이력 호환을 위해 남겨두며 JWT 발급·검증에는 사용하지 않는다.

근접 거리 판단, 캐릭터 이동, 순위 갱신 주기, 화면 스크롤·정렬 전환 응답 처리는 클라이언트 영역이며
서버 API에는 근접 거리나 이동 프로토콜을 추가하지 않았다.

## 검증 실행

```bash
./gradlew test
```

기본 테스트는 도메인·커서·경과 시간·가입 검증·비밀번호 해싱을 검증한다.
MySQL 통합 테스트는 별도 환경변수가 없으면 건너뛴다.

삭제해도 되는 **테스트 전용 MySQL DB** `eulji_festival_test`를 준비하고 다음처럼 실행한다.
테스트는 해당 DB의 데이터를 매 테스트 전에 삭제한다. 앱의 `eulji_festival` DB는 테스트에 사용하지 않는다.

```bash
export TEST_DB_URL='jdbc:mysql://127.0.0.1:13307/eulji_festival_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
export TEST_DB_USERNAME='테스트계정'
# TEST_DB_PASSWORD는 로컬 환경에 설정한다.
./gradlew test --rerun-tasks
```

통합 테스트는 실제 Flyway V1/V2 적용과 JPA 검증을 거친 전체 앱에서 MockMvc로 9개 API를 호출한다.
인증·만료·잘못된 타입·회원가입 저장 실패 롤백·반복/동시 좋아요·프로필 보존·공동 순위·커서 경계를 확인한다.
