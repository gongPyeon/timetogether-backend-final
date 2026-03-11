package timetogeter.context.group.presentation.controller;

import com.epages.restdocs.apispec.ResourceSnippetParameters;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import timetogeter.context.auth.application.dto.RegisterResponse;
import timetogeter.context.auth.application.dto.RegisterUserCommand;
import timetogeter.context.auth.domain.adaptor.UserPrincipal;
import timetogeter.context.auth.domain.entity.User;
import timetogeter.context.auth.domain.vo.Gender;
import timetogeter.context.auth.domain.vo.Provider;
import timetogeter.context.auth.domain.vo.Role;
import timetogeter.context.group.application.dto.request.*;
import timetogeter.context.group.application.dto.response.*;
import timetogeter.context.group.application.service.GroupManageDisplayService;
import timetogeter.context.group.application.service.GroupManageInfoService;
import timetogeter.context.group.application.service.GroupManageMemberService;
import timetogeter.global.RestDocsSupport;

import java.util.List;

import static com.epages.restdocs.apispec.ResourceDocumentation.resource;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class GroupManageControllerTest extends RestDocsSupport {

    @MockBean
    private GroupManageInfoService groupManageInfoService;

    @MockBean
    private GroupManageDisplayService groupManageDisplayService;

    @MockBean
    private GroupManageMemberService groupManageMemberService;

    @Autowired
    private ObjectMapper objectMapper;

    private Authentication authentication;

    @BeforeEach
    void setupAuthentication() {
        RegisterUserCommand dto = new RegisterUserCommand(
                "xpxp_id_1", "xpxp@example.com",
                "010-1234-5678", "xpxp", Provider.GENERAL, Role.USER, "18", Gender.FEMALE, null, null, null
        );
        User user = new User(dto);
        RegisterResponse registerResponse = RegisterResponse.from(user);
        UserPrincipal userPrincipal = new UserPrincipal(registerResponse);

        authentication = new UsernamePasswordAuthenticationToken(
                userPrincipal, null, userPrincipal.getAuthorities()
        );
    }

    @Nested
    @DisplayName("사용자의 그룹 정보 조회 API (/api/v1/group/view)")
    class ViewGroup1 {

        @Test
        @DisplayName("✅ 사용자가 속한 그룹의 encencGroupMemberId 정보를 조회할 수 있다. (/api/v1/group/view1)")
        @WithMockUser
        void testViewGroup1() throws Exception {
            // given
            RegisterUserCommand dto = new RegisterUserCommand(
                    "ImManager", "immanager@example.com",
                    "010-1234-5678", "ImManager", Provider.GENERAL, Role.USER, "25", Gender.FEMALE, null, null, null
            );
            User user = new User(dto);
            RegisterResponse registerResponse = RegisterResponse.from(user);
            UserPrincipal userPrincipal = new UserPrincipal(registerResponse);

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userPrincipal, null, userPrincipal.getAuthorities()
            );

            List<ViewGroup1Response> responseList = List.of(
                    new ViewGroup1Response(
                            "w9bd4CIDqOfc+Pl7ft2aHBawLuUvxUcvD1nATsbUYavRk7R3U0Lf76rzXRX3jFoDGfoaGw==",
                            "t5+LrD5Yp5Od+/x/JYbiCnK6QrQjkB1oGxySVcrfZt+Y/MV0VuIbHEurjBFqOG6IzxIwBzgUnio="
                    )
            );

            given(groupManageDisplayService.viewGroup1(userPrincipal.getId()))
                    .willReturn(responseList);

            // when, then
            mockMvc.perform(post("/api/v1/group/view1")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("요청에 성공했습니다."))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].encGroupId").value("w9bd4CIDqOfc+Pl7ft2aHBawLuUvxUcvD1nATsbUYavRk7R3U0Lf76rzXRX3jFoDGfoaGw=="))
                    .andExpect(jsonPath("$.data[0].encencGroupMemberId").value("t5+LrD5Yp5Od+/x/JYbiCnK6QrQjkB1oGxySVcrfZt+Y/MV0VuIbHEurjBFqOG6IzxIwBzgUnio="))
                    .andDo(restDocs.document(
                            resource(
                                    ResourceSnippetParameters.builder()
                                            .tag("그룹 관련 API")
                                            .description("encGroupId, encencGroupMemberId 그룹 정보 조회 성공")
                                            .responseFields(
                                                    fieldWithPath("code").type(JsonFieldType.NUMBER).description("응답 코드 (200)"),
                                                    fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                                    fieldWithPath("data[].encGroupId").type(JsonFieldType.STRING).description("암호화된 그룹 ID"),
                                                    fieldWithPath("data[].encencGroupMemberId").type(JsonFieldType.STRING).description("이중 암호화된 그룹 멤버 ID")
                                            )
                                            .build()
                            )
                    ));
        }

        @Test
        @DisplayName("✅ 사용자가 속한 그룹의 encGroupKey 정보를 조회할 수 있다. (/api/v1/group/view2)")
        @WithMockUser
        void testViewGroup2() throws Exception {
            // given
            ViewGroup2Request requestDto = new ViewGroup2Request(
                    "333571d9-a517-4e7f-94a3-d71aba508940",
                    "Gzey+jkMlb05lvLsSlAh84ijprcznj2DqVE3hss="
            );

            ViewGroup2Response responseDto = new ViewGroup2Response(
                    "o7GMrSYHqKyo3ZgnI8eZL2OCWa5Nw0k/FiYc6MSYPp1JaEl8u/ocWA=="
            );

            given(groupManageDisplayService.viewGroup2(anyList()))
                    .willReturn(List.of(responseDto));

            // when & then
            mockMvc.perform(post("/api/v1/group/view2")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                        [
                          {
                            "groupId": "333571d9-a517-4e7f-94a3-d71aba508940",
                            "encGroupMemberId": "Gzey+jkMlb05lvLsSlAh84ijprcznj2DqVE3hss="
                          }
                        ]
                        """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("요청에 성공했습니다."))
                    .andExpect(jsonPath("$.data[0].encGroupKey")
                            .value("o7GMrSYHqKyo3ZgnI8eZL2OCWa5Nw0k/FiYc6MSYPp1JaEl8u/ocWA=="))
                    .andDo(restDocs.document(
                            resource(
                                    ResourceSnippetParameters.builder()
                                            .tag("그룹 관련 API")
                                            .description("사용자가 속한 그룹의 encGroupKey 정보를 조회한다.")
                                            .requestFields(
                                                    fieldWithPath("[].groupId").type(JsonFieldType.STRING).description("그룹 ID"),
                                                    fieldWithPath("[].encGroupMemberId").type(JsonFieldType.STRING).description("암호화된 그룹 멤버 ID")
                                            )
                                            .responseFields(
                                                    fieldWithPath("code").type(JsonFieldType.NUMBER).description("응답 코드"),
                                                    fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                                    fieldWithPath("data[].encGroupKey").type(JsonFieldType.STRING).description("암호화된 그룹 키")
                                            )
                                            .build()
                            )
                    ));
        }

        @Test
        @DisplayName("✅ 사용자가 속한 그룹들과 그의 정보를 조회할 수 있다. (/api/v1/group/view3)")
        @WithMockUser
        void testViewGroup3() throws Exception {
            // given

            ViewGroup3Response responseDto = new ViewGroup3Response(
                    "333571d9-a517-4e7f-94a3-d71aba508940",
                    "toefl(수정됨제목)",
                    "toefl(수정됨이미지)",
                    "토플 스터디 설명",
                    "xpxp_id_1",
                    List.of(
                            "FyK5/hMWlJBXsh0uh75Pmz3d5+53FDwtrA==",
                            "Gzey+jkMlb05lvLsSlAh84ijprcznj2DqVE3hss="
                    )
            );

            given(groupManageDisplayService.viewGroup3(anyList()))
                    .willReturn(List.of(responseDto));

            RegisterUserCommand dto = new RegisterUserCommand(
                    "xpxp_id_1", "xpxp@example.com",
                    "010-1234-5678", "xpxp", Provider.GENERAL, Role.USER, "18", Gender.FEMALE, null, null, null
            );

            User user = new User(dto);
            RegisterResponse registerResponse = RegisterResponse.from(user);
            UserPrincipal userPrincipal = new UserPrincipal(registerResponse);

            // when & then
            mockMvc.perform(post("/api/v1/group/view3")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                        [
                          {
                            "groupId": "333571d9-a517-4e7f-94a3-d71aba508940"
                          }
                        ]
                        """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("요청에 성공했습니다."))
                    .andExpect(jsonPath("$.data[0].groupId").value("333571d9-a517-4e7f-94a3-d71aba508940"))
                    .andExpect(jsonPath("$.data[0].groupName").value("toefl(수정됨제목)"))
                    .andExpect(jsonPath("$.data[0].groupImg").value("toefl(수정됨이미지)"))
                    .andExpect(jsonPath("$.data[0].managerId").value("xpxp_id_1"))
                    .andExpect(jsonPath("$.data[0].encUserId[0]").value("FyK5/hMWlJBXsh0uh75Pmz3d5+53FDwtrA=="))
                    .andExpect(jsonPath("$.data[0].encUserId[1]").value("Gzey+jkMlb05lvLsSlAh84ijprcznj2DqVE3hss="))
                    .andDo(restDocs.document(
                            resource(
                                    ResourceSnippetParameters.builder()
                                            .tag("그룹 관련 API")
                                            .description("사용자가 속한 그룹들과 그의 정보를 조회한다.")
                                            .requestFields(
                                                    fieldWithPath("[].groupId").type(JsonFieldType.STRING).description("그룹 ID")
                                            )
                                            .responseFields(
                                                    fieldWithPath("code").type(JsonFieldType.NUMBER).description("응답 코드"),
                                                    fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                                    fieldWithPath("data[].groupId").type(JsonFieldType.STRING).description("그룹 ID"),
                                                    fieldWithPath("data[].groupName").type(JsonFieldType.STRING).description("그룹 이름"),
                                                    fieldWithPath("data[].groupImg").type(JsonFieldType.STRING).description("그룹 이미지"),
                                                    fieldWithPath("data[].managerId").type(JsonFieldType.STRING).description("그룹 관리자 ID"),
                                                    fieldWithPath("data[].encUserId[]").type(JsonFieldType.ARRAY).description("암호화된 사용자 ID 목록")
                                            )
                                            .build()
                            )
                    ));
        }
    }

    @Nested
    @DisplayName("사용자의 그룹 만들기 API (/api/v1/group/new)")
    class CreateGroup1 {

        @Test
        @DisplayName("✅ 예비 방장이 그룹을 생성할 수 있다. (/api/v1/group/new1)")
        @WithMockUser
        void testCreateGroup1() throws Exception {
            // given
            String groupId = "333571d9-a517-4e7f-94a3-d71aba508940";

            CreateGroup1Request requestDto = new CreateGroup1Request(
                    "toefl",
                    "토플 읽기",
                    "토플 이미지"
            );

            CreateGroup1Response responseDto = new CreateGroup1Response(groupId);

            given(groupManageInfoService.createGroup1(any(CreateGroup1Request.class), anyString()))
                    .willReturn(responseDto);

            RegisterUserCommand dto = new RegisterUserCommand(
                    "xpxp_id_1", "xpxp@example.com",
                    "010-1234-5678", "xpxp", Provider.GENERAL, Role.USER, "18", Gender.FEMALE, null, null, null
            );

            User user = new User(dto);
            RegisterResponse registerResponse = RegisterResponse.from(user);
            UserPrincipal userPrincipal = new UserPrincipal(registerResponse);

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userPrincipal, null, userPrincipal.getAuthorities()
            );

            // when & then
            mockMvc.perform(post("/api/v1/group/new1")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                        {
                          "groupName" : "toefl",
                          "groupExplain" : "토플 읽기",
                          "groupImg" : "토플 이미지",
                          "explain" : "토플 목표 점수 105"
                        }
                        """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("요청에 성공했습니다."))
                    .andExpect(jsonPath("$.data.groupId").value(groupId))
                    .andDo(restDocs.document(
                            resource(
                                    ResourceSnippetParameters.builder()
                                            .tag("그룹 관련 API")
                                            .description("예비 방장이 그룹을 생성할 수 있다.")
                                            .requestFields(
                                                    fieldWithPath("groupName").type(JsonFieldType.STRING).description("그룹 이름"),
                                                    fieldWithPath("groupExplain").type(JsonFieldType.STRING).description("그룹 설명"),
                                                    fieldWithPath("groupImg").type(JsonFieldType.STRING).description("그룹 이미지"),
                                                    fieldWithPath("explain").type(JsonFieldType.STRING).description("목표 설명")
                                            )
                                            .responseFields(
                                                    fieldWithPath("code").type(JsonFieldType.NUMBER).description("응답 코드"),
                                                    fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                                    fieldWithPath("data.groupId").type(JsonFieldType.STRING).description("생성된 그룹 ID")
                                            )
                                            .build()
                            )
                    ));
        }

        @Test
        @DisplayName("✅ 방장이 생성한 그룹 정보를 조회할 수 있다.  (/api/v1/group/new2)")
        @WithMockUser
        void testCreateGroup2() throws Exception {
            // given
            String groupId = "333571d9-a517-4e7f-94a3-d71aba508940";

            CreateGroup2Request requestDto = new CreateGroup2Request(
                    "333571d9-a517-4e7f-94a3-d71aba508940",
                    "HcFINyWax1NGlY99f3b4M81aFPIveL7Uct7k2j3wq6pSFMQi76jivIPVPGJoxbT6l2QCCQ==",
                    "aIswNz3D7j0HvvgUOzP8I5ILDJt2Y76dI8LgiBnV6e4YbM0vyc++dZSNG7Le7oIuRZ7UGw==",
                    "FyK5/hMWlJBXsh0uh75Pmz3d5+53FDwtrA==",
                    "faYZeiGexxgysO4hImz7ALhoY7lNfrDEiC5hgfyjywe4B0TDqTFSmQ=="
            );


            CreateGroup2Response responseDto = new CreateGroup2Response(
                    "333571d9-a517-4e7f-94a3-d71aba508940",
                    "toefl",
                    "토플 읽기",
                    "토플 이미지",
                    "xpxp_id_1"
            );

            given(groupManageInfoService.createGroup2(any(CreateGroup2Request.class), anyString()))
                    .willReturn(responseDto);

            RegisterUserCommand dto = new RegisterUserCommand(
                    "xpxp_id_1", "user2@example.com",
                    "010-9876-5432", "xpxp", Provider.GENERAL, Role.USER, "10", Gender.MALE, null, null, null
            );

            User user = new User(dto);
            RegisterResponse registerResponse = RegisterResponse.from(user);
            UserPrincipal userPrincipal = new UserPrincipal(registerResponse);

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userPrincipal, null, userPrincipal.getAuthorities()
            );

            // when & then
            mockMvc.perform(post("/api/v1/group/new2")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                        {
                          "groupId" : "333571d9-a517-4e7f-94a3-d71aba508940",
                          "encGroupId": "HcFINyWax1NGlY99f3b4M81aFPIveL7Uct7k2j3wq6pSFMQi76jivIPVPGJoxbT6l2QCCQ==",
                          "encencGroupMemberId": "aIswNz3D7j0HvvgUOzP8I5ILDJt2Y76dI8LgiBnV6e4YbM0vyc++dZSNG7Le7oIuRZ7UGw==",
                          "encUserId": "FyK5/hMWlJBXsh0uh75Pmz3d5+53FDwtrA==",
                          "encGroupKey": "faYZeiGexxgysO4hImz7ALhoY7lNfrDEiC5hgfyjywe4B0TDqTFSmQ=="
                        }
                        """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("요청에 성공했습니다."))
                    .andExpect(jsonPath("$.data.groupId").value("333571d9-a517-4e7f-94a3-d71aba508940"))
                    .andExpect(jsonPath("$.data.groupName").value("toefl"))
                    .andExpect(jsonPath("$.data.groupExplain").value("토플 읽기"))
                    .andExpect(jsonPath("$.data.groupImg").value("토플 이미지"))
                    .andExpect(jsonPath("$.data.managerId").value("xpxp_id_1"))
                    .andExpect(jsonPath("$.data.groupId").value(groupId))
                    .andDo(restDocs.document(
                            resource(
                                    ResourceSnippetParameters.builder()
                                            .tag("그룹 관련 API")
                                            .description("예비 방장이 그룹을 생성할 수 있다. (두 번째 API)")
                                            .requestFields(
                                                    fieldWithPath("groupId").type(JsonFieldType.STRING).description("그룹 ID"),
                                                    fieldWithPath("encGroupId").type(JsonFieldType.STRING).description("암호화된 그룹 ID"),
                                                    fieldWithPath("encencGroupMemberId").type(JsonFieldType.STRING).description("암호화된 그룹 멤버 ID"),
                                                    fieldWithPath("encUserId").type(JsonFieldType.STRING).description("암호화된 사용자 ID"),
                                                    fieldWithPath("encGroupKey").type(JsonFieldType.STRING).description("암호화된 그룹 키")
                                            )
                                            .responseFields(
                                                    fieldWithPath("code").type(JsonFieldType.NUMBER).description("응답 코드"),
                                                    fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                                    fieldWithPath("data.groupId").type(JsonFieldType.STRING).description("생성된 그룹 ID"),
                                                    fieldWithPath("data.groupName").type(JsonFieldType.STRING).description("그룹 이름"),
                                                    fieldWithPath("data.groupExplain").type(JsonFieldType.STRING).description("그룹 설명"),
                                                    fieldWithPath("data.groupImg").type(JsonFieldType.STRING).description("그룹 이미지"),
                                                    fieldWithPath("data.managerId").type(JsonFieldType.STRING).description("그룹 관리자 ID")
                                            )
                                            .build()
                            )
                    ));

        }

        @Test
        @DisplayName("✅ 방장이 그룹 정보를 수정할 수 있다.  (/api/v1/group/edit1)")
        @WithMockUser
        void testEditGroup1() throws Exception {
            // given
            String encencGroupMemberId = "aIswNz3D7j0HvvgUOzP8I5ILDJt2Y76dI8LgiBnV6e4YbM0vyc++dZSNG7Le7oIuRZ7UGw==";

            // 서비스에서 반환할 응답 DTO
            EditGroup1Response responseDto = new EditGroup1Response(encencGroupMemberId);

            given(groupManageInfoService.editGroup1(any(EditGroup1Request.class), anyString()))
                    .willReturn(responseDto);

            // 사용자 인증 principal 세팅
            RegisterUserCommand dto = new RegisterUserCommand(
                    "xpxp_id_1", "user2@example.com",
                    "010-9876-5432", "xpxp", Provider.GENERAL, Role.USER, "10", Gender.MALE, null, null, null
            );
            User user = new User(dto);
            RegisterResponse registerResponse = RegisterResponse.from(user);
            UserPrincipal userPrincipal = new UserPrincipal(registerResponse);

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userPrincipal, null, userPrincipal.getAuthorities()
            );

            // when & then
            mockMvc.perform(post("/api/v1/group/edit1")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                {
                    "groupId": "333571d9-a517-4e7f-94a3-d71aba508940",
                    "encGroupId": "HcFINyWax1NGlY99f3b4M81aFPIveL7Uct7k2j3wq6pSFMQi76jivIPVPGJoxbT6l2QCCQ==",
                    "groupName" : "toefl(수정됨제목)",
                    "groupImg" : "toefl(수정됨이미지)",
                    "description" : ""
                }
            """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("요청에 성공했습니다."))
                    .andExpect(jsonPath("$.data.encencGroupMemberId").value(encencGroupMemberId))
                    .andDo(restDocs.document(
                            resource(
                                    ResourceSnippetParameters.builder()
                                            .tag("그룹 관련 API")
                                            .description("방장이 그룹 정보를 수정할 수 있다.")
                                            .requestFields(
                                                    fieldWithPath("groupId").type(JsonFieldType.STRING).description("그룹 ID"),
                                                    fieldWithPath("encGroupId").type(JsonFieldType.STRING).description("암호화된 그룹 ID"),
                                                    fieldWithPath("groupName").type(JsonFieldType.STRING).description("그룹 이름"),
                                                    fieldWithPath("groupImg").type(JsonFieldType.STRING).description("그룹 이미지"),
                                                    fieldWithPath("description").type(JsonFieldType.STRING).description("그룹 설명")
                                            )
                                            .responseFields(
                                                    fieldWithPath("code").type(JsonFieldType.NUMBER).description("응답 코드"),
                                                    fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                                    fieldWithPath("data.encencGroupMemberId").type(JsonFieldType.STRING).description("암호화된 그룹 멤버 ID")
                                            )
                                            .build()
                            )
                    ));
        }

        @Test
        @DisplayName("✅ 그룹원이 그룹에 초대받을 수 있다.  (/api/v1/group/join1)")
        @WithMockUser
        void testInviteGroup1() throws Exception {
            // given
            String message = "toefl(수정됨제목)에 참여했어요.";
            JoinGroupResponse responseDto = new JoinGroupResponse(message);

            given(groupManageMemberService.joinGroup(any(JoinGroupRequest.class), anyString()))
                    .willReturn(responseDto);

            RegisterUserCommand dto = new RegisterUserCommand(
                    "xpxp_id_1", "user@example.com",
                    "010-1234-5678", "xpxp", Provider.GENERAL, Role.USER, "10", Gender.MALE, null, null, null
            );
            User user = new User(dto);
            UserPrincipal userPrincipal = new UserPrincipal(RegisterResponse.from(user));
            Authentication authentication = new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());

            // when & then
            mockMvc.perform(post("/api/v1/group/join1")
                            .with(authentication(authentication))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                        {
                            "randomUUID": "d0205893-8fa4-46b4-b030-84d1fa3e3c7e",
                            "groupId": "333571d9-a517-4e7f-94a3-d71aba508940",
                            "encGroupId": "HcFINyWax1NGlY99f3b4M81aFPIveL7Uct7k2j3wq6pSFMQi76jivIPVPGJoxbT6l2QCCQ==",
                            "encGroupKey": "faYZeiGexxgysO4hImz7ALhoY7lNfrDEiC5hgfyjywe4B0TDqTFSmQ==",
                            "encUserId": "FyK5/hMWlJBXsh0uh75Pmz3d5+53FDwtrA==",
                            "encencGroupMemberId": "aIswNz3D7j0HvvgUOzP8I5ILDJt2Y76dI8LgiBnV6e4YbM0vyc++dZSNG7Le7oIuRZ7UGw=="
                        }
                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.message").value("요청에 성공했습니다."))
                    .andExpect(jsonPath("$.data.message").value(message))
                    .andDo(print())
                    .andDo(restDocs.document(
                            resource(
                                    ResourceSnippetParameters.builder()
                                            .tag("그룹 관련 API")
                                            .description("그룹원이 그룹에 초대받을 수 있다.")
                                            .requestFields(
                                                    fieldWithPath("randomUUID").type(JsonFieldType.STRING).description("초대 고유 UUID"),
                                                    fieldWithPath("groupId").type(JsonFieldType.STRING).description("그룹 ID"),
                                                    fieldWithPath("encGroupId").type(JsonFieldType.STRING).description("암호화된 그룹 ID"),
                                                    fieldWithPath("encGroupKey").type(JsonFieldType.STRING).description("암호화된 그룹 키"),
                                                    fieldWithPath("encUserId").type(JsonFieldType.STRING).description("그룹키로 암호화된 사용자 ID"),
                                                    fieldWithPath("encencGroupMemberId").type(JsonFieldType.STRING).description("개인키로 암호화된 사용자 ID")
                                            )
                                            .responseFields(
                                                    fieldWithPath("code").type(JsonFieldType.NUMBER).description("응답 코드"),
                                                    fieldWithPath("message").type(JsonFieldType.STRING).description("응답 메시지"),
                                                    fieldWithPath("data.message").type(JsonFieldType.STRING).description("그룹 참여 완료 메시지")
                                            )
                                            .build()
                            )
                    ));
        }

    }


}


