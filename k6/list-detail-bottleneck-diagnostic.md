## 25. 목록·캐릭터 상세의 DB 연결 병목 JFR 진단

기록일: <date start="2026-11-16" />

### 기록 시간과 실행 조건

실제 200명 본 부하는 이전과 같은 3분이다. JFR의 5분은 로그인 준비 약 72초와 종료 정리를 담기 위한 최대 기록 시간이다. 실제 JFR 기록은 01:38:42 KST부터 276초(4분 36초)였으며, 부하가 끝난 뒤 이번 기록만 저장·중지했다. JFR.check에서 남은 기록이 없음을 확인했고 사용자 8080 서버는 종료하지 않았다.

앱·설정·연결 풀 10개·기존 읽기 트랜잭션·상위 순위 1초 캐시는 그대로 유지했다. JFR 설정은 ThreadPark·JavaMonitorEnter 5ms 이상, SocketRead/Write·FileWrite 2ms 이상, 스택 기록이다. 환경변수·초기 시스템 속성·JVM 인자 이벤트를 비활성화했다.

실행은 01:38:45~01:43:00 KST, PROFILE=smoke, VUS=200, SMOKE_DURATION=3m, WORKLOAD=mixed, PAUSE_SECONDS=1이다. smoke는 200명 constant-vus 분기다. 기존 회원 203명·테스트 계정 201개를 재사용했다. JFR 계측 비용과 JVM 워밍업이 이전 실행과 달라 24번과 순수 속도 개선율을 비교하지 않는다.

### 부하 결과

- 본 요청 283,664건, 준비 포함 284,067건. HTTP 실패·계약 실패 모두 0건, 모든 thresholds 통과, 종료 코드 0.
- 평균 6.023ms, p95 22.143ms, p99 47.320ms, 최대 272.396ms.
- 인기순·최신순·다음 페이지·상세 등 각 35,008건, 프로필 수정 3,600건.
- 인기순 p95 30.597ms, 최신순 29.589ms, 상세 29.521ms, 다음 페이지 21.761ms.
- 상위 순위 35,008요청 중 캐시 로딩 177회, 적중 약 99.494%.
- 본 부하 Hikari 획득 213,963회, 요청당 약 0.754회. 연결 획득 타임아웃 0, 종료 후 active·pending 모두 0.
- 준비의 예상 가입 중복 409 201건 외 API 4xx·5xx는 0건. 실행 전후 좋아요 집계 불일치 0건.

### 분석 구간과 관측 간격의 한계

JFR 분석 시작은 k6 준비 완료 로그의 초 단위 시각 01:39:57 KST로 잡고 첫 30초와 이후를 분리했다. 경계는 이벤트 시작 시각으로 분류했으며 첫 초에 준비 단계 말미가 일부 섞일 수 있다. 준비 전반의 비밀번호 검증 구간은 제외한다.

약 1초 표본 pending 최대는 79명으로 01:40:35.042 KST(분석 시작 약 38초 뒤)에 active 10개와 함께 관측됐다. 약 5초 Prometheus 표본 최대는 42명이었다.

첫 30초의 1초 표본 pending은 모두 0이었지만, 그 구간의 JFR에는 아래와 같은 연결 획득 대기가 직접 기록됐다. 표본이 짧은 대기를 놓칠 수 있으므로 0이 대기 없음이라는 뜻이 아니다. 이번 최댓값도 시작 이후 구간에 있어 시작 순간만의 문제로 볼 수 없다.

### 1. SQL 실행 전 연결 풀 대기를 확인

대표 경로:

```text
GuestbookQueryService 프록시의 page / detail
  → TransactionInterceptor
  → JpaTransactionManager.doBegin
  → HikariDataSource.getConnection
  → HikariPool.getConnection
  → ConcurrentBag.borrow
  → SynchronousQueue.poll / LockSupport.parkNanos
```

연결을 획득하기 전 ThreadPark 5ms 이상만 선별한 결과:

| 호출 경로 | 이벤트 수 | 첫 30초 대기 합계 | 이후 대기 합계 | 전체 대기 합계 |
| --- | --- | --- | --- | --- |
| 목록 page | 32,165 | 75.228초 | 260.999초 | 336.226초 |
| 캐릭터 상세 detail | 11,908 | 28.499초 | 97.328초 | 125.827초 |
| 좋아요 | 13,147 | 29.290초 | 101.899초 | 131.189초 |
| 회원 명령 | 1,009 | 3.001초 | 8.643초 | 11.644초 |
| 이름표 미스 | 160 | 4.384초 | 0 | 4.384초 |
| 상위 순위 캐시 미스 | 1 | 0 | 0.005초 | 0.005초 |

선별한 풀 대기 합계는 약 609.276 스레드·초다. 여러 스레드의 대기 합계로 테스트 경과 시간과 다르다. 하나의 연결 획득에서 여러 park가 생길 수 있어 이벤트 수를 요청 수·연결 획득 횟수로 세지 않는다.

목록·상세가 이 선별 합계의 약 75.8%를 차지한다. 그러나 목록은 3개 조회를 합쳐 105,024번, 상세는 35,008번 호출됐다. 전체 호출로 나눈 선별 대기 합계는 목록 약 3.201ms/호출, 상세 약 3.594ms/호출이다. 이는 전체 HTTP 평균이나 모든 연결 대기의 평균이 아니며 임계치 미만은 빠진다. 목록의 합계가 크다는 이유만으로 목록 SQL 한 건이 가장 느리다고 결론 내리지 않는다. 스택에는 sort 인자가 없어 인기순·최신순·커서를 JFR page 경로만으로 분리하지 않았다.

### 2. 연결 획득 후에는 SQL 외 DB 통신도 존재

