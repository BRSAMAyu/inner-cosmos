package com.innercosmos.vo;

import com.innercosmos.entity.User;

public class UserProfileVO {
    public Long id;
    public String username;
    public String nickname;
    public String role;

    public static UserProfileVO from(User user) {
        UserProfileVO vo = new UserProfileVO();
        vo.id = user.id;
        vo.username = user.username;
        vo.nickname = user.nickname;
        vo.role = user.role;
        return vo;
    }
}
