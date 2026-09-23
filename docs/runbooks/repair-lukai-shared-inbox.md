# LuKai / LuKai_QF 共享收件箱迁移与历史修复：现场执行手册与记录

> 计划：`docs/plans/2026-09-23/04-lukai-production-migration.md`（G-4、I-1…I-5）；上位约束 `docs/plans/2026-09-23/00-shared-inbox-main.md`（M-1…M-6）。
> 本文件是**执行记录**：所有命令、ID 清单、计数与前后结果均来自 2026-09-23 当晚的现场执行（21:12 起，停写 21:18:39，修复 COMMIT ≈21:33，部署 21:35）。
> 本文件不含任何凭据；专家/来信仅以数据库 ID 与 Message-ID 指代。

**执行边界（越界即停）**
- 除本窗口已授权动作外，不得再执行任何生产写操作；本窗口内未执行的动作见第 10 节「遗留」。
- 不删除任何物理附件文件（`/opt/talent/uploads/mail-attachments` 未改动）。
- 不回拨 `LuKai` 游标、不删除 `LuKai_QF` 旧游标；不重发历史邮件、不触发自动回复。

---

## 1. 授权与前置

| 项 | 值 |
|---|---|
| 授权 | 用户明确「授权」执行 04 的停轮询/停应用、DDL、逐 ID 删除与改归、账号配置 UPDATE、部署重启 |
| 窗口 | 2026-09-23 21:12 → 21:36（当晚；执行前确认无在途作业） |
| 生产主机 | `VM-4-16-centos`（`root@150.158.92.103`，见 `.multi-ai-kit.yaml` `release.production`） |
| 运行参数 | `SPRING_PROFILES_ACTIVE=simulator`、`SPRING_FLYWAY_ENABLED=false`、`MAIL_SCHEDULING_ENABLED=true`、`MAIL_QUEUE_ENABLED=false`、auto-reply-all cron `0 */10 * * * *` |
| DB | 本机 MySQL 5.7.41，库 `talent_introduction`（144.9 MB），无 `flyway_schema_history` |
| 部署前生产版本 | war 备份名 `talent.war.release_20260923-162151_9237d6f57333.bak` → 生产当时为 main HEAD `9237d6f57333`（= 本次 fast-p 的 master base） |
| 发布源 | fast-p 分支 `fast/2026-09-23-shared-inbox-master`，产品 commit `e77cb065ba6261317adc7060b2a7729052086407`（c1+c2+c3 验证通过） |

## 2. 停写与冻结

```bash
# 停写（JAVA_HOME 必须显式指向 JDK 11，PATH 上的 java 是 8）
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk; export JRE_HOME=$JAVA_HOME
/opt/apache-tomcat-9.0.71/bin/shutdown.sh 20     # 优雅停止（shutdown 端口未响应，超时后 SIGTERM）
```

| 证据 | 值 |
|---|---|
| 停止前 PID / 停止时间 | 1680 → **2026-09-23 21:18:39** |
| 端口 | 80/8080 监听数 0 |
| 写冻结验证 | 20s 内 `inbound_mail_processing` 409→409、`mail_record` 6963→6963（`WRITE_FREEZE_OK`），连接数 1 |
| 停写前在途作业 | 最近一次 `AUTO_REPLY_ALL` 21:10:00→21:12:02 SUCCESS；21:15 检查时无 RUNNING |

## 3. 现场重采（停写后，只读）

- 两账号 IMAP 现场登录（只读 `SELECT readonly` + `BODY.PEEK`，不置 `\Seen`）：
  - `LuKai` 与 `LuKai_QF` 均为 `UIDVALIDITY=1782107786`、`UIDNEXT=436`、`EXISTS=319` → **同一物理收件箱**成立。
  - 两账号游标：`LuKai last_uid=435 (18:40:07)`、`LuKai_QF last_uid=321 (21:11:34)`。
