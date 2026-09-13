-- CP-41 (commercial-cn blueprint): admit the mainland vendor push transports into the
-- device registration whitelist. The CHECK is the schema-side twin of the DTO @Pattern;
-- gateway beans stay credential-gated per vendor 渠道合同 (operator gate), so admitting
-- the transport values enables REGISTRATION and honest EXTERNAL_CREDENTIAL_GATE delivery
-- attempts — never fabricated sends.
ALTER TABLE tb_device_registration DROP CONSTRAINT ck_device_transport;
ALTER TABLE tb_device_registration ADD CONSTRAINT ck_device_transport
    CHECK (transport IN ('FCM','APNS','LOCAL_EVIDENCE','XIAOMI','OPPO','VIVO','HONOR','HUAWEI'));
