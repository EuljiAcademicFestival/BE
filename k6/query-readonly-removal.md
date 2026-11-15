## 26. 조회 서비스 전체 readOnly 해제 — 트랜잭션 유지 실험

현재 상태: 2026-11-16 비교 측정 후 사용자 요청으로 `@Transactional(readOnly = true)`를 복원했다. 아래는 제거 실험 당시 기록이다. 복원 후 전체 테스트 92개와 bootJar 빌드가 통과했으며, 사용자 8080 서버는 재시작 후 반영된다.

기록일: <date start="2026-11-16" />

25번의 진단 후 사용자 요청에 따라 GuestbookQueryService의 클래스 선언을 @Transactional(readOnly = true)에서 @Transactional로 변경했다. 이름표·캐릭터 상세·상위 순위·방명록 목록 전체에 적용된다. 메서드별로 추가된 readOnly 선언은 없다.

### 변경 범위

- 트랜잭션 자체는 유지한다. 12번의 트랜잭션 전체 제거·자동 커밋 실험과 다른 변경이다.
- 기존 전파 속성 REQUIRED를 유지한다. 이미 존재하는 외부 트랜잭션에 참여하면 그 외부 속성을 따르며, 외부 읽기 전용 트랜잭션을 강제로 쓰기 트랜잭션으로 바꾸지는 않는다.
- 기본 조회 경로에서 읽기 전용 상태 설정·복원 비용을 줄이는 것이 목적이다. 자동 커밋 전환·커밋 등 모든 트랜잭션 통신이 없어지는 것은 아니다.
- SQL·DTO·API 형식·순위/커서 규칙·쓰기 서비스·캐시 TTL·연결 풀 크기는 변경하지 않았다.
- 이름표·순위 캐시 적중은 기존처럼 트랜잭션 서비스에 진입하지 않는다. 캐시 미스 때 일반 조회 트랜잭션을 사용한다.
- spring.jpa.open-in-view=false는 기존부터 설정되어 있으며 그대로다.
- 읽기 전용에 따른 Hibernate/MySQL 최적화는 줄어들 수 있으므로 실제 성능 효과는 부하로 검증해야 한다. 현재 조회는 JdbcTemplate으로 DTO를 읽는 구조다.

### 검증 결과

개발 DB 3306과 분리된 13307 MySQL 전용 eulji_festival_test에서 전체 테스트 92개 통과. 실패·오류·건너뜀 0개, bootJar 빌드 성공. 검증 후 테스트 MySQL만 종료했다.

추가한 통합 테스트 allQueryPathsKeepTransactionsWithoutMarkingConnectionsReadOnly는 실제 Spring 프록시와 조회 어댑터 호출 안에서 다음을 확인했다.

- 트랜잭션 활성 상태 true.
- Spring 읽기 전용 상태 false.
- 실제 JDBC 연결 autoCommit=false, readOnly=false.
- 이름표·상세·상위 순위·인기순·최신순 5개 실제 조회가 정상 결과를 반환하고 Hikari 획득은 각각 1회, 총 5회.
- 기존 캐시 적중의 연결 획득 0회, 외부 트랜잭션 캐시 우회, 좋아요 동시성·롤백·JWT·순위·커서·WebSocket 회귀 검증도 통과.

### 비교 상태

사용자 8080 서버는 이번 변경으로 재시작하지 않았다. readOnly 해제 후 200명 부하 측정은 아직 하지 않았다. 향후 재시작 후 동일한 200명·3분 REST mixed 조건으로 확인한다.

순수 성능 비교의 기준은 JFR이 없는 24번 실행이며, 25번은 진단 계측이 추가된 자료라 같은 조건의 속도 비교로 취급하지 않는다. 연결 획득/점유 시간·초기와 유지 구간 pending·API별 p95/p99·오류·집계 정합성을 확인한다. 현재 단계에서는 응답속도 개선이나 병목 해결을 확정하지 않는다.

코드: GuestbookQueryService.java. 회귀 테스트: BackendApplicationTests.java. 실행 중 앱을 재시작하거나 DB 데이터를 변경하는 작업은 이 구현 단계에서 수행하지 않았다.
