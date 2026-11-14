package com.example.backend;

import com.example.backend.guestbook.application.port.in.GuestbookCommandUseCase;
import com.example.backend.guestbook.application.port.out.GuestbookQueryPort;
import com.example.backend.guestbook.application.result.EntryView;
import com.example.backend.guestbook.domain.model.GuestbookCursor;
import com.example.backend.guestbook.domain.model.GuestbookSort;
import com.example.backend.member.application.command.SignupCommand;
import com.example.backend.member.application.port.in.MemberCommandUseCase;
import com.example.backend.member.application.port.out.AccessTokenPort;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability(tracing = false)
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class BackendApplicationTests {
    private static final String JWT_TEST_SECRET = Base64.getEncoder().encodeToString(new byte[32]);
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("TEST_DB_URL");
        if (!url.matches("jdbc:mysql://[^/]+/eulji_festival_test(?:[?].*)?")) {
            throw new IllegalArgumentException("Integration tests require a dedicated eulji_festival_test database");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DB_USERNAME", "root"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", ""));
        registry.add("security.jwt-secret", () -> JWT_TEST_SECRET);
        registry.add("security.jwt-issuer", () -> "eulji-academic-festival");
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired MemberCommandUseCase members;
    @Autowired GuestbookCommandUseCase likes;
    @Autowired GuestbookQueryPort queries;
    @Autowired AccessTokenPort tokens;
    @Autowired com.example.backend.member.application.port.in.AuthenticateMemberUseCase authentication;
    @Autowired io.micrometer.core.instrument.MeterRegistry metrics;
    @Autowired javax.sql.DataSource dataSource;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.example.backend.guestbook.adapter.out.persistence.GuestbookPersistenceAdapter persistence;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.example.backend.member.adapter.out.security.AccessTokenAdapter tokenAdapter;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.example.backend.guestbook.adapter.out.persistence.GuestbookQueryAdapter queryAdapter;
    @Autowired com.example.backend.guestbook.application.port.in.GuestbookQueryUseCase cachedQueries;

    @BeforeEach
    void cleanTestDatabase() {
        jdbc.update("DELETE FROM access_tokens");
        jdbc.update("DELETE FROM guestbook_likes");
        jdbc.update("DELETE FROM guestbook_entries");
        jdbc.update("DELETE FROM members");
    }
    private String signupBody(String email, String message) throws com.fasterxml.jackson.core.JsonProcessingException {
        return json.writeValueAsString(Map.of("email", email, "password", "pw", "characterId", 3,
                "name", "김을지", "grade", "게스트", "guestbookMessage", message));
    }
    private long signup(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/signup").contentType("application/json").content(signupBody(email, "환영합니다")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("AUTH_SIGNUP_201"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("memberId").asLong();
    }
    private String login(String email) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("email", email, "password", "pw"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("AUTH_LOGIN_200"))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("accessToken").asText();
    }

    @Test
    void logsAreCorrelatedAndRoutedToApplicationErrorAndPerformanceFiles() throws Exception {
        long id = fixture("logging@example.com");
        String token = tokens.issue(id);
        var response = mvc.perform(get("/api/characters").param("memberIds", Long.toString(id))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse();
        String traceId = response.getHeader("X-Trace-Id");
        assertThat(traceId).matches("[a-f0-9]{32}");
        var context = (ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory();
        var root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        var application = (ch.qos.logback.core.FileAppender<?>) root.getAppender("FILE");
        var errors = (ch.qos.logback.core.FileAppender<?>) root.getAppender("ERROR_FILE");
        var performance = (ch.qos.logback.core.FileAppender<?>) context
                .getLogger("com.example.backend.global.infrastructure.aop.PerformanceLogAspect")
                .getAppender("PERFORMANCE_FILE");
        String normalMarker = "normal-marker-" + UUID.randomUUID();
        String errorMarker = "error-marker-" + UUID.randomUUID();
        var logger = org.slf4j.LoggerFactory.getLogger("com.example.backend.logging.Check");
        logger.info(normalMarker);
        logger.error(errorMarker);
        String applicationLog = java.nio.file.Files.readString(java.nio.file.Path.of(application.getFile()));
        String errorLog = java.nio.file.Files.readString(java.nio.file.Path.of(errors.getFile()));
        String performanceLog = java.nio.file.Files.readString(java.nio.file.Path.of(performance.getFile()));
        assertThat(applicationLog).contains(traceId, "[/api/characters]", "event=http_request", normalMarker, errorMarker)
                .doesNotContain(token);
        assertThat(errorLog).contains(errorMarker).doesNotContain(normalMarker);
        assertThat(performanceLog).contains(traceId, "event=performance", "GuestbookQueryService.labels");
        assertThat(org.slf4j.MDC.get("traceId")).isNull();
    }

    @Test
    void prometheusMetricsArePublicWhileOtherProtectedEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("jvm_memory_used_bytes")));
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/guestbook")).andExpect(status().isUnauthorized());
    }

    @Test
    void swaggerIsPublicAndDocumentsBearerAuthenticationAndAllNineOperations() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("swagger-ui")));
        mvc.perform(get("/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
        String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("을지 학술제 API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andReturn().getResponse().getContentAsString();
        JsonNode paths = json.readTree(body).path("paths");
        int operations = 0;
        for (var path : paths.properties()) {
            assertThat(path.getKey()).startsWith("/api/");
            for (var operation : path.getValue().properties()) {
                if (!Set.of("get", "post", "put", "delete").contains(operation.getKey())) continue;
                operations++;
                JsonNode security = operation.getValue().path("security");
                if (path.getKey().startsWith("/api/auth/")) assertThat(security.isMissingNode() || security.isEmpty()).isTrue();
                else assertThat(security.get(0).has("bearerAuth")).isTrue();
                for (JsonNode parameter : operation.getValue().path("parameters")) {
                    assertThat(parameter.path("name").asText()).isNotIn("member", "authMember");
                }
            }
        }
        assertThat(operations).isEqualTo(9);
        assertThat(paths.path("/api/auth/signup").path("post").path("responses").has("201")).isTrue();
        mvc.perform(get("/api/guestbook")).andExpect(status().isUnauthorized());
    }

    @Test
    void signupLoginAndAuthorizationFollowTheContract() throws Exception {
        long id = signup("visitor@example.com");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM guestbook_entries WHERE member_id = ?", Long.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT password_hash FROM members WHERE id = ?", String.class, id)).isNotEqualTo("pw");
        mvc.perform(post("/api/auth/signup").contentType("application/json").content(signupBody("VISITOR@example.com", "환영")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AUTH_DUPLICATE_MEMBER"));
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"email\":\"visitor@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
        mvc.perform(get("/api/guestbook")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_401_001"));
        mvc.perform(get("/api/guestbook").header("Authorization", "Bearer forged"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_401_001"));
        mvc.perform(get("/api/guestbook").header("Authorization", "Basic ignored"))
                .andExpect(status().isUnauthorized());
        String token = login("visitor@example.com");
        assertThat(token.split("\\.")).hasSize(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM access_tokens", Long.class)).isZero();
        mvc.perform(get("/api/guestbook").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.entries[0].memberId").value(id));
        mvc.perform(get("/api/guestbook").header("Authorization", "Bearer " + expiredToken(id)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH_401_001"));
    }

    @Test
    void authenticationVerifiesJwtWithoutStartingATransaction() throws Exception {
        long id = fixture("auth-boundary@example.com");
        String token = tokens.issue(id);
        org.mockito.Mockito.doAnswer(invocation -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
                    .as("JWT authentication must not start a service transaction")
                    .isFalse();
            return invocation.callRealMethod();
        }).when(tokenAdapter).authenticate(token);
        mvc.perform(get("/api/characters").param("memberIds", String.valueOf(id))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.characters[0].memberId").value(id));
    }

    @Test
    void jwtAuthenticationIsIndependentOfLegacyTokensAndMemberDeletion() throws Exception {
        long id = fixture("auth-state@example.com");
        long target = fixture("auth-target@example.com");
        String token = tokens.issue(id);
        var requestPath = "/api/characters/" + target;
        mvc.perform(get(requestPath).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        // Stateless JWTs remain valid until expiry; legacy rows and member deletion do not revoke them.
        jdbc.update("DELETE FROM access_tokens");
        mvc.perform(get(requestPath).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        jdbc.update("DELETE FROM members WHERE id = ?", id);
        mvc.perform(get(requestPath).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        for (String header : List.of("Bearer ", "Bearer invalid", "Bearer " + "A".repeat(43), "Basic invalid")) {
            mvc.perform(get(requestPath).header("Authorization", header)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void jwtIssueAndAuthenticationDoNotAcquireDatabaseConnections() {
        var timers = metrics.find("hikaricp.connections.acquire").timers();
        assertThat(timers).isNotEmpty();
        long before = timers.stream().mapToLong(io.micrometer.core.instrument.Timer::count).sum();
        String token = tokens.issue(42);
        for (int i = 0; i < 10; i++) {
            assertThat(authentication.authenticate(token)).contains(42L);
            assertThat(authentication.authenticate("invalid")).isEmpty();
        }
        long after = timers.stream().mapToLong(io.micrometer.core.instrument.Timer::count).sum();
        assertThat(after).as("JWT issue/verification must not borrow a DB connection").isEqualTo(before);
    }

    private String expiredToken(long memberId) {
        var clock = java.time.Clock.fixed(java.time.Instant.now().minusSeconds(7200), java.time.ZoneOffset.UTC);
        return new com.example.backend.member.adapter.out.security.AccessTokenAdapter(clock, java.time.Duration.ofHours(1),
                JWT_TEST_SECRET, "eulji-academic-festival").issue(memberId);
    }

    private long connectionAcquisitions() {
        var timers = metrics.find("hikaricp.connections.acquire").timers();
        assertThat(timers).isNotEmpty();
        return timers.stream().mapToLong(io.micrometer.core.instrument.Timer::count).sum();
    }

    @Test
    void labelCacheHitsSkipTransactionsAndConnectionsAndMissesUseOneBatch() throws Exception {
        long a = fixture("label-a@example.com"), b = fixture("label-b@example.com");
        String auth = "Bearer " + tokens.issue(a);
        mvc.perform(get("/api/characters").param("memberIds", b + "," + a + "," + a).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.characters.length()").value(2))
                .andExpect(jsonPath("$.data.characters[0].memberId").value(a));
        long before = connectionAcquisitions();
        mvc.perform(get("/api/characters").param("memberIds", a + "," + b).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.characters[1].memberId").value(b));
        assertThat(connectionAcquisitions()).isEqualTo(before);
        long c = fixture("label-c@example.com"), d = fixture("label-d@example.com");
        org.mockito.Mockito.clearInvocations(queryAdapter);
        before = connectionAcquisitions();
        mvc.perform(get("/api/characters").param("memberIds", d + "," + a + "," + c + "," + b).header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.characters.length()").value(4));
        assertThat(connectionAcquisitions()).isEqualTo(before + 1);
        org.mockito.Mockito.verify(queryAdapter).labels(List.of(c, d));
    }

    @Test
    void profileCommitInvalidatesLabelsOnlyAfterCommit() throws Exception {
        long id = fixture("label-commit@example.com");
        assertThat(cachedQueries.labels(List.of(id)).get(0).name()).isEqualTo("이름");
        ExecutorService reader = Executors.newSingleThreadExecutor();
        try {
            new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                members.updateProfile(new com.example.backend.member.application.command.UpdateProfileCommand(id, 1, "수정 이름", "졸업생", "수정"));
                try { assertThat(reader.submit(() -> cachedQueries.labels(List.of(id)).get(0).name()).get(5, TimeUnit.SECONDS)).isEqualTo("이름"); }
                catch (Exception e) { throw new RuntimeException(e); }
            });
        } finally { reader.shutdownNow(); }
        var label = cachedQueries.labels(List.of(id)).get(0);
        assertThat(label.name()).isEqualTo("수정 이름");
        assertThat(label.grade()).isEqualTo("졸업생");
        long before = connectionAcquisitions();
        cachedQueries.labels(List.of(id));
        assertThat(connectionAcquisitions()).isEqualTo(before);
    }

    @Test
    void transactionalLabelReadsBypassCacheAndRollbackDoesNotEvictOrPublish() {
        long id = fixture("label-rollback@example.com");
        cachedQueries.labels(List.of(id));
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            members.updateProfile(new com.example.backend.member.application.command.UpdateProfileCommand(id, 1, "미커밋 이름", "게스트", "수정"));
            assertThat(cachedQueries.labels(List.of(id)).get(0).name()).isEqualTo("미커밋 이름");
            status.setRollbackOnly();
        });
        long before = connectionAcquisitions();
        assertThat(cachedQueries.labels(List.of(id)).get(0).name()).isEqualTo("이름");
        assertThat(connectionAcquisitions()).isEqualTo(before);
    }

    @Test
    void unchangedNameAndGradeDoNotEvictLabelCache() {
        long id = fixture("label-message-only@example.com");
        cachedQueries.labels(List.of(id));
        members.updateProfile(new com.example.backend.member.application.command.UpdateProfileCommand(id, 6, "이름", "게스트", "내용만 수정"));
        long before = connectionAcquisitions();
        assertThat(cachedQueries.labels(List.of(id)).get(0).name()).isEqualTo("이름");
        assertThat(connectionAcquisitions()).isEqualTo(before);
    }

    @Test
    void unicodeValidationAndMalformedBodiesNeverCreatePartialAccounts() throws Exception {
        mvc.perform(post("/api/auth/signup").contentType("application/json").content(signupBody("bad@example.com", "x".repeat(101))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AUTH_INVALID_INPUT"));
        mvc.perform(post("/api/auth/signup").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AUTH_INVALID_INPUT"));
        mvc.perform(post("/api/auth/signup").contentType("application/json").content(signupBody("bad@example.com", "환영").replace("\"characterId\":3", "\"characterId\":1.5")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/signup").contentType("application/json").content(signupBody("bad@example.com", "환영").replace("\"characterId\":3", "\"characterId\":\"3\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/signup").contentType("application/json").content(signupBody("bad@example.com", "환영").replace("\"password\":\"pw\"", "\"password\":123")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AUTH_INVALID_INPUT"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM members", Long.class)).isZero();
        mvc.perform(post("/api/auth/signup").contentType("application/json")
                        .content(signupBody("emoji@example.com", "👨‍👩‍👧‍👦".repeat(100))))
                .andExpect(status().isCreated());
    }

    @Test
    void signupRollsBackWhenGuestbookStorageFails() {
        jdbc.execute("CREATE TRIGGER reject_guestbook BEFORE INSERT ON guestbook_entries FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'test storage failure'");
        try {
            assertThatThrownBy(() -> members.signup(new SignupCommand("rollback@example.com", "pw", 1, "이름", "졸업생", "환영")))
                    .isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM members", Long.class)).isZero();
        } finally { jdbc.execute("DROP TRIGGER reject_guestbook"); }
    }

    @Test
    void concurrentSignupCreatesExactlyOneMemberAndGuestbook() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<String> signup = () -> {
            start.await();
            try {
                members.signup(new SignupCommand("race@example.com", "pw", 1, "이름", "게스트", "환영"));
                return "created";
            } catch (com.example.backend.global.domain.exception.BusinessException ex) {
                return ex.error().name();
            }
        };
        try {
            Future<String> first = pool.submit(signup);
            Future<String> second = pool.submit(signup);
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("created", "AUTH_DUPLICATE_MEMBER");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM members", Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM guestbook_entries", Long.class)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void profileStorageFailureRollsBackMemberChanges() throws Exception {
        long id = signup("rollback-profile@example.com");
        String token = login("rollback-profile@example.com");
        jdbc.execute("CREATE TRIGGER reject_guestbook_update BEFORE UPDATE ON guestbook_entries FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'test storage failure'");
        try {
            mvc.perform(put("/api/members/me/profile").header("Authorization", "Bearer " + token)
                            .contentType("application/json")
                            .content("{\"characterId\":5,\"name\":\"변경이름\",\"grade\":\"졸업생\",\"guestbookMessage\":\"새 본문\"}"))
                    .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("COMMON_500"));
            assertThat(jdbc.queryForObject("SELECT name FROM members WHERE id = ?", String.class, id)).isEqualTo("김을지");
            assertThat(jdbc.queryForObject("SELECT message FROM guestbook_entries WHERE member_id = ?", String.class, id))
                    .isEqualTo("환영합니다");
        } finally { jdbc.execute("DROP TRIGGER reject_guestbook_update"); }
    }

    @Test
    void likesAreIdempotentAndProfileEditsPreserveLikesAndCreatedAt() throws Exception {
        long target = signup("author@example.com");
        signup("viewer@example.com");
        String viewerToken = login("viewer@example.com");
        String authorToken = login("author@example.com");
        var createdAt = jdbc.queryForObject("SELECT created_at FROM guestbook_entries WHERE member_id = ?", java.sql.Timestamp.class, target);
        for (int i = 0; i < 2; i++) {
            mvc.perform(put("/api/guestbook/" + target + "/like").header("Authorization", "Bearer " + viewerToken))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.likeCount").value(1))
                    .andExpect(jsonPath("$.data.likedByMe").value(true));
        }
        String profile = "{\"characterId\":5,\"name\":\"수정이름\",\"grade\":\"3학년\",\"guestbookMessage\":\"  새 방명록  \"}";
        mvc.perform(put("/api/members/me/profile").header("Authorization", "Bearer " + authorToken)
                        .contentType("application/json").content(profile))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value("MEMBER_PROFILE_UPDATE_200"))
                .andExpect(jsonPath("$.data.likeCount").value(1)).andExpect(jsonPath("$.data.guestbookMessage").value("새 방명록"));
        assertThat(jdbc.queryForObject("SELECT created_at FROM guestbook_entries WHERE member_id = ?", java.sql.Timestamp.class, target)).isEqualTo(createdAt);
        mvc.perform(get("/api/characters").param("memberIds", target + ",999999").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.characters.length()").value(1))
                .andExpect(jsonPath("$.data.characters[0].name").value("수정이름"));
        mvc.perform(get("/api/characters/" + target).header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.likeCount").value(1))
                .andExpect(jsonPath("$.data.rank").value(1)).andExpect(jsonPath("$.data.guestbookMessage").value("새 방명록"));
        mvc.perform(put("/api/members/me/profile").header("Authorization", "Bearer " + authorToken)
                        .contentType("application/json").content(profile.replace("3학년", "5학년")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MEMBER_PROFILE_INVALID_INPUT"));
        assertThat(jdbc.queryForObject("SELECT name FROM members WHERE id = ?", String.class, target)).isEqualTo("수정이름");
        mvc.perform(get("/api/guestbook").header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.entries[0].likedByMe").value(true));
        for (int i = 0; i < 2; i++) {
            mvc.perform(delete("/api/guestbook/" + target + "/like").header("Authorization", "Bearer " + viewerToken))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.likeCount").value(0))
                    .andExpect(jsonPath("$.data.likedByMe").value(false));
        }
    }

    private long fixture(String email) {
        jdbc.update("INSERT INTO members (email, password_hash, character_id, name, grade) VALUES (?, 'unused', 1, '이름', '게스트')", email);
        Long id = jdbc.queryForObject("SELECT id FROM members WHERE email = ?", Long.class, email);
        jdbc.update("INSERT INTO guestbook_entries (member_id, message, created_at) VALUES (?, '환영', '2026-11-10 04:30:00.123456')", id);
        return id;
    }
    @Test
    void concurrentLikesAndUnlikesMaintainExactCounts() throws Exception {
        long target = fixture("author@example.com");
        List<Long> viewers = new ArrayList<>();
        for (int i = 0; i < 8; i++) viewers.add(fixture("v" + i + "@example.com"));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (long viewer : viewers) for (int i = 0; i < 3; i++) {
                tasks.add(() -> { likes.setLike(target, viewer, true); return null; });
            }
            for (var future : pool.invokeAll(tasks)) future.get(15, TimeUnit.SECONDS);
            assertLikeCount(target, 8);
            tasks.clear();
            for (long viewer : viewers) for (int i = 0; i < 3; i++) {
                tasks.add(() -> { likes.setLike(target, viewer, false); return null; });
            }
            for (var future : pool.invokeAll(tasks)) future.get(15, TimeUnit.SECONDS);
            assertLikeCount(target, 0);
        } finally { pool.shutdownNow(); }
    }
    private void assertLikeCount(long target, long expected) {
        assertThat(jdbc.queryForObject("SELECT like_count FROM guestbook_entries WHERE member_id = ?", Long.class, target)).isEqualTo(expected);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM guestbook_likes WHERE entry_member_id = ?", Long.class, target)).isEqualTo(expected);
    }

    @Test
    void likesOnDifferentEntriesDoNotDeadlockOnAnEmptyIndexGap() throws Exception {
        long first = fixture("first@example.com");
        long second = fixture("second@example.com");
        long viewer = fixture("viewer@example.com");
        long otherViewer = fixture("other@example.com");
        CountDownLatch inserting = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            inserting.countDown();
            return invocation.callRealMethod();
        }).when(persistence).add(first, viewer);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (var guard = dataSource.getConnection()) {
            guard.setAutoCommit(false);
            Future<?> request = null;
            try (var statement = guard.createStatement()) {
                statement.executeQuery("SELECT member_id FROM guestbook_entries WHERE member_id = " + second + " FOR UPDATE").close();
                statement.executeQuery("SELECT liker_member_id FROM guestbook_likes WHERE entry_member_id = " + second
                        + " AND liker_member_id = " + otherViewer + " FOR UPDATE").close();
                request = worker.submit(() -> likes.setLike(first, viewer, true));
                assertThat(inserting.await(5, TimeUnit.SECONDS)).isTrue();
                statement.executeUpdate("INSERT INTO guestbook_likes (entry_member_id, liker_member_id) VALUES (" + second + ", " + otherViewer + ")");
                statement.executeUpdate("UPDATE guestbook_entries SET like_count = 1 WHERE member_id = " + second);
                guard.commit();
                request.get(15, TimeUnit.SECONDS);
            } finally {
                guard.rollback();
                if (request != null) {
                    try { request.get(15, TimeUnit.SECONDS); }
                    catch (ExecutionException ignored) { /* Original failure is reported above. */ }
                }
            }
        } finally { worker.shutdownNow(); }
        assertLikeCount(first, 1);
        assertLikeCount(second, 1);
    }

    @Test
    void mixedProfileAndLikeWritesKeepCountsConsistent() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 12; i++) ids.add(fixture("mixed" + i + "@example.com"));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (long viewer : ids) tasks.add(() -> {
                for (long target : ids) {
                    likes.setLike(target, viewer, true);
                    likes.setLike(target, viewer, true);
                    members.updateProfile(new com.example.backend.member.application.command.UpdateProfileCommand(
                            viewer, 1, "이름", "게스트", "수정"));
                    likes.setLike(target, viewer, false);
                    likes.setLike(target, viewer, false);
                }
                return null;
            });
            for (var future : pool.invokeAll(tasks)) future.get(20, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        for (long id : ids) assertLikeCount(id, 0);
    }

    @Test
    void duplicateLikeUsesCurrentStateEvenAfterAnOlderTransactionSnapshot() throws Exception {
        long target = fixture("snapshot-target@example.com");
        long viewer = fixture("snapshot-viewer@example.com");
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                assertLikeCount(target, 0); // Establish a REPEATABLE READ snapshot.
                try { worker.submit(() -> likes.setLike(target, viewer, true)).get(10, TimeUnit.SECONDS); }
                catch (Exception failure) { throw new RuntimeException(failure); }
                assertThat(likes.setLike(target, viewer, true).likeCount()).isEqualTo(1);
            });
        } finally { worker.shutdownNow(); }
        assertLikeCount(target, 1);
    }

    @Test
    void invalidLikerRollsBackInsteadOfBeingTreatedAsADuplicate() {
        long target = fixture("fk-target@example.com");
        assertThatThrownBy(() -> likes.setLike(target, Long.MAX_VALUE, true))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertLikeCount(target, 0);
    }

    @Test
    void rankingsIncludeAllTiesAndCursorPaginationUsesFullSortKeys() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) ids.add(fixture("m" + i + "@example.com"));
        String token = tokens.issue(ids.get(0));
        // All six zero-like entries are jointly first, not limited to three rows.
        mvc.perform(get("/api/rankings/top").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rankings.length()").value(6))
                .andExpect(jsonPath("$.data.rankings[5].rank").value(1));
        mvc.perform(get("/api/guestbook").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries[0].createdAt").value("2026-11-10T04:30:00.123456Z"));
        likes.setLike(ids.get(0), ids.get(4), true);
        likes.setLike(ids.get(0), ids.get(5), true);
        likes.setLike(ids.get(1), ids.get(4), true);
        likes.setLike(ids.get(2), ids.get(4), true);
        mvc.perform(get("/api/rankings/top").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rankings.length()").value(3))
                .andExpect(jsonPath("$.data.rankings[0].rank").value(1))
                .andExpect(jsonPath("$.data.rankings[1].rank").value(2))
                .andExpect(jsonPath("$.data.rankings[2].rank").value(2));
        mvc.perform(get("/api/characters/" + ids.get(3)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rank").value(org.hamcrest.Matchers.nullValue()));
        for (String sort : List.of("popular", "latest")) {
            String cursor = null;
            List<Long> seen = new ArrayList<>();
            do {
                var request = get("/api/guestbook").param("sort", sort).param("limit", "2").header("Authorization", "Bearer " + token);
                if (cursor != null) request.param("cursor", cursor);
                String body = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                JsonNode data = json.readTree(body).path("data");
                data.path("entries").forEach(entry -> seen.add(entry.path("memberId").asLong()));
                cursor = data.path("nextCursor").isNull() ? null : data.path("nextCursor").asText();
            } while (cursor != null && seen.size() <= 10);
            assertThat(seen).containsExactlyElementsOf(ids);
        }
        String body = mvc.perform(get("/api/guestbook").param("limit", "1").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        String popularCursor = json.readTree(body).path("data").path("nextCursor").asText();
        mvc.perform(get("/api/guestbook").param("sort", "latest").param("cursor", popularCursor).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("GUESTBOOK_INVALID_QUERY"));
    }

    @Test
    void endpointSpecificValidationAndEmptyResults() throws Exception {
        long id = fixture("viewer@example.com");
        String auth = "Bearer " + tokens.issue(id);
        mvc.perform(get("/api/characters").header("Authorization", auth))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CHARACTER_INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("회원 ID 목록을 확인해 주세요."));
        mvc.perform(get("/api/characters").param("memberIds", "1,,2").header("Authorization", auth))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("회원 ID 목록을 확인해 주세요."));
        mvc.perform(get("/api/characters").param("memberIds", "999999").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.characters").isEmpty());
        mvc.perform(get("/api/characters/999999").header("Authorization", auth))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CHARACTER_NOT_FOUND"));
        mvc.perform(get("/api/characters/abc").header("Authorization", auth))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CHARACTER_INVALID_INPUT"));
        mvc.perform(put("/api/guestbook/999999/like").header("Authorization", auth))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GUESTBOOK_NOT_FOUND"));
        mvc.perform(put("/api/guestbook/0/like").header("Authorization", auth))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("GUESTBOOK_INVALID_MEMBER_ID"));
        for (String limit : List.of("0", "101", "abc", "")) {
            mvc.perform(get("/api/guestbook").param("limit", limit).header("Authorization", auth))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("GUESTBOOK_INVALID_QUERY"));
        }
        mvc.perform(get("/api/guestbook").param("sort", "unknown").header("Authorization", auth))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("GUESTBOOK_INVALID_QUERY"));
        jdbc.update("DELETE FROM guestbook_entries");
        mvc.perform(get("/api/guestbook").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.entries").isEmpty())
                .andExpect(jsonPath("$.data.nextCursor").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/api/rankings/top").header("Authorization", auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rankings").isEmpty());
    }

    @Test
    void optimizedQueriesMatchFullGlobalRankingAcrossTiesAndPages() {
        List<int[]> distributions = List.of(new int[0], new int[]{0}, new int[]{5, 5},
                new int[]{0, 0, 0, 0, 0, 0}, new int[]{9, 9, 9, 8, 7},
                new int[]{9, 9, 8, 8, 8, 7}, new int[]{9, 8, 8, 8, 7},
                new int[]{9, 8, 7, 7, 7, 0},
                new Random(20261115).ints(120, 0, 20).toArray());
        for (int[] counts : distributions) {
            cleanTestDatabase();
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < counts.length; i++) {
                long id = fixture("query" + i + "@example.com");
                ids.add(id);
                // Tied scores, tied times, and different creation dates exercise every cursor key.
                jdbc.update("UPDATE guestbook_entries SET like_count = ?, created_at = ? WHERE member_id = ?",
                        counts[i], java.time.LocalDateTime.of(2026, 11, 10, 0, 0).plusSeconds(i % 4), id);
            }
            long viewer = ids.isEmpty() ? 0 : ids.get(0);
            for (int i = 1; i < ids.size(); i += 2) {
                jdbc.update("INSERT INTO guestbook_likes (entry_member_id, liker_member_id) VALUES (?, ?)", ids.get(i), viewer);
            }
            List<EntryView> global = fullRankingReference(0, GuestbookSort.POPULAR);
            assertThat(queries.topRankings()).containsExactlyElementsOf(global.stream().filter(e -> e.rank() != null).toList());
            assertThat(queries.detail(Long.MAX_VALUE)).isEmpty();
            for (EntryView entry : global) assertThat(queries.detail(entry.memberId())).contains(entry);
            for (GuestbookSort sort : GuestbookSort.values()) {
                List<EntryView> expected = fullRankingReference(viewer, sort);
                for (int size : List.of(1, 3, 21, 100)) {
                    GuestbookCursor cursor = null;
                    int offset = 0;
                    while (true) {
                        List<EntryView> page = queries.page(viewer, sort, cursor, size);
                        assertThat(page).containsExactlyElementsOf(expected.subList(offset, Math.min(offset + size, expected.size())));
                        if (page.isEmpty()) break;
                        offset += page.size();
                        EntryView last = page.get(page.size() - 1);
                        cursor = new GuestbookCursor(sort, last.likeCount(), last.createdAt(), last.memberId());
                    }
                    assertThat(offset).isEqualTo(expected.size());
                }
            }
        }
    }

    private List<EntryView> fullRankingReference(long viewer, GuestbookSort sort) {
        // Independent reference: rank ALL rows before filtering/pagination, as the original contract requires.
        String sql = """
                WITH ranked AS (
                    SELECT member_id, message, like_count, created_at,
                           RANK() OVER (ORDER BY like_count DESC) AS global_rank
                    FROM guestbook_entries
                )
                SELECT m.id, m.character_id, m.grade, m.name, r.message, r.created_at, r.like_count,
                       CASE WHEN r.global_rank <= 3 THEN r.global_rank ELSE NULL END AS member_rank,
                       EXISTS(SELECT 1 FROM guestbook_likes l WHERE l.entry_member_id = m.id
                              AND l.liker_member_id = ?) AS liked_by_me
                FROM ranked r JOIN members m ON m.id = r.member_id
                ORDER BY
                """ + (sort == GuestbookSort.POPULAR ? "r.like_count DESC, " : "") + "r.created_at DESC, r.member_id ASC";
        return jdbc.query(sql, (rs, row) -> {
            Number rank = (Number) rs.getObject("member_rank");
            return new EntryView(rs.getLong("id"), rs.getInt("character_id"), rs.getString("grade"), rs.getString("name"),
                    rank == null ? null : rank.intValue(), rs.getString("message"),
                    rs.getObject("created_at", java.time.LocalDateTime.class).toInstant(java.time.ZoneOffset.UTC),
                    rs.getLong("like_count"), rs.getBoolean("liked_by_me"));
        }, viewer);
    }
}
