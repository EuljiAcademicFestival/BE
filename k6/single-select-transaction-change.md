## 12. 단일 조회 트랜잭션 제거 실험 — 미채택·원복

상태: 두 번의 200 VU 실험에서 HTTP 지연이 높아져 조회 서비스 트랜잭션을 원복했다. 아래는 실험 당시 변경과 검증 기록이다.

JWT 전환 뒤에도 DB 연결 대기가 남아 있어 단일 조회의 명시적 JPA 트랜잭션을 제거했다.
`GuestbookQueryService`의 이름표·캐릭터 상세·상위 순위·방명록 목록은 각각 출력 포트를 한 번 호출하고,
각 어댑터 메서드는 JDBC SELECT 한 문장만 실행한다.

### 변경 의도

- 서비스 수준 `@Transactional(readOnly = true)`를 제거한다.
- 입력 검증에서 실패하면 DB 연결을 빌리지 않는다.
- 정상 조회는 JdbcTemplate이 SQL·결과 매핑을 끝낸 뒤 연결을 반환한다.
  이후 응답 DTO 조합과 서비스 성능 로그가 연결을 계속 잡지 않도록 한다.
- 가입·로그인·좋아요·프로필 수정의 쓰기 트랜잭션은 유지한다.
- DB 연결 풀 크기·SQL·캐시는 변경하지 않는다.

트랜잭션을 합치는 변경이 아니다. 정상 조회는 여전히 DB 연결 1회를 사용한다.
MySQL의 autocommit 단일 SELECT 트랜잭션으로 실행하므로 한 SQL 안의 CTE·JOIN·서브쿼리는 함께 조회한다.
이미 트랜잭션이 있는 호출자 안에서 사용하면 기존 트랜잭션에 참여할 수 있다.
추후 여러 SELECT를 하나의 스냅샷으로 묶어야 하는 기능으로 바뀌면 트랜잭션 경계를 재검토해야 한다.

### 기능 검증

- 변경 전 두 회귀 테스트가 실패해 기존의 불필요한 서비스 트랜잭션·연결 획득을 재현했다.
- 변경 후 실제 HTTP 조회 4종과 인기순·최신순 경로에서 서비스 트랜잭션이 없음을 검증했다.
- 잘못된 회원 ID·페이지 크기·정렬·커서의 400 응답에서 Hikari 연결 획득 증가가 0임을 확인했다.
- 전체 테스트 **41개 통과**, 실패·오류·건너뜀 0개, bootJar 빌드 성공.
- 기존 JWT, 저장 실패 롤백, 좋아요 동시성, 공동 순위와 커서 동등성 테스트도 통과했다.
- 개발 DB와 분리된 `eulji_festival_test`에서 검증했다.

### 부하 검증 결과와 최종 상태

200 VU 두 실행에서 p95가 각각 23.245ms, 23.85ms로 기존 JWT 기준 13.072ms보다 높았다.
연결 점유는 감소한 관측 결과가 있었지만 HTTP 성능 개선을 입증하지 못해 이 후보는 채택하지 않았다.
조회 서비스의 `@Transactional(readOnly = true)`를 복원했다.
실험 조건을 강제하던 두 테스트와 스파이도 제거하고 기존 JWT·기능 회귀 테스트를 다시 실행해 39개 모두 통과했다. bootJar 빌드도 성공했다.
JWT의 DB 없는 인증과 기존 쿼리 최적화, 쓰기 트랜잭션은 유지한다.
실행 중인 8080 JVM에는 재시작 후 원복 코드가 반영된다. 원복 후 부하테스트는 아직 실행하지 않았다.
상세 비교는 `k6/single-select-load-test-result.md`에 기록한다.

근거: [MySQL autocommit](https://dev.mysql.com/doc/refman/8.0/en/innodb-autocommit-commit-rollback.html),
[Spring JDBC 연결 관리](https://docs.spring.io/spring-framework/reference/6.2/data-access/jdbc/connections.html).
