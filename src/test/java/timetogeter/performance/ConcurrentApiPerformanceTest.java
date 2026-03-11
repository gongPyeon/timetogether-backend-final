package timetogeter.performance;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import timetogeter.context.auth.application.dto.RegisterResponse;
import timetogeter.context.auth.domain.adaptor.UserPrincipal;
import timetogeter.context.auth.domain.vo.Provider;
import timetogeter.context.auth.domain.vo.Role;
import timetogeter.context.group.application.dto.response.*;
import timetogeter.context.group.application.service.GroupManageDisplayService;
import timetogeter.context.group.application.service.GroupManageInfoService;
import timetogeter.context.group.application.service.GroupManageMemberService;
import timetogeter.context.promise.application.dto.response.basic.*;
import timetogeter.context.promise.application.dto.response.manage.GetPromiseKey1;
import timetogeter.context.promise.application.dto.response.manage.GetPromiseKey2;
import timetogeter.context.promise.application.service.PromiseManageInfoService;
import timetogeter.context.promise.application.service.PromiseSecurityService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 다단계 암호화 API 동시 접근 성능 측정 테스트
 *
 * 측정 대상:
 * 1. 단일 API 동시 호출 (10 / 50 / 100 동시 사용자)
 * 2. 그룹 조회 3단계 플로우 동시 실행
 * 3. 약속 생성 4단계 플로우 동시 실행
 *
 * 측정 지표:
 * - 응답시간: avg, p50, p95, p99, max
 * - 처리량: TPS (Transactions Per Second)
 * - 실패율: 에러 발생 비율
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ConcurrentApiPerformanceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GroupManageDisplayService groupManageDisplayService;
    @MockBean
    private GroupManageInfoService groupManageInfoService;
    @MockBean
    private GroupManageMemberService groupManageMemberService;
    @MockBean
    private PromiseManageInfoService promiseManageInfoService;
    @MockBean
    private PromiseSecurityService promiseSecurityService;

    private static final int[] CONCURRENCY_LEVELS = {10, 50, 100};
    private static final int REQUESTS_PER_USER = 20;

    private static final List<ConcurrencyResult> allResults = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setup() throws Exception {
        // Group View Mocks
        given(groupManageDisplayService.viewGroup1(anyString()))
                .willReturn(List.of(new ViewGroup1Response(
                        "w9bd4CIDqOfc+Pl7ft2aHBawLuUvxUcvD1nATsbUYavRk7R3U0Lf76rzXRX3jFoDGfoaGw==",
                        "t5+LrD5Yp5Od+/x/JYbiCnK6QrQjkB1oGxySVcrfZt+Y/MV0VuIbHEurjBFqOG6IzxIwBzgUnio="
                )));

        given(groupManageDisplayService.viewGroup2(anyList()))
                .willReturn(List.of(new ViewGroup2Response("o7GMrSYHqKyo3ZgnI8eZL2OCWa5Nw0k/FiYc6MSYPp1JaEl8u/ocWA==")));

        given(groupManageDisplayService.viewGroup3(anyList()))
                .willReturn(List.of(new ViewGroup3Response(
                        "333571d9-a517-4e7f-94a3-d71aba508940",
                        "스터디그룹", "group_img.png", "토플 스터디 그룹", "manager_id_1",
                        List.of("FyK5/hMWlJBXsh0uh75Pmz3d5+53FDwtrA==", "Gzey+jkMlb05lvLsSlAh84ijprcznj2DqVE3hss=")
                )));

        // Promise Create Mocks
        given(promiseManageInfoService.createPromise1(anyString(), any()))
                .willReturn(new CreatePromise1Response("encGroupId_val", "encencGroupMemberId_val"));

        given(promiseManageInfoService.createPromise2(anyString(), any()))
                .willReturn(new CreatePromise2Response("encGroupKey_val"));

        given(promiseManageInfoService.createPromise3(any()))
                .willReturn(new CreatePromise3Response(
                        "333571d9-a517-4e7f-94a3-d71aba508940",
                        "스터디그룹", "group_img.png", "manager_id_1",
                        List.of("enc_user_1", "enc_user_2", "enc_user_3")
                ));

        given(promiseManageInfoService.createPromise4(anyString(), any()))
                .willReturn(new CreatePromise4Response("new_promise_id_12345"));

        given(promiseManageInfoService.getPromiseKey1(anyString()))
                .willReturn(new GetPromiseKey1(List.of("enc_promise_1", "enc_promise_2")));

        given(promiseManageInfoService.getPromiseKey2(anyString(), any()))
                .willReturn(new GetPromiseKey2("enc_promise_key_value"));

        given(groupManageInfoService.createGroup1(any(), anyString()))
                .willReturn(new CreateGroup1Response("new_group_id_12345"));

        given(groupManageInfoService.createGroup2(any(), anyString()))
                .willReturn(new CreateGroup2Response("new_group_id_12345", "새그룹", "설명", "img.png", "manager_id"));
    }

    private Authentication createAuth(String userId) {
        RegisterResponse registerResponse = new RegisterResponse(
                userId, "user_" + userId, userId + "@test.com",
                null, Provider.GENERAL, Role.USER, null, null, null
        );
        UserPrincipal userPrincipal = new UserPrincipal(registerResponse);
        return new UsernamePasswordAuthenticationToken(
                userPrincipal, null, userPrincipal.getAuthorities()
        );
    }

    // ================================================================
    // 1. 단일 API 동시 호출
    // ================================================================

    @Test
    @Order(1)
    @DisplayName("[동시성] 단일 API (Group view1) - 10/50/100 동시 사용자")
    void concurrentSingleApi() throws Exception {
        for (int concurrency : CONCURRENCY_LEVELS) {
            ConcurrencyResult result = runConcurrent(
                    "단일 API (view1)",
                    concurrency,
                    (auth) -> {
                        mockMvc.perform(get("/api/v1/group/view1")
                                        .with(authentication(auth))
                                        .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk());
                    }
            );
            allResults.add(result);
        }
    }

    // ================================================================
    // 2. 그룹 조회 3단계 플로우 동시 실행
    // ================================================================

    @Test
    @Order(2)
    @DisplayName("[동시성] 그룹 조회 3단계 (view1→2→3) - 10/50/100 동시 사용자")
    void concurrentGroupViewFlow() throws Exception {
        for (int concurrency : CONCURRENCY_LEVELS) {
            ConcurrencyResult result = runConcurrent(
                    "그룹 조회 3단계",
                    concurrency,
                    (auth) -> {
                        mockMvc.perform(get("/api/v1/group/view1")
                                        .with(authentication(auth))
                                        .contentType(MediaType.APPLICATION_JSON))
                                .andExpect(status().isOk());

                        mockMvc.perform(post("/api/v1/group/view2")
                                        .with(authentication(auth))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("[{\"groupId\":\"333571d9-a517-4e7f-94a3-d71aba508940\",\"encGroupMemberId\":\"Gzey+jkMlb05lvLsSlAh84ijprcznj2DqVE3hss=\"}]"))
                                .andExpect(status().isOk());

                        mockMvc.perform(post("/api/v1/group/view3")
                                        .with(authentication(auth))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("[{\"groupId\":\"333571d9-a517-4e7f-94a3-d71aba508940\"}]"))
                                .andExpect(status().isOk());
                    }
            );
            allResults.add(result);
        }
    }

    // ================================================================
    // 3. 약속 생성 4단계 플로우 동시 실행
    // ================================================================

    @Test
    @Order(3)
    @DisplayName("[동시성] 약속 생성 4단계 (create1→2→3→4) - 10/50/100 동시 사용자")
    void concurrentPromiseCreateFlow() throws Exception {
        for (int concurrency : CONCURRENCY_LEVELS) {
            ConcurrencyResult result = runConcurrent(
                    "약속 생성 4단계",
                    concurrency,
                    (auth) -> {
                        mockMvc.perform(post("/promise/create1")
                                        .with(authentication(auth))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"encGroupId\":\"w9bd4CIDqOfc+Pl7ft2aHBawLuUvxUcvD1nATsbUYavRk7R3U0Lf76rzXRX3jFoDGfoaGw==\"}"))
                                .andExpect(status().isOk());

                        mockMvc.perform(post("/promise/create2")
                                        .with(authentication(auth))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"groupId\":\"333571d9-a517-4e7f-94a3-d71aba508940\",\"encGroupMemberId\":\"Gzey+jkMlb05lvLsSlAh84ijprcznj2DqVE3hss=\"}"))
                                .andExpect(status().isOk());

                        mockMvc.perform(post("/promise/create3")
                                        .with(authentication(auth))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"groupId\":\"333571d9-a517-4e7f-94a3-d71aba508940\"}"))
                                .andExpect(status().isOk());

                        mockMvc.perform(post("/promise/create4")
                                        .with(authentication(auth))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"groupId\":\"333571d9-a517-4e7f-94a3-d71aba508940\",\"title\":\"토플 스터디\",\"type\":\"스터디\",\"promiseImg\":\"img.png\",\"managerId\":\"perf_test_user\",\"startDate\":\"2025-07-01\",\"endDate\":\"2025-07-31\"}"))
                                .andExpect(status().isOk());
                    }
            );
            allResults.add(result);
        }
    }

    // ================================================================
    // 4. 혼합 부하: 단일 API + 다단계 플로우 동시 실행
    // ================================================================

    @Test
    @Order(4)
    @DisplayName("[동시성] 혼합 부하 (단일 API 50% + 4단계 플로우 50%) - 50/100 동시 사용자")
    void concurrentMixedLoad() throws Exception {
        int[] mixedLevels = {50, 100};
        for (int concurrency : mixedLevels) {
            ExecutorService executor = Executors.newFixedThreadPool(concurrency);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(concurrency * REQUESTS_PER_USER);
            AtomicInteger errorCount = new AtomicInteger(0);
            List<Long> allTimes = Collections.synchronizedList(new ArrayList<>());

            // 절반은 단일 API, 절반은 4단계 플로우
            for (int i = 0; i < concurrency; i++) {
                final boolean isSingleApi = i < concurrency / 2;
                final Authentication auth = createAuth("mixed_user_" + i);

                for (int j = 0; j < REQUESTS_PER_USER; j++) {
                    executor.submit(() -> {
                        try {
                            startLatch.await();
                            long start = System.nanoTime();

                            if (isSingleApi) {
                                mockMvc.perform(get("/api/v1/group/view1")
                                                .with(authentication(auth))
                                                .contentType(MediaType.APPLICATION_JSON))
                                        .andExpect(status().isOk());
                            } else {
                                mockMvc.perform(post("/promise/create1")
                                                .with(authentication(auth))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"encGroupId\":\"w9bd4CIDqOfc+Pl7ft2aHBawLuUvxUcvD1nATsbUYavRk7R3U0Lf76rzXRX3jFoDGfoaGw==\"}"))
                                        .andExpect(status().isOk());
                                mockMvc.perform(post("/promise/create2")
                                                .with(authentication(auth))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"groupId\":\"333571d9-a517-4e7f-94a3-d71aba508940\",\"encGroupMemberId\":\"Gzey+jkMlb05lvLsSlAh84ijprcznj2DqVE3hss=\"}"))
                                        .andExpect(status().isOk());
                                mockMvc.perform(post("/promise/create3")
                                                .with(authentication(auth))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"groupId\":\"333571d9-a517-4e7f-94a3-d71aba508940\"}"))
                                        .andExpect(status().isOk());
                                mockMvc.perform(post("/promise/create4")
                                                .with(authentication(auth))
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"groupId\":\"333571d9-a517-4e7f-94a3-d71aba508940\",\"title\":\"토플\",\"type\":\"스터디\",\"promiseImg\":\"img.png\",\"managerId\":\"user\",\"startDate\":\"2025-07-01\",\"endDate\":\"2025-07-31\"}"))
                                        .andExpect(status().isOk());
                            }

                            long elapsed = (System.nanoTime() - start) / 1_000_000;
                            allTimes.add(elapsed);
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                        } finally {
                            endLatch.countDown();
                        }
                    });
                }
            }

            long totalStart = System.nanoTime();
            startLatch.countDown();
            endLatch.await(120, TimeUnit.SECONDS);
            long totalElapsed = (System.nanoTime() - totalStart) / 1_000_000;
            executor.shutdown();

            long[] times = allTimes.stream().mapToLong(Long::longValue).toArray();
            int totalRequests = concurrency * REQUESTS_PER_USER;
            double tps = totalRequests / (totalElapsed / 1000.0);

            allResults.add(new ConcurrencyResult(
                    "혼합 부하 (1단계 50% + 4단계 50%)",
                    concurrency, totalRequests, times,
                    errorCount.get(), tps, totalElapsed
            ));
        }
    }

    // ================================================================
    // 리포트 출력
    // ================================================================

    @Test
    @Order(99)
    @DisplayName("[리포트] 동시성 성능 측정 결과 출력")
    void printConcurrencyReport() {
        StringBuilder sb = new StringBuilder();

        sb.append("\n");
        sb.append("╔══════════════════════════════════════════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║                  다단계 암호화 API 동시 접근 성능 측정 결과 (Concurrency Benchmark Report)                         ║\n");
        sb.append("╠══════════════════════════════════════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║  측정 조건: 동시 사용자 %s명, 사용자당 %d회 반복, MockBean 서비스 격리                                            ║\n",
                "10/50/100", REQUESTS_PER_USER));
        sb.append("╠══════════════════════════════════════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║ %-40s │ %4s │ %6s │ %6s │ %6s │ %6s │ %6s │ %8s │ %5s ║\n",
                "API 플로우", "동시", "AVG", "P50", "P95", "P99", "MAX", "TPS", "에러"));
        sb.append("╠══════════════════════════════════════════════════════════════════════════════════════════════════════════════════╣\n");

        String currentFlow = "";
        for (ConcurrencyResult r : allResults) {
            if (!r.flowName.equals(currentFlow)) {
                if (!currentFlow.isEmpty()) {
                    sb.append("╠──────────────────────────────────────────────────────────────────────────────────────────────────────────────╣\n");
                }
                currentFlow = r.flowName;
            }
            sb.append(String.format("║ %-40s │ %4d │ %4.1fms │ %4.1fms │ %4.1fms │ %4.1fms │ %4.1fms │ %6.1f/s │ %4d ║\n",
                    r.flowName,
                    r.concurrency,
                    avg(r.times),
                    percentile(r.times, 50),
                    percentile(r.times, 95),
                    percentile(r.times, 99),
                    (double) LongStream.of(r.times).max().orElse(0),
                    r.tps,
                    r.errorCount
            ));
        }

        sb.append("╠══════════════════════════════════════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append("║                                                                                                                ║\n");
        sb.append("║  ▶ 분석                                                                                                       ║\n");

        // 단일 API vs 4단계 비교 (동일 동시 사용자 수 기준)
        for (int level : CONCURRENCY_LEVELS) {
            ConcurrencyResult singleResult = allResults.stream()
                    .filter(r -> r.flowName.contains("단일") && r.concurrency == level)
                    .findFirst().orElse(null);
            ConcurrencyResult multiResult = allResults.stream()
                    .filter(r -> r.flowName.contains("4단계") && r.concurrency == level)
                    .findFirst().orElse(null);

            if (singleResult != null && multiResult != null) {
                double ratio = avg(multiResult.times) / avg(singleResult.times);
                sb.append(String.format("║    동시 %3d명: 단일 API AVG %.1fms → 4단계 AVG %.1fms (×%.1f배), 에러율 %d/%d건                              ║\n",
                        level, avg(singleResult.times), avg(multiResult.times), ratio,
                        multiResult.errorCount, multiResult.totalRequests));
            }
        }

        sb.append("║                                                                                                                ║\n");
        sb.append("╚══════════════════════════════════════════════════════════════════════════════════════════════════════════════════╝\n");

        System.out.println(sb);

        try {
            Path outputPath = Paths.get("build/concurrency-report.txt");
            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, sb.toString());
        } catch (IOException e) {
            System.err.println("Failed to save report: " + e.getMessage());
        }
    }

    // ================================================================
    // 공통 유틸리티
    // ================================================================

    @FunctionalInterface
    interface AuthenticatedAction {
        void execute(Authentication auth) throws Exception;
    }

    private ConcurrencyResult runConcurrent(String flowName, int concurrency, AuthenticatedAction action) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrency * REQUESTS_PER_USER);
        AtomicInteger errorCount = new AtomicInteger(0);
        List<Long> times = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < concurrency; i++) {
            final Authentication auth = createAuth("user_" + i);
            for (int j = 0; j < REQUESTS_PER_USER; j++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        long start = System.nanoTime();
                        action.execute(auth);
                        long elapsed = (System.nanoTime() - start) / 1_000_000;
                        times.add(elapsed);
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }
        }

        // 모든 스레드 동시 시작
        long totalStart = System.nanoTime();
        startLatch.countDown();
        endLatch.await(120, TimeUnit.SECONDS);
        long totalElapsed = (System.nanoTime() - totalStart) / 1_000_000;
        executor.shutdown();

        long[] timeArray = times.stream().mapToLong(Long::longValue).toArray();
        int totalRequests = concurrency * REQUESTS_PER_USER;
        double tps = totalRequests / (totalElapsed / 1000.0);

        return new ConcurrencyResult(flowName, concurrency, totalRequests, timeArray, errorCount.get(), tps, totalElapsed);
    }

    private double avg(long[] times) {
        return times.length == 0 ? 0 : LongStream.of(times).average().orElse(0);
    }

    private double percentile(long[] times, int p) {
        if (times.length == 0) return 0;
        long[] sorted = LongStream.of(times).sorted().toArray();
        int index = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }

    record ConcurrencyResult(
            String flowName,
            int concurrency,
            int totalRequests,
            long[] times,
            int errorCount,
            double tps,
            long totalElapsedMs
    ) {}
}
