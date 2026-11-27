USE caifan_db;

ALTER TABLE dlv_order
    ADD COLUMN delivered_time DATETIME NULL COMMENT '进入已配送状态时间' AFTER deliverer,
    ADD COLUMN sign_type VARCHAR(8) NULL COMMENT '签收来源 MANUAL/AUTO' AFTER signed_time,
    ADD INDEX idx_dlv_auto_sign (status, delivered_time);

UPDATE dlv_order
SET delivered_time = update_time
WHERE status = '3' AND delivered_time IS NULL;

UPDATE dlv_order
SET sign_type = 'MANUAL'
WHERE status = '5' AND sign_type IS NULL;

CREATE TABLE IF NOT EXISTS sal_claim_setting (
    id                 BIGINT NOT NULL,
    merchant_id        BIGINT NOT NULL DEFAULT 0,
    claim_window_hours INT    NOT NULL DEFAULT 14 COMMENT '主动签收后可报损小时数',
    create_by          BIGINT NULL, create_time DATETIME NULL,
    update_by          BIGINT NULL, update_time DATETIME NULL,
    deleted            TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_claim_setting_merchant (merchant_id)
) ENGINE=InnoDB COMMENT='商户损耗报损时限设置';
