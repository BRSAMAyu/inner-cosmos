-- 2026-07-27 audit follow-up.
--
-- V30 created tb_capsule_landing with INLINE `REFERENCES` clauses, so PostgreSQL auto-generated
-- the two foreign-key constraint names (tb_capsule_landing_capsule_id_fkey /
-- tb_capsule_landing_user_id_fkey). schema.sql (the H2/MySQL source of truth) declares the same
-- two keys as NAMED constraints fk_capsule_landing_capsule / fk_capsule_landing_user.
--
-- PostgresFlywayBaselineTest compares the foreign-key NAME set parsed out of schema.sql against
-- the live PostgreSQL catalogue, so the two could never agree.
--
-- The tempting shortcut -- editing V30 in place -- was applied at one point and is why this
-- migration exists instead: V30 ships in commit 66c7413, so rewriting it changes its Flyway
-- checksum and makes every database that already ran it fail startup with
-- "Migration checksum mismatch for version 30". A new forward migration is the only safe fix.
--
-- Renaming (not drop + re-add) keeps the constraint's OID, its ON DELETE CASCADE behaviour and
-- the underlying index untouched -- no table rewrite, no window where referential integrity is
-- unenforced.
--
-- Guarded so this is safe on a database provisioned from any prior state: if a constraint has
-- already been given the target name, its branch is simply skipped.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint
               WHERE conrelid = 'tb_capsule_landing'::regclass
                 AND conname = 'tb_capsule_landing_capsule_id_fkey') THEN
        ALTER TABLE tb_capsule_landing
            RENAME CONSTRAINT tb_capsule_landing_capsule_id_fkey TO fk_capsule_landing_capsule;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_constraint
               WHERE conrelid = 'tb_capsule_landing'::regclass
                 AND conname = 'tb_capsule_landing_user_id_fkey') THEN
        ALTER TABLE tb_capsule_landing
            RENAME CONSTRAINT tb_capsule_landing_user_id_fkey TO fk_capsule_landing_user;
    END IF;
END $$;
