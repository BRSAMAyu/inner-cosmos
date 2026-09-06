package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** CP-07 consent center: latest effective decision per (user, purpose). */
@TableName("tb_consent_record")
public class ConsentRecord extends BaseEntity {
    public Long userId;
    public String purposeCode;
    /** GRANTED or DECLINED. */
    public String status;
    public String version;
    public LocalDateTime grantedAt;
    public LocalDateTime revokedAt;
    public String evidenceSource;
}
