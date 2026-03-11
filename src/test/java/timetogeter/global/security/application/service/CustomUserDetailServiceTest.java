package timetogeter.global.security.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import timetogeter.context.auth.application.service.UserRegisterService;
import timetogeter.context.auth.domain.vo.Role;
import timetogeter.context.auth.application.dto.RegisterResponse;
import timetogeter.context.auth.exception.AuthFailureException;
import timetogeter.context.auth.exception.UserNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailServiceTest {

    @InjectMocks
    private CustomUserDetailService customUserDetailService;

    @Mock
    private UserRegisterService userRegisterService;

    @Nested
    @DisplayName("CustomUserDetailService 실행 시")
    class returnUserDetailTest{
        @Test
        @DisplayName("성공: UserDetails 반환")
        void success(){
            // given
            String userId = "test";
            RegisterResponse response = new RegisterResponse(userId, "test", "test@konkuk.co.kr", "pwd", null, Role.USER, null, null, null);
            when(userRegisterService.getRegisterUser(userId)).thenReturn(response);

            // when
            UserDetails result = customUserDetailService.loadUserByUsername(userId);

            // then
            verify(userRegisterService).getRegisterUser(userId);
            assertThat(result.getUsername()).isEqualTo("test");
        }

        @Test
        @DisplayName("예외: 등록되지 않은 사용자일 경우")
        void throwUsernameNotFoundException(){
            // given
            String userId = "none";
            when(userRegisterService.getRegisterUser(userId)).thenThrow(UserNotFoundException.class);

            // when & given
            assertThrows(UserNotFoundException.class,
                    () -> customUserDetailService.loadUserByUsername(userId));
        }

        @Test
        @DisplayName("예외: null을 반환할 경우")
        void throwNullException(){
            // given
            String userId = "none";
            when(userRegisterService.getRegisterUser(userId)).thenReturn(null);

            // when & given
            assertThrows(AuthFailureException.class,
                    () -> customUserDetailService.loadUserByUsername(userId));
        }
    }

}