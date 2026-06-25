package com.innercosmos.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * M1 fix: auto-fill {@code createdAt}/{@code updatedAt} on every insert/update.
 *
 * <p>Previously {@link com.innercosmos.entity.BaseEntity} relied solely on the DDL
 * {@code DEFAULT CURRENT_TIMESTAMP}, which set {@code created_at}/{@code updated_at}
 * only at insert time. Since the schema has no {@code ON UPDATE CURRENT_TIMESTAMP},
 * {@code updated_at} stayed frozen at the insert value on every {@code updateById}.</p>
 *
 * <p>This handler runs in Java (before the SQL is issued), so it cooperates with —
 * rather than conflicts with — the DDL defaults: on insert it provides explicit
 * values for both columns; on update it sets {@code updated_at} to "now". Fields are
 * only filled when null, so callers that set timestamps explicitly are respected.</p>
 */
@Component
public class MybatisMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