- 账号：`LuKai` enabled=1 limit=99999 todaySent=32；`LuKai_QF` enabled=0 limit=100 todaySent=0。
- 本组 processing（停写时）：`LuKai PROCESSED 109`；`LuKai_QF MANUAL_REVIEW 157 / PROCESSED 2`（其中 `[self-check]` 主题 95 条待处理 + 2 条人工已处理 366/369）。
- 快照数字（调查期 44/23）已被现场值取代：**待处理 157、探针候选 95** —— 印证「不得继承旧快照作阈值」。
- 外键/软引用全量清点（`information_schema.KEY_COLUMN_USAGE`）：指向 processing 的 FK 3 类（`mail_attachment.inbound_processing_id`、`mail_attachment_transfer.inbound_processing_id`、`operator_action_log.inbound_processing_id`）+ 无 FK 软引用 `inbound_mail_tag.inbound_processing_id`；指向 mail_record 的 FK 7 类（`inbound_intent`、`mail_attachment`、`mail_record_qa_rule`、`meeting_schedule`、`meeting_calendar_event`）+ 软引用（`mail_record_rag_fact`、`expert_application_promotion.source_inbound_id`、`auto_reply_confidence_log.inbound_mail_record_id`、`mail_record.source_inbound_id`）。

## 4. 备份与可恢复性验证

| 项 | 值 |
|---|---|
| 备份 | `/root/db-backups/20260923-211757/talent_introduction.sql`（111,376,225 B） |
| sha256 | `78fb471af3fed0da78d3f42dcb1dc94e1c9cbee257de66584fa8dc1d4063e910` |
| 恢复验证 | 导入临时库 `talent_introduction_restorecheck`（保留至今，可继续用于取回被删行） |
| 逐表比对 | `mail_sender_account` 6/6、`mail_inbox_cursor` 7/7、`inbound_mail_processing` 409/409、`mail_record` 6963/6963、`inbound_mail_tag` 428/428、`mail_attachment` 141/141、`mail_attachment_transfer` 82/82、`inbound_intent` 233/233、`bounce_record` 249/249、`operator_action_log` 471/471 —— 全部 OK |

**行级快照表（同库，回滚可直接取用）**

| 表 | 内容 | 行数 |
|---|---|---|
| `_bk_c4_20260923_imp` | 本次改动/删除的 processing 行（95 探针 + 35 重复 + 27 改归） | 157 |
| `_bk_c4_20260923_mr` | 改归的 QF INBOUND mail_record | 47 |
| `_bk_c4_20260923_att` | 删除的重复附件元数据 | 6 |
| `_bk_c4_20260923_tag` | 删除的重复标签 | 31 |
| `_bk_c4_20260923_trf` / `_trf2` / `_trf_all` | transfer 快照（`_trf_all` 由恢复库补全，覆盖全部 57 条关联 transfer） | 1 / 5 / 57 |
| `_bk_c4_20260923_acc` | 账号行（切换前） | 2 |
| `_bk_c4_20260923_grp` | 本组代际 1782107786 行 | 204 |

## 5. 兼容 DDL（V134 等价，已应用）

执行前 `SHOW CREATE TABLE` 判定两列**均不存在**（生产无 Flyway），故手工执行与 c1 `V134__shared_inbox_owner.sql` 逐字等价的 DDL：

```sql
ALTER TABLE mail_sender_account ADD COLUMN inbound_mailbox_code VARCHAR(64) NULL;
ALTER TABLE mail_sender_account ADD CONSTRAINT fk_mail_sender_account_inbound_mailbox
  FOREIGN KEY (inbound_mailbox_code) REFERENCES mail_sender_account (account_code);
ALTER TABLE inbound_mail_processing ADD COLUMN mailbox_owner_code VARCHAR(64) NULL;
ALTER TABLE inbound_mail_processing ADD UNIQUE KEY uk_inbound_mail_processing_owner_uid (mailbox_owner_code, uid_validity, imap_uid);
```

