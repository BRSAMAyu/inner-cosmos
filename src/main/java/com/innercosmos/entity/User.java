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
    /** Persisted provenance used by discovery policy; never inferred at read time. */
    public String accountKind;
    /** CP-08 adult gate: self-declared full birth date (Asia/Shanghai reckoning), nullable pre-gate. */
    public java.time.LocalDate birthDate;
    /** SELF_DECLARED (registration) or VERIFIED_ID (CP-13 identity flow). */
    public String ageGateMethod;
    public LocalDateTime lastLoginAt;
}
