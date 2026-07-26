package com.innercosmos.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchemaAuroraActionInitializerTest {
    @Test
    void longLivedH2TurnPlanIsBackfilledWithV26ActionColumnsAndIndexIdempotently() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                "jdbc:h2:mem:v26-action-legacy;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        // Pre-V26 shape: a real long-lived turn-plan table without action proposal columns.
        jdbc.execute("""
                CREATE TABLE tb_turn_plan(
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  turn_id BIGINT NOT NULL,
                  user_id BIGINT NOT NULL,
                  plan_version INT NOT NULL,
                  status VARCHAR(32) NOT NULL)
                """);
        jdbc.update("INSERT INTO tb_turn_plan(turn_id,user_id,plan_version,status) VALUES (11,7,1,'COMMITTED')");

        SchemaAuroraActionInitializer initializer = new SchemaAuroraActionInitializer(jdbc);
        initializer.run(new DefaultApplicationArguments(new String[0]));
        initializer.run(new DefaultApplicationArguments(new String[0]));

        jdbc.update("""
                UPDATE tb_turn_plan SET proposed_action_type='REMEMBER',
                  proposed_action_payload='{"title":"demo","content":"remember this"}',
                  proposed_action_summary='Save a private memory',
                  action_status='PENDING_CONFIRMATION',
                  action_confirmed_at=NULL,
                  action_result_ref=NULL
                WHERE id=1
                """);
        assertEquals("REMEMBER", jdbc.queryForObject(
                "SELECT proposed_action_type FROM tb_turn_plan WHERE user_id=7 AND action_status='PENDING_CONFIRMATION'",
                String.class));
        assertEquals(1, jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.indexes
                WHERE table_name='tb_turn_plan' AND index_name='idx_turn_plan_pending_action'
                """, Integer.class));
    }
}
