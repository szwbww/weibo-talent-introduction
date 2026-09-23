-- ============================================================================
-- V134 共享物理收件箱归属：mail_sender_account / inbound_mail_processing（fast-p 01）
--
-- 目标：任意逻辑发件账号可被管理员显式关联到一个物理收件箱主账号。
-- 本迁移只加结构，不改写任何既有行；生产上 inbound_mailbox_code 在本组
-- 02/03 完成验证前必须保持 NULL（发布闸门）。
--
-- 关键不变量（I-1、I-2）：
--   * I-1 关联是显式单层关系：inbound_mailbox_code NULL = 本账号独立收件；
--     非空由外键保证指向存在的账号代码，服务层另行保证非模拟器、非自己、
--     且主账号自身为 NULL（不成链、不成环、不级联）。
--   * I-2 旧行不可伪造物理代际：mailbox_owner_code 默认 NULL，历史行保持
--     NULL（尤其 uid_validity=0），绝不用当前 UIDVALIDITY 回填。
--     唯一键 (mailbox_owner_code, uid_validity, imap_uid) 只约束非空物理身份；
--     MySQL 唯一键允许多个 NULL，故历史行不受影响，两个不同物理 owner 也可
--     各自持有相同 UID 号。
--   * 该唯一键是「新收件物理唯一」的库层兜底，不替换既有
--     uk_inbound_mail_processing_uid_validity（逻辑账号 + 代际 + UID）。
-- ============================================================================
ALTER TABLE mail_sender_account
    ADD COLUMN inbound_mailbox_code VARCHAR(64) NULL
        COMMENT '共享收件箱主账号代码；NULL=本账号独立收件（单层，不级联）';

ALTER TABLE mail_sender_account
    ADD CONSTRAINT fk_mail_sender_account_inbound_mailbox
        FOREIGN KEY (inbound_mailbox_code) REFERENCES mail_sender_account (account_code);

ALTER TABLE inbound_mail_processing
    ADD COLUMN mailbox_owner_code VARCHAR(64) NULL
        COMMENT '物理收件箱主账号代码；NULL=历史未知行（不回填）';

ALTER TABLE inbound_mail_processing
    ADD UNIQUE KEY uk_inbound_mail_processing_owner_uid (mailbox_owner_code, uid_validity, imap_uid);
