package com.innercosmos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Fresh schema.sql and long-lived non-Flyway H2 databases converge on DataUseGrant v1. */
@Component
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "false", matchIfMissing = true)
@ConditionalOnExpression("'${spring.datasource.driver-class-name:org.h2.Driver}' == 'org.h2.Driver'")
@Order(16)
public class SchemaM16Initializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaM16Initializer.class);
    private final JdbcTemplate jdbc;

    public SchemaM16Initializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        execute("""
                CREATE TABLE IF NOT EXISTS tb_data_use_grant (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY, owner_user_id BIGINT NOT NULL,
                  resource_type VARCHAR(32) NOT NULL, resource_id BIGINT NOT NULL,
                  resource_version INT NOT NULL, purpose VARCHAR(48) NOT NULL,
                  consumer_type VARCHAR(32) NOT NULL, consumer_id BIGINT NOT NULL,
                  grant_version INT NOT NULL, parent_grant_id BIGINT,
                  status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE', consent_source VARCHAR(64) NOT NULL,
                  granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, revoked_at TIMESTAMP NULL,
                  revoke_reason VARCHAR(240), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)
                """);
        execute("CREATE INDEX IF NOT EXISTS idx_data_grant_consumer ON tb_data_use_grant(consumer_type,consumer_id,status)");
        execute("CREATE INDEX IF NOT EXISTS idx_data_grant_resource ON tb_data_use_grant(resource_type,resource_id,status)");
        tryExecute("ALTER TABLE tb_authorized_memory_ref ADD COLUMN data_use_grant_id BIGINT");
        backfill("CASE WHEN COALESCE(c.simulator_only,FALSE) THEN 'CAPSULE_SIMULATOR' ELSE 'CAPSULE_RUNTIME' END");
        backfill("'PROVIDER_EGRESS'");
        execute("""
                UPDATE tb_authorized_memory_ref r SET data_use_grant_id=(
                  SELECT g.id FROM tb_data_use_grant g JOIN tb_echo_capsule c ON c.id=r.capsule_id
                  WHERE g.consumer_type='ECHO_CAPSULE' AND g.consumer_id=r.capsule_id
                    AND g.resource_type='MEMORY_CARD' AND g.resource_id=r.memory_card_id
                    AND g.purpose=CASE WHEN COALESCE(c.simulator_only,FALSE)
                      THEN 'CAPSULE_SIMULATOR' ELSE 'CAPSULE_RUNTIME' END
                  ORDER BY g.grant_version DESC LIMIT 1)
                WHERE r.data_use_grant_id IS NULL
                """);
    }

    private void backfill(String purposeExpression) {
        execute("""
                INSERT INTO tb_data_use_grant
                  (owner_user_id,resource_type,resource_id,resource_version,purpose,consumer_type,
                   consumer_id,grant_version,status,consent_source,granted_at,revoked_at,revoke_reason,
                   created_at,updated_at)
                SELECT c.owner_user_id,'MEMORY_CARD',m.id,COALESCE(m.version_no,1),%s,'ECHO_CAPSULE',c.id,1,
                  CASE WHEN r.authorization_status='AUTHORIZED' THEN 'ACTIVE' ELSE 'REVOKED' END,
                  'LEGACY_AUTHORIZED_MEMORY_REF_BACKFILL',COALESCE(r.created_at,CURRENT_TIMESTAMP),
                  CASE WHEN r.authorization_status='AUTHORIZED' THEN NULL ELSE COALESCE(r.updated_at,CURRENT_TIMESTAMP) END,
                  CASE WHEN r.authorization_status='AUTHORIZED' THEN NULL ELSE 'LEGACY_REF_NOT_ACTIVE' END,
                  COALESCE(r.created_at,CURRENT_TIMESTAMP),COALESCE(r.updated_at,CURRENT_TIMESTAMP)
                FROM tb_authorized_memory_ref r JOIN tb_echo_capsule c ON c.id=r.capsule_id
                JOIN tb_memory_card m ON m.id=r.memory_card_id
                WHERE NOT EXISTS (SELECT 1 FROM tb_data_use_grant g
                  WHERE g.consumer_type='ECHO_CAPSULE' AND g.consumer_id=c.id
                    AND g.resource_type='MEMORY_CARD' AND g.resource_id=m.id AND g.purpose=%s)
                """.formatted(purposeExpression, purposeExpression));
    }

    private void execute(String sql) {
        try {
            jdbc.execute(sql);
        } catch (Exception e) {
            throw new IllegalStateException("DataUseGrant schema migration failed", e);
        }
    }

    private void tryExecute(String sql) {
        try { jdbc.execute(sql); }
        catch (Exception alreadyPresent) { log.debug("Schema migration skipped: {}", alreadyPresent.getMessage()); }
    }
}