核验：两列 `varchar(64) NULL` 存在；`uk_inbound_mail_processing_owner_uid` 存在；`mailbox_owner_code IS NOT NULL` 行数 = 0（**无回填**）。

## 6. 分类（逐 ID，全部以现场 IMAP 头 + DB 依赖为准）

**A. 内部自检探针（95 条，全部 `MANUAL_REVIEW`）**
- 判据：From = 本组账号 `sender_email`（IMAP 头）+ Subject 为完整生成格式 `[self-check] LuKai <十进制时间戳>` + `MANUAL_REVIEW` + 专家为空 + `inbound_mail_tag`/`mail_attachment`/`mail_attachment_transfer`/`operator_action_log` 依赖全为 0。
- ID：348,349,350,351,352,353,355,358,360,361,362,363,364,365,371,372,373,375,376,379,381,382,**383**,384,385,386,387,388,389,390,391,393,394,395,396,397,398,399,400,401,403,406,407,408,409,411,412,414,417,418,419,420,422,423,426,427,430,433,435,436,438,440,441,443,445,447,448,450,451,452,453,454,457,459,460,461,462,463,464,468,475,476,477,478,479,482,483,485,486,487,488,490,496,497,498
- **保留**：366、369（`PROCESSED/MANUAL_RESOLVED`，人工已处理，按 I-5 不清）。

**B. processing 物理重复（35 条）** — 与 owner 行同 `(uid_validity, imap_uid)` 且 Message-ID/From/ReceivedAt/专家全等，raw `To` 为 updates：
- ID（QF 侧）：415,416,421,424,429,431,432,434,437,439,442,444,446,449,455,456,458,465,466,467,469,470,471,472,473,474,480,481,484,489,491,492,493,494,495（对应 owner 行 182,183,184,188,190,191,192,260,256,263,265,266,271,273,274,276,277,282,284,285,288,289,290,293,294,297,299,300,301,303,306,307,308,309,310）

**C. 错归真实来信（27 条）** — raw `To` = `lukai@updates...`、无 owner 同 UID 行、专家关联存在：
- ID：335,336,337,338,339,340,341,342,343,344,346,347,356,357,359,367,368,374,377,378,380,392,402,404,405,410,413

**D. QF INBOUND mail_record（47 条）** — 与 owner 行同专家/Message-ID/ReceivedAt/类型/正文/清洗正文（其中 42 条完全等价），但**全部带有 `inbound_intent` 等依赖**，无法证明「依赖可无损合并」，故按计划「无等价 owner 行的改归 LuKai 保留」处理：**全部改归，不删除**。
- 其中 3 条不等价（7047/7053 正文与清洗正文不同、7061 ReceivedAt 不同）、3 条无 owner 对应（7031/7042/7062）。

**依赖重指向（删除前）**
- 删除重复 transfer 10 条（QF 侧与 owner 同 `file_name`+`part_path`），重指向 1 条（142 → owner 行 266）。
- 删除重复附件元数据 6 条（owner 已有同名文件、无 `expert_document` 悬挂：203,204,210,211,212,213）；4 条独有材料（205,206,207,208）重指向 owner 行 260/265/266/276。
- 删除重复标签 31 条（与 owner 完全同 `(tag_type, qa_rule_id)`）；无剩余标签需重指向。

## 7. 事务内修复（单事务，断言不通过即 ROLLBACK）

```
START TRANSACTION;
  -- transfer 去重(10) + 重指向(1) / 附件去重(6) + 重指向(4) / 标签去重(31)
  -- 删除 35 条重复 processing（守卫：删除时不得再有任何依赖）
  -- 删除 95 条已核验探针（守卫：MANUAL_REVIEW + 主题 + 专家空 + 零依赖）
  -- 27 条错归来信 UPDATE sender_account_code='LuKai', mailbox_owner_code='LuKai'（守卫：无 owner 同 UID 行）
  -- 47 条 QF INBOUND mail_record UPDATE sender_account_code='LuKai'
  -- 保留行物理 owner 标注：uid_validity=1782107786 且 mailbox_owner_code IS NULL 的组内行 → 'LuKai'（47 行）
  -- 账号切换：UPDATE mail_sender_account SET inbound_mailbox_code='LuKai' WHERE account_code='LuKai_QF' AND sender_email='lukai@qingfeitalent.com' AND inbound_mailbox_code IS NULL
COMMIT;   -- fail_count=0
```

