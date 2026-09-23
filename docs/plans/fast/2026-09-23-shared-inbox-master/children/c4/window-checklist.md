# c4 维护窗口执行清单（预置，未授权前不得执行任何一步）

> 归属：fast-p child c4（`docs/plans/2026-09-23/04-lukai-production-migration.md`）。本文件是**操作清单**，不是
> 计划 04 要求的 `docs/runbooks/repair-lukai-shared-inbox.md`（后者必须在窗口内按实际结果填写）。规范条文一律
> 以计划 04 与 MAIN 的 M-1～M-6 为准；本清单只把「跑什么、怎么判、记录什么」排成可执行顺序。
>
> **硬边界**：未拿到单独上线授权前，禁止执行第 1～8 节任何生产写操作（含停调度、DDL、DML、部署、重启）。
> 2026-09-23 的只读快照数字（QF `MANUAL_REVIEW 44 / PROCESSED 2`、`[self-check]` 23/2、`id=383`、`366/369`、
> QF INBOUND 21 等）**只是调查快照，不得作为阈值或断言基数**；一切以窗口内重采为准。

## 0. 需要人工提供/确认的输入

| 输入 | 说明 | 当前状态 |
|---|---|---|
| 上线授权 | 明确「授权在 X 窗口执行 04 的 DDL/DML/部署」 | 待提供 |
| 维护窗口 | 起止时间；期间系统可停轮询/可重启 | 待提供 |
| 发布来源 | 用哪个 commit/分支的 WAR（fast 分支 `e77cb06`，还是合并进 main 后再发） | 待定 |
| 生产访问 | SSH `root@150.158.92.103`（`.multi-ai-kit.yaml: release.production`）；本机是否已配 key | 待确认 |
| 停写方式 | 停 Tomcat 还是仅停调度（`talent-introduction.scheduling.enabled=false` + 队列关闭） | 待定 |

## 1. 冻结与只读预检（计划 04 阶段 0）

```bash
# 1.1 记录版本与运行参数（在生产机上）
git -C <部署源> rev-parse HEAD                         # 记录实际部署 commit
ps -ef | grep -i tomcat | head -3                      # 记进程
cat /opt/apache-tomcat-9.0.71/bin/setenv.sh 2>/dev/null | grep -E "PROFILES|FLYWAY|DATASOURCE"  # profile / Flyway 开关 / DB
mysql -N -e "SELECT VERSION(); SHOW DATABASES LIKE 'talent_introduction';"
mysql talent_introduction -N -e "SHOW TABLES LIKE 'flyway_schema_history';"   # 计划断言：生产无 Flyway 表
```
- 计划 04 记录调查时为 `SPRING_PROFILES_ACTIVE=simulator`、`SPRING_FLYWAY_ENABLED=false`；窗口内必须**重新确认**，不一致即停止并回交。
- 记录两账号当前值（脱敏后可入 runbook）：`SELECT account_code, sender_email, enabled, inbound_mailbox_code FROM mail_sender_account WHERE account_code IN ('LuKai','LuKai_QF');`
- 记录两条游标：`SELECT * FROM mail_inbox_cursor WHERE sender_account_code IN ('LuKai','LuKai_QF');`

```bash
# 1.2 停写（顺序：先停调度/队列，再停应用；确认无活动作业与写入）
#   停止后必须复核：无 RUNNING 的 task_execution、无活动收信日志、DB 连接数归零
mysql talent_introduction -e "SELECT status, COUNT(*) FROM task_execution GROUP BY status;"
```

## 2. 现场重采（只读，全部落 runbook 附件）

