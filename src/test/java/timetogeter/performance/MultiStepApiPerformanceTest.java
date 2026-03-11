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
import org.springframework.test.web.servlet.MvcResult;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.LongStream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 다단계 암호화 API 성능 측정 테스트
 *
 * 측정 대상:
 * 1. 그룹 조회 플로우: view1 → view2 → view3 (3단계)
 * 2. 약속 생성 플로우: create1 → create2 → create3 → create4 (4단계)
 * 3. 단일 API 호출 기준선 (각 단계 개별 측정)
 *
 * 측정 방식:
 * - MockBean으로 서비스 계층 격리 (순수 HTTP 처리 오버헤드 측정)
 * - Warmup 50회 → 본 측정 200회
 * - 통계: avg, min, max, p50, p95, p99
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MultiStepApiPerformanceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Group services
    @MockBean
    private GroupManageDisplayService groupManageDisplayService;
    @MockBean
    private GroupManageInfoService groupManageInfoService;
    @MockBean
    private GroupManageMemberService groupManageMemberService;

    // Promise services
    @MockBean
    private PromiseManageInfoService promiseManageInfoService;
    @MockBean
    private PromiseSecurityService promiseSecurityService;

    private Authentication authentication;

    private static final int WARMUP_COUNT = 50;
    private static final int MEASURE_COUNT = 200;

    // 결과 저장용
    private static final Map<String, long[]> results = new LinkedHashMap<>();

    @BeforeEach
    void setup() throws Exception {
        // 인증 설정
        RegisterResponse registerResponse = new RegisterResponse(
                "perf_test_user", "perfuser", "perf@test.com",
                null, Provider.GENERAL, Role.USER, null, null, null
        );
        UserPrincipal userPrincipal = new UserPrincipal(registerResponse);
        authentication = new UsernamePasswordAuthenticationToken(
                userPrincipal, null, userPrincipal.getAuthorities()
        );

        // === Group View Mock 설정 ===
        given(groupManageDisplayService.viewGroup1(anyString()))
                .willReturn(List.of(
                        new ViewGroup1Response(
                                "w9bd4CIDqOfc+Pl7ft2aHBawLuUvxUcvD1nATsbUYavRk7R3U0Lf76rzXRX3jFoDGfoaGw==",
                                "t5+LrD5Yp5Od+/x/JYbiCnK6QrQjkB1oGxySVcrfZt+Y/MV0VuIbHEurjBFqOG6IzxIwBzgUnio="
                        )
                ));

        given(groupManageDisplayService.viewGroup2(anyList()))
                .willReturn(List.of(
                        new ViewGroup2Response("o7GMrSYHqKyo3ZgnI8eZL2OCWa5Nw0k/FiYc6MSYPp1JaEl8u/ocWA==")
                ));

        given(groupManageDisplayService.viewGroup3(anyList()))
                .willReturn(List.of(
                        new ViewGroup3Response(
                                "333571d9-a517-4e7f-94a3-d71aba508940",
                                "스터디그룹", "group_img.png", "토플 스터디 그룹", "manager_id_1",
                                List.of("FyK5/hMWlJBXsh0uh75Pmz3d5+53FDwtrA==",
                                        "Gzey+jkMlb05lvLsSlAh84ijprcznj2DqVE3hss=")
                        )
                ));

        // === Promise Create Mock 설정 ===
        given(promiseManageInfoService.createPromise1(anyString(), any()))
                .willReturn(new CreatePromise1Response(
                        "encGroupId_encrypted_value",
                        "encencGroupMemberId_encrypted_value"
                ));

        given(promiseManageInfoService.createPromise2(anyString(), any()))
                .willReturn(new CreatePromise2Response("encGroupKey_encrypted_value"));

        given(promiseManageInfoService.createPromise3(any()))
                .willReturn(new CreatePromise3Response(
                        "333571d9-a517-4e7f-94a3-d71aba508940",
                        "스터디그룹", "group_img.png", "manager_id_1",
                        List.of("enc_user_1", "enc_user_2", "enc_user_3")
                ));

        given(promiseManageInfoService.createPromise4(anyString(), any()))
                .willReturn(new CreatePromise4Response("new_promise_id_12345"));

        // === Promise Key Mock 설정 ===
        given(promiseManageInfoService.getPromiseKey1(anyString()))
                .willReturn(new GetPromiseKey1(List.of("enc_promise_1", "enc_promise_2")));

        given(promiseManageInfoService.getPromiseKey2(anyString(), any()))
                .willReturn(new GetPromiseKey2("enc_promise_key_value"));

        // === Group Create Mock 설정 ===
        given(groupManageInfoService.createGroup1(any(), anyString()))
                .willReturn(new CreateGroup1Response("new_group_id_12345"));

        given(groupManageInfoService.createGroup2(any(), anyString()))
                .willReturn(new CreateGroup2Response(
                        "new_group_id_12345", "새그룹", "설명", "img.png", "manager_id"
                ));
    }

    // ================================================================
    // 1. 그룹 조회 - 개별 단계 측정
    // ================================================================

    @Test
    @Order(1)
    @DisplayName("[성능] 그룹 조회 Step1 - view1 (암호화된 그룹 ID 조회)")
    void measureGroupView1() throws Exception {
        long[] times = measureEndpoint(() ->
                mockMvc.perform(get("/api/v1/group/view1")
                                .with(authentication(authentication))
                                .contentType(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk())
                        .andReturn()
        );
        results.put("Group view1 (암호화 ID 조회)", times);
    }

    @Test
    @Order(2)
    @DisplayName("[성능] 그룹 조회 Step2 - view2 (암호화된 그룹키 조회)")
    void measureGroupView2() throws Exception {
        String body = """
                [{"groupId":"333571d9-a517-4e7f-94a3-d71aba508940","encGroupMemberId":"Gzey+jkMlb05lvLsSlAh84ijprcznj2DqVE3hss="}]
                """;
        long[] times = measureEndpoint(() ->
                mockMvc.perform(post("/api/v1/group/view2")
                                .with(authentication(authentication))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andExpect(status().isOk())
                        .andReturn()
        );
        results.put("Group view2 (그룹키 조회)", times);
    }

    @Test
    @Order(3)
    @DisplayName("[성능] 그룹 조회 Step3 - view3 (그룹 상세 정보 조회)")
    void measureGroupView3() throws Exception {
        String body = """
                [{"groupId":"333571d9-a517-4e7f-94a3-d71aba508940"}]
                """;
        long[] times = measureEndpoint(() ->
                mockMvc.perform(post("/api/v1/group/view3")
                                .with(authentication(authentication))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andExpect(status().isOk())
                        .andReturn()
        );
        results.put("Group view3 (상세 정보 조회)", times);
    }

    // ================================================================
    // 2. 그룹 조회 - 전체 플로우 (3단계 연속)
    // ================================================================

    @Test
    @Order(4)
    @DisplayName("[성능] 그룹 조회 전체 플로우 (view1→view2→view3)")
    void measureGroupViewFullFlow() throws Exception {
        long[] times = measureEndpoint(() -> {
            // Step 1
            mockMvc.perform(get("/api/v1/group/view1")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            // Step 2
            mockMvc.perform(post("/api/v1/group/view2")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[{\"groupId\":\"333571d9-a517-4e7f-94a3-d71aba508940\",\"encGroupMemberId\":\"Gzey+jkMlb05lvLsSlAh84ijprcznj2DqVE3hss=\"}]"))
                    .andExpect(status().isOk());

            // Step 3
            return mockMvc.perform(post("/api/v1/group/view3")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("[{\"groupId\":\"333571d9-a517-4e7f-94a3-d71aba508940\"}]"))
                    .andExpect(status().isOk())
                    .andReturn();
        });
        results.put("Group 전체 플로우 (3단계)", times);
    }

    // ================================================================
    // 3. 약속 생성 - 개별 단계 측정
    // ================================================================

    @Test
    @Order(5)
    @DisplayName("[성능] 약속 생성 Step1 - create1 (그룹 암호화 정보 조회)")
    void measurePromiseCreate1() throws Exception {
        String body = """
                {"encGroupId":"w9bd4CIDqOfc+Pl7ft2aHBawLuUvxUcvD1nATsbUYavRk7R3U0Lf76rzXRX3jFoDGfoaGw=="}
                """;
        long[] times = measureEndpoint(() ->
                mockMvc.perform(post("/promise/create1")
                                .with(authentication(authentication))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andExpect(status().isOk())
                        .andReturn()
        );
        results.put("Promise create1 (그룹 암호화 조회)", times);
    }

    @Test
    @Order(6)
    @DisplayName("[성능] 약속 생성 Step2 - create2 (그룹키 조회)")
    void measurePromiseCreate2() throws Exception {
        String body = """
                {"groupId":"333571d9-a517-4e7f-94a3-d71aba508940","encGroupMemberId":"Gzey+jkMlb05lvLsSlAh84ijprcznj2DqVE3hss="}
                """;
        long[] times = measureEndpoint(() ->
                mockMvc.perform(post("/promise/create2")
                                .with(authentication(authentication))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andExpect(status().isOk())
                        .andReturn()
        );
        results.put("Promise create2 (그룹키 조회)", times);
    }

    @Test
    @Order(7)
    @DisplayName("[성능] 약속 생성 Step3 - create3 (그룹원 목록 조회)")
    void measurePromiseCreate3() throws Exception {
        String body = """
                {"groupId":"333571d9-a517-4e7f-94a3-d71aba508940"}
                """;
        long[] times = measureEndpoint(() ->
                mockMvc.perform(post("/promise/create3")
                                .with(authentication(authentication))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andExpect(status().isOk())
                        .andReturn()
        );
        results.put("Promise create3 (그룹원 조회)", times);
    }

    @Test
    @Order(8)
    @DisplayName("[성능] 약속 생성 Step4 - create4 (약속 최종 생성)")
    void measurePromiseCreate4() throws Exception {
        String body = """
                {
                    "groupId":"333571d9-a517-4e7f-94a3-d71aba508940",
                    "title":"토플 스터디",
                    "type":"스터디",
                    "promiseImg":"img.png",
                    "managerId":"perf_test_user",
                    "startDate":"2025-07-01",
                    "endDate":"2025-07-31"
                }
                """;
        long[] times = measureEndpoint(() ->
                mockMvc.perform(post("/promise/create4")
                                .with(authentication(authentication))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andExpect(status().isOk())
                        .andReturn()
        );
        results.put("Promise create4 (약속 생성)", times);
    }

    // ================================================================
    // 4. 약속 생성 - 전체 플로우 (4단계 연속)
    // ================================================================

    @Test
    @Order(9)
    @DisplayName("[성능] 약속 생성 전체 플로우 (create1→create2→create3→create4)")
    void measurePromiseCreateFullFlow() throws Exception {
        long[] times = measureEndpoint(() -> {
            // Step 1
            mockMvc.perform(post("/promise/create1")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"encGroupId\":\"w9bd4CIDqOfc+Pl7ft2aHBawLuUvxUcvD1nATsbUYavRk7R3U0Lf76rzXRX3jFoDGfoaGw==\"}"))
                    .andExpect(status().isOk());

            // Step 2
            mockMvc.perform(post("/promise/create2")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"groupId\":\"333571d9-a517-4e7f-94a3-d71aba508940\",\"encGroupMemberId\":\"Gzey+jkMlb05lvLsSlAh84ijprcznj2DqVE3hss=\"}"))
                    .andExpect(status().isOk());

            // Step 3
            mockMvc.perform(post("/promise/create3")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"groupId\":\"333571d9-a517-4e7f-94a3-d71aba508940\"}"))
                    .andExpect(status().isOk());

            // Step 4
            return mockMvc.perform(post("/promise/create4")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"groupId\":\"333571d9-a517-4e7f-94a3-d71aba508940\",\"title\":\"토플 스터디\",\"type\":\"스터디\",\"promiseImg\":\"img.png\",\"managerId\":\"perf_test_user\",\"startDate\":\"2025-07-01\",\"endDate\":\"2025-07-31\"}"))
                    .andExpect(status().isOk())
                    .andReturn();
        });
        results.put("Promise 전체 플로우 (4단계)", times);
    }

    // ================================================================
    // 5. 약속키 조회 플로우 (2단계)
    // ================================================================

    @Test
    @Order(10)
    @DisplayName("[성능] 약속키 조회 전체 플로우 (promisekey1→promisekey2)")
    void measurePromiseKeyFullFlow() throws Exception {
        long[] times = measureEndpoint(() -> {
            // Step 1
            mockMvc.perform(post("/promise/promisekey1")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            // Step 2
            return mockMvc.perform(post("/promise/promisekey2")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"promiseId\":\"promise_id_123\",\"encUserId\":\"enc_user_id_value\"}"))
                    .andExpect(status().isOk())
                    .andReturn();
        });
        results.put("PromiseKey 전체 플로우 (2단계)", times);
    }

    // ================================================================
    // 6. 그룹 생성 플로우 (2단계)
    // ================================================================

    @Test
    @Order(11)
    @DisplayName("[성능] 그룹 생성 전체 플로우 (new1→new2)")
    void measureGroupCreateFullFlow() throws Exception {
        long[] times = measureEndpoint(() -> {
            // Step 1
            mockMvc.perform(post("/api/v1/group/new1")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"groupName\":\"토플스터디\",\"groupExplain\":\"토플 105점 목표\",\"groupImg\":\"img.png\"}"))
                    .andExpect(status().isOk());

            // Step 2
            return mockMvc.perform(post("/api/v1/group/new2")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"groupId\":\"new_group_id_12345\",\"encGroupId\":\"enc_group_id\",\"encencGroupMemberId\":\"enc_enc_member\",\"encUserId\":\"enc_user\",\"encGroupKey\":\"enc_key\"}"))
                    .andExpect(status().isOk())
                    .andReturn();
        });
        results.put("Group 생성 플로우 (2단계)", times);
    }

    // ================================================================
    // 최종 리포트 출력
    // ================================================================

    @Test
    @Order(99)
    @DisplayName("[리포트] 성능 측정 결과 출력")
    void printPerformanceReport() {
        StringBuilder sb = new StringBuilder();

        sb.append("\n");
        sb.append("╔══════════════════════════════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║                    다단계 암호화 API 성능 측정 결과 (Performance Benchmark Report)                      ║\n");
        sb.append("╠══════════════════════════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║  측정 조건: Warmup %d회, 본 측정 %d회, MockBean 서비스 격리                                          ║\n", WARMUP_COUNT, MEASURE_COUNT));
        sb.append("╠══════════════════════════════════════════════════════════════════════════════════════════════════════╣\n");

        sb.append(String.format("║ %-42s │ %6s │ %6s │ %6s │ %6s │ %6s │ %6s ║\n",
                "API 엔드포인트", "AVG", "MIN", "MAX", "P50", "P95", "P99"));
        sb.append("╠══════════════════════════════════════════════════════════════════════════════════════════════════════╣\n");

        // 개별 단계 결과
        sb.append("║ --- 개별 단계 (Single Step) ---------------------------------------------------------------------- ║\n");
        for (Map.Entry<String, long[]> entry : results.entrySet()) {
            if (entry.getKey().contains("전체 플로우")) continue;
            printRow(sb, entry.getKey(), entry.getValue());
        }

        sb.append("╠══════════════════════════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append("║ --- 전체 플로우 (Full Flow) ---------------------------------------------------------------------- ║\n");
        for (Map.Entry<String, long[]> entry : results.entrySet()) {
            if (!entry.getKey().contains("전체 플로우")) continue;
            printRow(sb, entry.getKey(), entry.getValue());
        }

        sb.append("╠══════════════════════════════════════════════════════════════════════════════════════════════════════╣\n");

        // 비교 분석
        sb.append("║                                                                                                    ║\n");
        sb.append("║  ▶ 비교 분석                                                                                       ║\n");

        long[] groupFlow = results.get("Group 전체 플로우 (3단계)");
        long[] promiseFlow = results.get("Promise 전체 플로우 (4단계)");
        long[] promiseKeyFlow = results.get("PromiseKey 전체 플로우 (2단계)");
        long[] groupCreateFlow = results.get("Group 생성 플로우 (2단계)");

        // 개별 단계 평균 합산
        long[] gv1 = results.get("Group view1 (암호화 ID 조회)");
        long[] gv2 = results.get("Group view2 (그룹키 조회)");
        long[] gv3 = results.get("Group view3 (상세 정보 조회)");

        if (gv1 != null && gv2 != null && gv3 != null && groupFlow != null) {
            double stepSum = avg(gv1) + avg(gv2) + avg(gv3);
            double flowAvg = avg(groupFlow);
            sb.append(String.format("║  [그룹 조회] 단계별 합산 AVG: %.2fms vs 연속 플로우 AVG: %.2fms (오버헤드: %.2fms)           ║\n",
                    stepSum, flowAvg, flowAvg - stepSum));
        }

        long[] pc1 = results.get("Promise create1 (그룹 암호화 조회)");
        long[] pc2 = results.get("Promise create2 (그룹키 조회)");
        long[] pc3 = results.get("Promise create3 (그룹원 조회)");
        long[] pc4 = results.get("Promise create4 (약속 생성)");

        if (pc1 != null && pc2 != null && pc3 != null && pc4 != null && promiseFlow != null) {
            double stepSum = avg(pc1) + avg(pc2) + avg(pc3) + avg(pc4);
            double flowAvg = avg(promiseFlow);
            sb.append(String.format("║  [약속 생성] 단계별 합산 AVG: %.2fms vs 연속 플로우 AVG: %.2fms (오버헤드: %.2fms)           ║\n",
                    stepSum, flowAvg, flowAvg - stepSum));
        }

        sb.append("║                                                                                                    ║\n");

        // 단일 API 대비 비교
        if (gv1 != null && groupFlow != null) {
            double singleAvg = avg(gv1); // 단일 API 기준선
            sb.append("║  ▶ 단일 API 호출 대비 다단계 플로우 비교 (단일 API 기준: Group view1)                                 ║\n");
            sb.append(String.format("║    단일 API AVG:          %6.2fms                                                                ║\n", singleAvg));
            if (groupFlow != null) {
                sb.append(String.format("║    그룹 조회(3단계) AVG:   %6.2fms  (×%.1f배)                                                     ║\n",
                        avg(groupFlow), avg(groupFlow) / singleAvg));
            }
            if (promiseFlow != null) {
                sb.append(String.format("║    약속 생성(4단계) AVG:   %6.2fms  (×%.1f배)                                                     ║\n",
                        avg(promiseFlow), avg(promiseFlow) / singleAvg));
            }
        }

        sb.append("║                                                                                                    ║\n");
        sb.append("║  ▶ 결론                                                                                            ║\n");

        if (promiseFlow != null) {
            double flowP95 = percentile(promiseFlow, 95);
            if (flowP95 < 100) {
                sb.append("║    4단계 다단계 API의 P95 응답시간이 100ms 미만으로, 보안 강화에 따른 성능 영향 미미               ║\n");
            } else {
                sb.append(String.format("║    4단계 다단계 API의 P95 응답시간: %.2fms                                                        ║\n", flowP95));
            }
        }

        sb.append("╚══════════════════════════════════════════════════════════════════════════════════════════════════════╝\n");

        System.out.println(sb);

        // 파일로도 저장
        try {
            Path outputPath = Paths.get("build/performance-report.txt");
            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, sb.toString());
            System.out.println("Report saved to: " + outputPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to save report: " + e.getMessage());
        }
    }

    // ================================================================
    // 측정 유틸리티
    // ================================================================

    @FunctionalInterface
    interface MockMvcAction {
        MvcResult execute() throws Exception;
    }

    private long[] measureEndpoint(MockMvcAction action) throws Exception {
        // Warmup
        for (int i = 0; i < WARMUP_COUNT; i++) {
            action.execute();
        }

        // Measure
        long[] times = new long[MEASURE_COUNT];
        for (int i = 0; i < MEASURE_COUNT; i++) {
            long start = System.nanoTime();
            action.execute();
            long end = System.nanoTime();
            times[i] = (end - start) / 1_000_000; // ns → ms
        }

        return times;
    }

    private void printRow(StringBuilder sb, String name, long[] times) {
        if (times == null) return;
        sb.append(String.format("║ %-42s │ %4.2fms │ %4dms │ %4dms │ %4.2fms │ %4.2fms │ %4.2fms ║\n",
                name,
                avg(times),
                LongStream.of(times).min().orElse(0),
                LongStream.of(times).max().orElse(0),
                percentile(times, 50),
                percentile(times, 95),
                percentile(times, 99)
        ));
    }

    private double avg(long[] times) {
        return LongStream.of(times).average().orElse(0);
    }

    private double percentile(long[] times, int p) {
        long[] sorted = LongStream.of(times).sorted().toArray();
        int index = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(index, sorted.length - 1))];
    }
}
