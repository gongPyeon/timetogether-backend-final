package timetogeter.global.support;

import timetogeter.context.auth.domain.entity.User;
import timetogeter.context.auth.domain.vo.Gender;
import timetogeter.context.auth.domain.vo.Provider;
import timetogeter.context.auth.domain.vo.Role;
import timetogeter.context.auth.application.dto.RegisterUserCommand;

public class UserFixture {
    private static final String USER_ID = "test";
    private static final String USER_NICKNAME = "test";
    private static final String USER_IMG = "img";
    private static final String USER_PWD = "pwd";
    private static final String USER_EMAIL = "test@konkuk.ac.kr";
    private static final String USER_PHONE = "010-1234-5678";
    private static final Provider USER_PROVIDER = Provider.GOOGLE;
    private static final Role USER_ROLE = Role.USER;
    private static final String USER_AGE = "20-30";
    private static final Gender USER_GENDER = Gender.MALE;

    public static User userById(String userId){
        return new User(new RegisterUserCommand(userId, USER_EMAIL, USER_PHONE,
                USER_NICKNAME, USER_PROVIDER, USER_ROLE, USER_AGE, USER_GENDER, null, null, null));
    }
    public static User userByRegisterCommand(RegisterUserCommand registerUserCommand){
        return new User(registerUserCommand);
    }

    public static RegisterUserCommand registerUserCommand(String userId){
        return new RegisterUserCommand(userId, USER_EMAIL, USER_PHONE,
                USER_NICKNAME, USER_PROVIDER, USER_ROLE, USER_AGE, USER_GENDER, null, null, null);
    }
}
