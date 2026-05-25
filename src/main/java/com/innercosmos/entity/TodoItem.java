package com.innercosmos.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("tb_todo_item")
public class TodoItem extends BaseEntity {
    public Long userId;
    public Long sourceMemoryCardId;
    public String taskName;
    public String description;
    public String priority;
    public String status;
    public LocalDateTime deadline;
}