**实际 ROW_COUNT / 断言**

| 项 | 值 |
|---|---|
| 删除 transfer / 重指向 | 10 / 1 |
| 删除附件 / 重指向（事务内 + 收尾） | 6 / 0 + 4 |
| 删除标签 / 重指向 | 31 / 0 |
| 删除重复 processing / 探针 | 35 / 95 |
| 改归 processing / mail_record | 27 / 47 |
| 物理 owner 标注 / 账号切换 | 47 / 1 |
| 孤儿行（9 类 FK/软引用合计） | 事务前 6 → 事务后 6（未新增；`fail_count=0` 要求相等） |
| 账号非目标列哈希 | `SAME`（`sender_email`/SMTP/IMAP/限额/`enabled` 全未变） |
| 零代际保护 | `uid_validity=0` 且 `mailbox_owner_code IS NOT NULL` = 0 |
| 结果 | `COMMITTED` |

## 8. 部署与受控恢复

| 步骤 | 结果 |
|---|---|
| 构建 | 本地 JDK 11 `mvn clean package -DskipTests`（fast-p worktree `e77cb06`）；war sha256 `cbcca08c1cc9e8878e7b460f81ad77f5edacdb8b4b187c7bf16a32450c301d26`（48,194,299 B） |
| 备份 | `deploy/backups/talent.war.release_<TS>_9237d6f57333.bak`（切换前 war） |
| 替换 | 上传至 `deploy/incoming/talent.war` → 覆盖 `webapps/talent.war` → 清理展开目录（附件存储在 `/opt/talent/uploads/mail-attachments`，未受影响） |
| 启动 | `startup.sh`（JAVA_HOME=JDK11）→ 21:35 启动；`Started TalentIntroductionApplication in 11.489s`；健康 `http://127.0.0.1/talent/` = **200** |
| 版本证据 | 部署包内迁移目录出现 `V134__shared_inbox_owner.sql` |

**部署后只读核对（21:36，调度下一轮 21:40 之前）**
- 游标：`LuKai 435`（未回拨）、`LuKai_QF 321`（部署后未再推进）。
- 部署后新增 processing = 0；部署后无新的重复键报错。
- 停写前日志中的历史错误（16:31 `LuKai_QF` uid=178 附件 transfer 重复键、18:31 uid=239 唯一键冲突）是迁移前双抓的直接证据，已随本次修复消失。

## 8b. 现场新发现：`webapps/` 内的旧备份目录被当作独立上下文运行（原计划未覆盖）

21:40 周期后 `LuKai_QF` 游标仍推进（321→326）并新增 3 条 `[self-check]` 待处理行，与新代码行为矛盾。排查结论：

| 证据 | 值 |
|---|---|
| 疑点目录 | `/opt/apache-tomcat-9.0.71/webapps/talent.dir.bak-20260922-manual-scope/`（2026-09-22 人工备份的完整 webapp） |
| 代码身份 | 其 `AutoMailReplyService.class`（2026-09-22 11:25）**不含** `SELF_CHECK_IGNORED` / `mailboxOwnerCode` → 旧代码 |
| 部署事实 | catalina 日志：`21:35:22 把web 应用程序部署到目录 [.../talent.dir.bak-20260922-manual-scope]`；该上下文于 `21:35:24` 独立启动 Spring（= 每轮 cron 出现 **两条** `AUTO_REPLY_ALL` 的原因） |
| 后果 | 该旧实例用自己的调度继续轮询 `LuKai_QF`（其游标 321→326）、以旧逻辑写入 3 条探针行（499/500/501，`mailbox_owner_code=NULL`） |