```sql
-- 2.1 账号与游标（上一节）
-- 2.2 两个逻辑账号的 processing 分布（重采，不看旧快照）
SELECT sender_account_code, process_status, COUNT(*) FROM inbound_mail_processing
 WHERE sender_account_code IN ('LuKai','LuKai_QF') GROUP BY 1,2 ORDER BY 1,2;
-- 2.3 探针候选（仅作候选，逐 ID 复核原始 IMAP 头后才可判定）
SELECT id, sender_account_code, uid_validity, imap_uid, process_status, process_reason, subject
  FROM inbound_mail_processing WHERE subject LIKE '[self-check]%' ORDER BY id;
-- 2.4 QF INBOUND 邮件
SELECT COUNT(*) FROM mail_record WHERE direction='INBOUND' AND sender_account_code='LuKai_QF';
-- 2.5 外键与软引用全量清点（不假设为零）
SELECT TABLE_NAME, COLUMN_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME
  FROM information_schema.KEY_COLUMN_USAGE
 WHERE TABLE_SCHEMA='talent_introduction'
   AND REFERENCED_TABLE_NAME IN ('inbound_mail_processing','mail_record','mail_attachment','mail_attachment_transfer')
 ORDER BY REFERENCED_TABLE_NAME, TABLE_NAME;
SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA='talent_introduction'
   AND (COLUMN_NAME LIKE '%processing_id%' OR COLUMN_NAME LIKE '%mail_record_id%' OR COLUMN_NAME LIKE '%attachment_id%'
        OR COLUMN_NAME LIKE '%source_inbound_id%' OR COLUMN_NAME LIKE '%transfer_id%')
 ORDER BY TABLE_NAME, COLUMN_NAME;
-- 2.6 计划 04 明列的历史依赖（逐表 COUNT 按本窗口的候选 ID 集合）
--    inbound_intent(V6) / inbound_mail_tag(V53) / mail_attachment(V7,V36,V118,V130) /
--    mail_attachment_transfer(V119) / expert_document / operator_action_log(V19) /
--    QA link(V42) / meeting 表(V9,V125) / mail_record.source_inbound_id
```
- IMAP 侧只读复核（两账号各自登录）：UIDVALIDITY、抽样 UID 的 `Message-ID/From/ReceivedAt/To`，用于证明同一物理收件箱与原始收件人；**不写、不标已读、不推进游标**。
- 任一关键证据缺失、出现计划未列出的依赖类型 → 停止并回交（计划 04 阶段 0 的硬要求）。

## 3. 备份（可恢复性必须验证）

```bash
mysqldump --single-transaction --routines --triggers talent_introduction > /secure/backup/talent_introduction_<ts>.sql
sha256sum /secure/backup/talent_introduction_<ts>.sql | tee /secure/backup/<ts>.sha256
# 可恢复性验证：导入临时库并逐表计数比对（至少核心 6 表）
mysql -e "CREATE DATABASE talent_introduction_restorecheck;"
mysql talent_introduction_restorecheck < /secure/backup/talent_introduction_<ts>.sql
# 计数比对：mail_sender_account / mail_inbox_cursor / inbound_mail_processing / mail_record / inbound_mail_tag / mail_attachment
```
- 备份不可读或计数不一致 → 停止，不得进入第 4 节。

## 4. 兼容 DDL（计划 04 阶段 1）

```bash
mysql talent_introduction -e "SHOW CREATE TABLE mail_sender_account\G SHOW CREATE TABLE inbound_mail_processing\G"
```
- 若两列与唯一键**已完全等价存在** → 记录并跳过；若**部分存在/不等价** → 停止并回交。
- 否则执行一次等价手工 DDL（与 c1 的 V134 逐字等价，**不得回填旧行**）：
```sql
ALTER TABLE mail_sender_account
  ADD COLUMN inbound_mailbox_code VARCHAR(64) NULL COMMENT '共享收件箱主账号代码；NULL=本账号独立收件（单层，不级联）';
ALTER TABLE mail_sender_account
  ADD CONSTRAINT fk_mail_sender_account_inbound_mailbox
  FOREIGN KEY (inbound_mailbox_code) REFERENCES mail_sender_account (account_code);
ALTER TABLE inbound_mail_processing
  ADD COLUMN mailbox_owner_code VARCHAR(64) NULL COMMENT '物理收件箱主账号代码；NULL=历史未知行（不回填）';
ALTER TABLE inbound_mail_processing
  ADD UNIQUE KEY uk_inbound_mail_processing_owner_uid (mailbox_owner_code, uid_validity, imap_uid);
```
- 执行后核验：两列存在、`uk_inbound_mail_processing_owner_uid` 存在、`SELECT COUNT(*) FROM inbound_mail_processing WHERE mailbox_owner_code IS NOT NULL` 应为 0。
- **注意**：MySQL DDL 隐式提交，普通 `ROLLBACK` 不能撤销 DDL；回滚只能按备份/反向变更方案执行。

## 5. 三段分类（计划 04 阶段 1；顺序不可换）

**A. 内部自检探针（先做）** — 逐 ID，需同时满足：
- 原始 IMAP 头证明为本物理组自检（From = 本组某账号 `sender_email`；Subject = 该账号的完整生成格式 `[self-check] {accountCode} {十进制时间戳}`，允许 `[ self - check ]` 空格变体）；
- 该行 `process_status = 'MANUAL_REVIEW'`；
- 无专家关联、无 tag/intent/attachment/transfer/operator log/其他 FK 或软引用（用 2.5 的清单逐表 COUNT）。
- 只有全部满足的精确 ID 才可备份后删除；`MANUAL_RESOLVED`（历史 366/369 类）**不动**；不满足的行按普通来信进入 B。
- **禁止** `DELETE ... WHERE subject LIKE '[self-check]%'`。

