package com.innercosmos.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 2026-07-24 8-agent audit follow-up: reproduced live that a pre-existing (pre-V23) file-backed H2
 * database 500s on the very first Aurora chat message with "Column preferred_tts_voice_id not
 * found", because no retrofit initializer backfilled V23's tb_user_profile columns. This test
 * pins the fix against exactly that pre-V23 shape.
 */
class SchemaM19InitializerTest {
    @Test
    void longLivedH2UserProfileTableIsBackfilledWithTtsInnerVoiceColumnsIdempotently() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:m19-legacy;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        // Pre-V23 shape: no preferred_tts_voice_id / inner_voice_enabled / inner_voice_mode.
        jdbc.execute("CREATE TABLE tb_user_profile(id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT)");
        jdbc.update("INSERT INTO tb_user_profile(user_id) VALUES (7)");

        SchemaM19Initializer initializer = new SchemaM19Initializer(jdbc);
        initializer.run(new DefaultApplicationArguments(new String[0]));
        initializer.run(new DefaultApplicationArguments(new String[0])); // idempotent: must not throw on re-run

        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM tb_user_profile WHERE inner_voice_mode='AMBIENT' AND inner_voice_enabled=TRUE AND preferred_tts_voice_id IS NULL",
                Integer.class));
    }
}