**处置**：将该目录整体移出部署目录（保留不删除）→ `/opt/talent/backups/talent.dir.bak-20260922-manual-scope`，随后重启 Tomcat（21:49:08 停、21:49:39 起，健康 200）。

**处置后验证（21:50 周期）**

| 项 | 结果 |
|---|---|
| 启动实例数 | 单实例（此前每轮 2 条 `AUTO_REPLY_ALL`，现在 1 条：19543 SUCCESS 21:50:00→21:51:12） |
| `LuKai_QF` 游标 | 冻结在 **326**（21:41:27 之后再未推进） |
| 新增 processing / mail_record | 0 / 0 |
| 旧实例残留的 3 条探针行 | 快照 `_bk_c4_20260923_probe3` 后按严格谓词删除（依赖全 0，`COMMITTED`） |

> 该目录在 2026-09-22 起就以旧代码并行运行，属**既存生产隐患**（也贡献了历史上的重复处理与重复 cron 执行）；本次仅移出部署目录，未删除任何文件。

## 9. 回滚

1. **配置/数据回滚**：从第 4 节全量备份或行级快照表恢复；顺序：账号行 → processing/mail_record 改归行 → 重新插入被删行（`_bk_c4_20260923_imp/mr/att/tag/trf_all`）→ 依赖重指向回退（transfer/attachment 的 `inbound_processing_id`）。恢复库 `talent_introduction_restorecheck` 保存了全部改动前状态。
2. **代码回滚**：`cp deploy/backups/talent.war.release_<TS>_9237d6f57333.bak webapps/talent.war` → 清理展开目录 → 启动；两列可保留 nullable 供旧代码忽略（旧代码不读新列）。**禁止**在未回滚数据修复的情况下重启旧 WAR（会恢复双抓）。
3. 回滚后必须复核：`LuKai_QF.inbound_mailbox_code` 恢复为 NULL、`mailbox_owner_code` 恢复原状、被删行回到 `MANUAL_REVIEW`。

## 10. 遗留与人工验收

- **最终状态（2026-09-23 21:52）**：本组 `LuKai MANUAL_REVIEW 27 / PROCESSED 109`、`LuKai_QF PROCESSED 2`（= 366/369 人工已处理探针）；本组 `[self-check]` 待处理 = **0**；`id=383` 已不存在；`mail_sender_account.inbound_mailbox_code(LuKai_QF)='LuKai'`；`LuKai` 游标 435、`LuKai_QF` 游标冻结 326。
- **未执行（需人工）**：A-1/A-2/A-5/A-6 的测试信投递（需外部测试邮箱）；A-4 的会话/材料目视核对。
- **保留的重复**：47 条 QF INBOUND `mail_record` 已改归 LuKai 但未删除（依赖不可证明无损合并）。它们与 owner 行同专家同 Message-ID，属**元数据级重复**；「收件/待处理」展示以 processing 为权威，因此会话收件不再重复。
- **其他账号**：`WuWei`/`WuWei_WB`/`LuKai` 的零代际历史探针行（如 8-23、15/17）按计划**未处理**（不在范围）。
- **既存生产隐患（本次已处置/建议后续处理）**：
  1. `webapps/` 下的 `talent.dir.bak-20260922-manual-scope` 曾被当作独立上下文以旧代码运行（已移出，见 8b）；
  2. 生产 MySQL `root` 使用弱口令、Tomcat `setenv.sh` 内含明文第三方凭据；
  3. `catalina.out` 已 1.05 GB 且混装三个应用的输出。

## 11. 复现本次执行的脚本

- 只读预检/分类：`c4-preflight-1..4.sh`、`c4-classify-1.sh`、`c4-dep-inv*.sh`（会话内 `/tmp/fast-p-shared-inbox/`）
- 停写 + 备份 + DDL：`c4-window-stage01.sh`
- 修复事务：`c4-fix.sql`（含全部 ID 清单与断言）
- 部署：`c4-deploy.sh`