**B. processing 物理重复 / 逻辑缺漏** — 判据（计划 04 I-3）：
- 与 owner 行同一物理来信 = `uid_validity` + `imap_uid` 相同 **且** 非空 `Message-ID`、`From`、秒级 `ReceivedAt` 全等 **且** raw `To` 证明原业务归属为 LuKai → 可合并（保留 owner 行）；
- 无 owner 对应行、raw `To` 指向 updates 的真实来信 → 改 `sender_account_code='LuKai'`、`mailbox_owner_code='LuKai'`，**保留 ID/标签/附件**；
- 唯一键冲突 → 停止（不得强删）；
- raw `To` 不唯一/缺失 → 保留并交人工，不自动归属。

**C. INBOUND `mail_record` 等价** — 仅当 owner 行同专家、同 Message-ID、同 `ReceivedAt`、同正文/清洗正文/类型，且依赖可无损重指向时才删冗余；否则改归 LuKai 保留。

## 6. 事务内修复 + 账号切换（计划 04 阶段 2）

```sql
START TRANSACTION;
-- 6.1 备份精确 ID 的旧行快照（SELECT ... FOR UPDATE 或先落 backup 表）
-- 6.2 依赖重指向（按 2.5 清单的 FK 顺序；attachment 物理文件一律不删）
-- 6.3 A 段确认的探针行删除 / B 段合并删除 / B、C 段改归
-- 6.4 账号切换（必须恰好 1 行，且原值为 NULL）
UPDATE mail_sender_account SET inbound_mailbox_code='LuKai'
 WHERE account_code='LuKai_QF' AND sender_email='lukai@qingfeitalent.com' AND inbound_mailbox_code IS NULL;
-- 6.5 COMMIT 前断言（全部必须成立，否则 ROLLBACK）
--   a) ROW_COUNT()=1；b) QF 的 SMTP/IMAP/限额/enabled 与窗口前逐列相等；
--   c) 无孤儿 FK：按 2.5 清单逐表 LEFT JOIN 反查为 0；
--   d) 保留下来的每封信仍能由会话/材料读路径取到（抽样 3 条）；
--   e) 本组唯一键无冲突；f) 旧 0 代际行未被回填（mailbox_owner_code IS NULL 计数不变）。
COMMIT;
```
- 只让 owner 游标前进：`LuKai_QF` 的旧 cursor **不删不改**，`LuKai` 的 cursor **不回拨**。

## 7. 部署与受控恢复（计划 04 阶段 3）

- 发布路径（仓库已配置，`publish_feature` 同源）：SSH `root@150.158.92.103`，Tomcat `/opt/apache-tomcat-9.0.71`，war `talent.war`，上传 `/opt/apache-tomcat-9.0.71/deploy/incoming`，备份 `/opt/apache-tomcat-9.0.71/deploy/backups`，健康 `http://127.0.0.1/talent/`（超时 120s）；DB 迁移模式 `external-mysql`（baseline 9，故 DDL 不随包自动执行）。
- 逐步：部署 01–03 完整 WAR → 先只读检查（QF 已映射 LuKai、owner 游标与行数稳定、截图里的重复待处理消失）→ 用专门测试邮箱分别投 updates/QF 各 1 封 → 手动检查 1 次 → 确认每封恰好 1 条 processing 且账号正确 → 再恢复原调度。**不开启自动回复、不批量发送**。
- 观察窗口内若再次出现同 UID 双 processing 或探针待处理 → 停止并回交。

## 8. 回滚

- 代码启动失败：停止新应用，按备份恢复配置/行快照；两列可暂留 nullable 供旧代码忽略，但**必须先在测试环境验证旧 WAR 对新增列兼容**，不得猜测。
- 数据修复已提交后需回滚：只能按备份 + 依赖核查人工恢复；**禁止**简单重启旧 WAR 导致双轮询复发。

## 9. 执行时记录（窗口内填写；本清单不预填）

| 项 | 值 |
|---|---|
| 授权原文 | |
| 窗口起止 | |
| 部署 commit / WAR 校验和 | |
| 备份路径 + sha256 + 恢复验证结果 | |
| DDL 是否已存在 / 执行记录 | |
| A 段确认删除 ID 列表（含证据） | |
| B 段：合并 ID / 改归 ID / 保留待人工 ID | |
| C 段：删除 ID / 改归 ID | |
| 账号 UPDATE 影响行数 | |
| 断言 a–f 结果 | |
| 首轮试投结果 | |
| 回滚执行情况（如有） | |
