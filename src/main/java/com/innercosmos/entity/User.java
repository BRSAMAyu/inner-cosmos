package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_user")
public class User extends BaseEntity {
    public String username;
    public String passwordHash;
    public String nickname;
    public String avatarUrl;
    public String email;
    public String role;
    public String status;
    public LocalDateTime lastLoginAt;
}
