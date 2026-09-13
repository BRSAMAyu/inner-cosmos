package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** CP-62 import receipt: the natural-key fact that makes re-imports idempotent. */
@TableName("tb_data_import_receipt")
public class DataImportReceipt extends BaseEntity {
    public Long targetUserId;
    public Long sourceUserId;
    public String section;
    public String sourceRecordKey;
    public LocalDateTime importedAt;
}
