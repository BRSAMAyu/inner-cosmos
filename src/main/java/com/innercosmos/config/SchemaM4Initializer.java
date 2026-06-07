package com.innercosmos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Schema migrations for M4 WeeklyReviewV2 + EmotionPatternService.
 * H2 does not support ADD COLUMN IF NOT EXISTS, so each statement is attempted
 * and any "column already exists" failure is ignored.
 */
@Component
@Order(2)
public class SchemaM4Initializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaM4Initializer.class);

    @Autowired
    private JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        // Extend tb_weekly_review with V2 fields
        migrate("tb_weekly_review", "title VARCHAR(200)");
        migrate("tb_weekly_review", "date_range VARCHAR(40)");
        migrate("tb_weekly_review", "top_themes TEXT");
        migrate("tb_weekly_review", "memory_count INT DEFAULT 0");
        migrate("tb_weekly_review", "dominant_emotion VARCHAR(64)");
        migrate("tb_weekly_review", "emotion_spectrum TEXT");
        migrate("tb_weekly_review", "intensity_average DOUBLE DEFAULT 0");
        migrate("tb_weekly_review", "todo_ratio VARCHAR(16)");
        migrate("tb_weekly_review", "recommendation TEXT");
        migrate("tb_weekly_review", "daily_snapshots TEXT");
        migrate("tb_weekly_review", "legacy BOOLEAN DEFAULT FALSE");

        // Extend tb_emotion_timeline with pattern fields
        migrate("tb_emotion_timeline", "trigger_scenes TEXT");
        migrate("tb_emotion_timeline", "related_memory_titles TEXT");
        migrate("tb_emotion_timeline", "confidence_score DOUBLE DEFAULT 0");
        migrate("tb_emotion_timeline", "pattern_type VARCHAR(32)");
    }

    private void migrate(String table, String columnDef) {
        String ddl = "ALTER TABLE " + table + " ADD COLUMN " + columnDef;
        try {
            jdbc.execute(ddl);
            log.info("Schema migration applied: {}", ddl);
        } catch (Exception e) {
            log.debug("Schema migration skipped for {} ({}): {}", ddl, table, e.getMessage());
        }
    }
}