SocketRead 2ms 이상 표본에는 다음 경로가 있었다.

| 경로 | 목록 단일 이벤트 최대 | 상세 단일 이벤트 최대 |
| --- | --- | --- |
| 트랜잭션 시작의 setReadOnly | 24.100ms | 59.845ms |
| 커밋 경로의 setAutoCommit | 24.393ms | 22.656ms |
| 종료 정리의 setReadOnly 복원 | 20.135ms | 19.472ms |
| 쿼리 또는 기타 JDBC 통신 | 21.039ms | 20.218ms |

setReadOnly는 DataSourceUtils.prepareConnectionForTransaction과 HibernateJpaDialect.beginTransaction, 복원은 resetConnectionAfterTransaction과 doCleanupAfterCompletion 스택에서 확인됐다. 단순 SELECT 한 문장만의 실행시간이 전체 연결 점유시간이 아니다.

이 값은 임계치 이상 소켓 읽기 하나의 시간이며 전체 SQL·트랜잭션 시간은 아니다. DB 실행·네트워크·TLS·JVM 스케줄링을 분리한 값도 아니다. 임계치 미만의 많은 호출이 빠지므로 상태 설정 비용이 전체 점유의 대부분이라고 확정하지 않는다.

### 3. 로그 경합과 GC

- 목록·상세의 성능 파일 출력 경로에서 잠금 대기가 각각 최대 19.294ms·19.101ms로 기록됐다.
- 스택에 PerformanceLogAspect와 TransactionInterceptor가 함께 있으며 대상은 동기 FileAppender다. 실제 performance 파일 쓰기도 확인됐다. 파일 출력 비용이 트랜잭션 경로와 겹칠 수 있는 근거지만 이것만으로 전체 DB 대기의 주원인이라고 단정하지 않는다.
- HTTP 요청 로그의 일반 파일 출력에서도 별도 경합이 있었다. 이번 HTTP 작업 스레드 선별 자료에서 2ms 이상 콘솔 FileWrite는 잡히지 않았다. 콘솔 비용이 0이라는 의미는 아니다.
- 본 분석 구간 GC pause 356건, 합계 약 347.471ms, 단일 최대 약 2.563ms. 수십~100ms 연결 대기를 긴 GC 한 번으로 설명할 근거는 없다. 짧은 GC·스케줄링의 기여까지 배제하지 않는다.
- JFR DataLoss 0건. 비활성화한 환경변수·초기 시스템 속성·JVMInformation 이벤트도 모두 0건.
- 요청이 없어 작업 큐에서 쉬는 Tomcat 스레드는 idle_worker로 분리해 풀 대기에 합산하지 않았다.

### 판단과 다음 개선 후보

목록·상세의 주요 관측 대기 위치는 SQL 실행 전 연결 획득이다. 제한된 연결을 여러 읽기·쓰기 요청이 경쟁하며, 연결 점유 안에는 SELECT 외에 읽기 전용 설정·커밋·상태 복원과 성능 로그 경로도 있다.

12번의 조회 트랜잭션 제거 실험은 부하 결과가 나빠 원복했다. 이번 진단만으로 그 변경을 다시 채택하지 않는다. 다음 후보는 트랜잭션을 유지한 채 JDBC 상태 설정의 불필요한 왕복을 줄일 수 있는지 검증하는 것이다.

[MySQL Connector/J 공식 문서](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-performance-extensions.html)는 readOnlyPropagatesToServer가 읽기 전용 최적화를 제공하는 대신 상태 설정 왕복을 추가한다고 설명한다. 해당 옵션이나 로컬 세션 상태 최적화는 후보이며 아직 변경하지 않았다. 읽기 전용 모드의 서버 전파·검증 의미와 실제 조회·쓰기 동작을 확인한 후 동일 부하로 비교해야 한다. 모든 통신이 불필요하다고 가정하지 않는다.

성능 로그의 비동기화·트랜잭션 경로 밖 기록도 별도 후보다. 새 캐시·연결 풀 확대·애플리케이션 설정 변경은 이번 진단에 포함하지 않았다. 원인별 기여를 더 검증해야 하므로 병목 해결 완료로 기록하지 않는다.

### 자료와 재분석

로컬 Git 제외 k6/results/에 보관하며 Notion 첨부는 아니다.

- 1794760978218-599814-smoke-mixed-summary.json
- list-detail-diagnostic-200-20261115T163845Z-console.log
- list-detail-diagnostic-200-20261115T163845Z-server-metrics.jsonl
- list-detail-diagnostic-200-20261115T163845Z-analysis.json, -prometheus.json, -db-checks.json
- festival-list-detail-diagnostic-20261116.jfr
- list-detail-diagnostic-200-20261115T163845Z-jfr-summary.tsv, -examples.tsv, -gc.tsv, -event-counts.tsv

추가한 오프라인 도구 scripts/AnalyzeRestBottleneck.java는 기록을 읽기만 하며 첫 30초·이후와 page/detail 등의 경로를 구분해 합계·대표 스택을 저장한다. JVM attach나 실행 중 서버 변경은 하지 않는다.

```bash
java scripts/AnalyzeRestBottleneck.java \
  k6/results/festival-list-detail-diagnostic-20261116.jfr \
  2026-11-15T16:39:57Z 2026-11-15T16:43:00.424514Z \
  k6/results/list-detail-diagnostic-200-20261115T163845Z-jfr
```

애플리케이션 코드는 변경하지 않아 기능 테스트를 다시 실행하지 않았다. 분석 도구는 javac 컴파일 및 실제 JFR 분석으로 검증했다. 측정·설명은 개선 결과에만 추가하며 기존 기록을 보존한다.
