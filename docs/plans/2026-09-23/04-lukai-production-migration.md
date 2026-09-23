# LuKai / LuKai_QF 生产配置迁移与历史异常修复计划（4/4）

> 上位约束：[MAIN 总计划](00-shared-inbox-main.md)。依赖 01、02、03 均完成并独立验证。本文件是执行方案，不是上线授权；当前绝不运行 UPDATE/DELETE/DDL/重启。任何行数与证据以停写时重新读取为准，下面的数字只是 2026-09-23 只读快照。

**目标**：把现有 `LuKai_QF` 从“独立轮询同一个 INBOX”迁移为“独立发信账号 + `LuKai` 物理收件箱子账号”，修复已产生的重复收件/待处理及内部自检探针误入待处理，同时保留真实来信、标签、意图和附件；以后新增别名只需配置归属，不再写一次性分支。

**上线前置**：线上 Tomcat 进程在调查时为 `SPRING_PROFILES_ACTIVE=simulator`、`SPRING_FLYWAY_ENABLED=false`，DB 为本机 `talent_introduction`，没有 `flyway_schema_history`；因此 01 的 DDL 在生产须人工应用/核对，不能指望 WAR 自动跑 Flyway。部署前再次确认这些运行参数和现有表结构，若变化则停止并修订步骤。(来源: `K-flyway-version-follows-deploy-order`)

## 需求描述

- 可观察结果：新邮件寄 QF 时，在专家会话中只出现一次且账号为 `LuKai_QF`；旧错误重复待处理及经核验的 `[self-check]` 探针待处理消失；寄 updates 的旧真实来信/附件仍可查看；QF 的发信地址、SMTP 身份和当前禁用状态不被悄悄改变。
- 不得改变：历史有效邮件、发信记录、专家状态、标签/意图/附件内容、两个账号的 SMTP 凭据/日限额；不得自动重发历史邮件。
- 不在范围：批量重算全部退信、清理其他账号、修改专家匹配逻辑、把 QF 邮件硬并成 LuKai 业务账号。

## 关键不变量

### I-1：只以现场证据判定共享 INBOX
- 规则：停写后重新验证两组 IMAP 登录的 UIDVALIDITY、同一 UID 的 Message-ID/From/ReceivedAt/To；仅当这些均证明同一物理收件箱，才设置 `LuKai_QF.inbound_mailbox_code='LuKai'`。`To` 不唯一或缺失的行不得按历史快照自动归属。
- 适用写路径：账号配置 UPDATE、历史 processing/mail_record 分类。
- 违反后果：将两个真正独立邮箱错误合并而吞信。
- 来源：只读在线 IMAP 实测（两登录 UIDVALIDITY `1782107786`，当时 317 封相同可见邮件，授权测试信 UID 434 在两边且 To=QF）。

### I-2：可恢复且不跨越不明依赖
- 规则：每个拟改/删 ID 先列清映射、保存行快照和可恢复备份；依赖表逐一清点，不满足严格等价就保留并暂停，禁止 `DELETE ... WHERE sender_account_code='LuKai_QF'`。数据修复事务与切换配置要在停轮询窗口内完成；未通过校验则回滚 DML、保持旧应用停写并按备份恢复，不能靠“重新检查回复”补救。
- 适用写路径：一次性数据修复 SQL；包括 `inbound_mail_processing`、`mail_record`、`inbound_intent`、`inbound_mail_tag`、`mail_attachment`、`mail_attachment_transfer` 及任何现场 FK 依赖。
- 违反后果：材料/意图丢失或悬空引用。
- 来源：`K-mail-attachment-write-paths`、`K-mailbox-inbound-source-authority`。

### I-3：物理重复与逻辑缺漏分开处理
- 规则：QF processing 只有在 owner processing 的物理 UIDVALIDITY/UID、非空 Message-ID、From、秒级 ReceivedAt 均相同，且 raw To 证明原业务归属为 LuKai，才是可合并重复；否则保留并人工核验。没有 owner 对应行、raw To=updates 的 QF processing 改归 LuKai 且设置 `mailbox_owner_code='LuKai'`，不能删除。QF INBOUND mail_record 只有在 owner 行同专家、同 Message-ID、同 ReceivedAt、同正文/清洗正文/类型等价，且依赖可无损合并时才删除；无等价 owner 行的改归 LuKai 保留。
- 适用写路径：processing/mail_record/标签/意图/附件修复。
- 违反后果：把一封未处理真实来信当作重复删掉，或继续展示两遍。(来源: `K-inbound-processing-write-paths`、`K-mail-record-source-inbound-id`)

### I-4：不制造历史代际与新发信
- 规则：历史 `uid_validity=0` 不回填；只有现场 IMAP 实证 UID 与代际相符的行才可设置新的 `mailbox_owner_code`。不回拨 owner 游标去批量重放，不执行批量发送/自动回复；当前 `LuKai_QF.enabled=0` 保持 0，除非用户另行决定。
- 适用写路径：游标检查、配置 UPDATE、processing 归属 UPDATE。
- 违反后果：吞信、重信或外发历史邮件。
- 来源：`K-inbound-seen-not-processed-marker`、`K-sender-account-enabled-scope`。

### I-5：内部探针先分类、只清误入待处理
- 规则：在一般“重复/缺漏”分类之前，先单列经 03 严格识别的内部自检探针；只对 `MANUAL_REVIEW`、无专家、无关联标签/附件/transfer/操作/其他生产 FK 或软引用、原始 IMAP 头证明为本组自检的精确 ID 做备份后删除。不能将其改归 LuKai 继续留在待处理；不能仅用 `subject LIKE '[self-check]%'` 批删。已 `MANUAL_RESOLVED` 的历史行不纳入本次待处理清理，保留审计痕迹。
- 适用写路径：本次 `inbound_mail_processing` 逐 ID 修复和依赖核查。
- 违反后果：待处理噪声仍在、误删真实邮件或清掉人工审计记录。
- 来源：线上只读行 383 和同类聚合证据、`SelfCheckProbeDetector.kt:6-14`、`K-mailbox-inbound-source-authority`。

## 现状审计

### 线上只读快照（非执行时恒定值）
- 两账号：`LuKai` 为 `lukai@updates.szwebotech.cn`，`LuKai_QF` 为 `lukai@qingfeitalent.com`，均指向 `imap.exmail.qq.com:993`；当时 QF `enabled=0`，代码 `MailSenderAccountService.kt:43-60` 仍会轮询它。
- 游标：两边 UIDVALIDITY 同为 `1782107786`；快照 owner last_uid 434、QF last_uid 146。分开游标是重复抓取直接机制，不能用关 enabled 解决。
- 较早快照曾见 QF processing 27 行、INBOUND mail_record 15 行及初筛重复/缺漏 15/12、13/2；**这些数字现已过期，不可用于执行阈值或验收**。2026-09-23 后续只读重查：QF processing 为 `MANUAL_REVIEW=44`、`PROCESSED=2`，QF INBOUND mail_record 为 21。查询口径：`SELECT sender_account_code,process_status,COUNT(*) FROM inbound_mail_processing WHERE sender_account_code IN ('LuKai','LuKai_QF') GROUP BY sender_account_code,process_status`；`SELECT COUNT(*) FROM mail_record WHERE direction='INBOUND' AND sender_account_code='LuKai_QF'`。执行前再重采，不能继承今天的数字。(来源: `K-plan-quantified-claims-need-grep-receipts`)
- 当前 QF `[self-check]` 处理行：只读聚合 `SELECT sender_account_code,process_status,COUNT(*) FROM inbound_mail_processing WHERE subject LIKE '[self-check]%' GROUP BY sender_account_code,process_status` 返回 QF 待处理 23、人工已处理 2。示例 `id=383`：From=`lukai@updates.szwebotech.cn`、Subject=`[self-check] LuKai 1788498000276`、账号=`LuKai_QF`、状态=`MANUAL_REVIEW/CONTACT_NOT_FOUND`、无专家。对 23 条候选的专家、tag、attachment、transfer、operator_action 引用汇总均为 0；但完整 FK/软引用和原始 IMAP 头仍须停写时逐 ID 复核。两条已人工处理 `id=366,369` 为 `MANUAL_RESOLVED`，本轮不清。(来源: `K-process-single-all-callers`)
- 较早快照还见 QF mail_record 对应 `inbound_intent` 15 行；QF processing 对应 `inbound_mail_tag` 31 行、`mail_attachment_transfer` 4 行；QF mail_record 对应 `mail_attachment` 4 行。快照的 `operator_action_log` 和 `mail_record.source_inbound_id` 引用为 0。依赖数可能已变，`expert_document` 等不能假设为 0；执行前完整重查。

### `mail_sender_account` / `mail_inbox_cursor`
- Schema：`V1__create_business_tables.sql:1-25` 账号独立，01 新增 nullable owner；`V49__create_mail_inbox_cursor.sql:1-7` 以账号代码唯一。
- 写路径：`MailSenderAccountService.kt:74-174` 和 repository SQL `:23-90`，计划本次精确账号 UPDATE；`MailInboxCursorService.kt:60` 游标保存。当前两条游标不删除，QF 旧值仅作为审计留存。
- 读路径：`MailSenderAccountService.kt:43-60` 与 02 完成后的 owner 解析；`AutoMailReplyService.kt:773-907` 抓取/回填；批量入口 `BatchAutoMailReplyService.kt:24,57-79`。切换后须仅 owner 游标推进。
- 交互点：配置 UPDATE→02 单次抓取；当前旧 QF cursor→不得被新收信路径消费。

### `inbound_mail_processing` / `mail_record`
- Schema：V120 逻辑账号+代际+UID 唯一，01 增加物理 owner 唯一；`mail_record` V15 增业务账号和 `source_inbound_id`。不能只按 Message-ID 删除（该字段可空/可重复）。
- 写路径：收信写 `AutoMailReplyService.kt:204-375,971,1169,1231,1284`；processing 状态 copy/save 在 `UnmatchedInboundMailService.kt:193,238`、`PendingMailOperationService.kt:1522`、`InboundMailProcessingRepository.kt:35-47`；发信/手动记录见 02 现状审计；03 将使新探针在这些新建写路径之前返回；本次只修改预核验的 INBOUND/processing ID。(来源: `K-inbound-processing-write-paths`)
- 读路径：`MailboxConversationRepository.kt:433-495` 用 processing 展示收件/待处理，`MailboxConversationService.kt:151-193` 显示两个账号；`MailRecordRepository.kt` 的 INBOUND 列表/回复统计和 `source_inbound_id` 关联消费 mail_record；待处理队列消费 processing。(来源: `K-mailbox-inbound-source-authority`、`K-mail-record-source-inbound-id`)
- 交互点：processing 合并/归属→截图处计数和账号；mail_record 合并/归属→回复统计、历史来源和意图关联。

### 依赖数据表与文件
- Schema：`V6` `inbound_intent.mail_record_id` FK；`V7/V36/V118/V130` `mail_attachment` 允许 mail_record 或 processing 单一 owner，`expert_document.mail_attachment_id` FK；`V119` transfer 同时可引用 attachment/processing 且源唯一键包含旧账号代码；`V53` `inbound_mail_tag` 以 processing ID 索引；`V19` operator log 指 processing；`V42` QA link、`V9/V125` meeting 表指 mail_record。以生产 `information_schema.KEY_COLUMN_USAGE` 最终清点。
- 写路径：标签/意图/材料由 `AutoMailReplyService` 与其服务写入；本次 runbook 只能做受控依赖重指向/删除，不碰存储文件字节。`mail_attachment_transfer.account_code='LuKai_QF'` 的历史物理源可继续用旧 IMAP 凭据下载，不盲改为 LuKai。
- 读路径：材料/专家文档、传输 worker、标签筛选、意图和会话页面按上述 FK/ID 读取；合并后这些入口必须仍有相同有效内容。
- 交互点：processing/mail_record 删除→所有 FK/软引用检查；附件源账号→历史下载；原始文件路径→不得因删元数据而删物理文件。

## 实现方案

### 阶段 0：冻结与只读预检（I-1～I-5）
- 文件：执行时创建 `docs/runbooks/repair-lukai-shared-inbox.md`，包含本次实际命令、脱敏行映射、备份位置、前后 SQL 结果和回滚步骤；不在计划阶段生成验收勾选文件。
- 确认新 WAR 已包含 01～03 的验证结果（包括 03 的同组探针过滤）；复查运行 profile、Flyway 开关、数据库名、表结构/索引、最大迁移版本；比较 01 DDL 与生产 `SHOW CREATE TABLE`，若生产无 Flyway 则准备手动等价 DDL，DDL 前做可恢复数据库备份并验证能读。
- 进入维护窗口，停应用/调度，确认无活动收信/发送作业与数据库写入。只读重采两账号 UIDVALIDITY、相同 UID 头、每条候选原始 To/Cc、游标、QF processing/mail_record、全部 FK 和软引用；先按 I-5 分类 `self-check`，其余才进入 `keep/reassign/merge/unknown` 清单。若任一关键证据缺失或出现新类型依赖，停止并请用户审阅修订计划。

### 阶段 1：兼容 DDL 与数据分类（I-1～I-3、I-5）
- 文件：同一 runbook。生产 Flyway 关闭时，先 `SHOW CREATE TABLE` 判定 01 的等价 DDL 是否已应用；已存在且完全等价则记录并跳过，缺失时手工执行一次，部分存在/不等价则停下修订计划。执行后核验两列与唯一键。不得给旧行批量填 owner。
- 三套分类独立且有顺序：先按 I-5 逐 ID 验证内部探针；其余 processing 再依据物理 UID+三字段+raw To 分类；mail_record 依据业务行完整等价（包含正文/cleaned_body）和依赖等价。过期的 15/12、13/2 数字不作阈值，绝不写成固定 `LIMIT`/固定待删除 ID。专家联系人、已发 OUTBOUND 和旧 0 代际行原样。

### 阶段 2：事务内迁移（I-2～I-5）
- 文件：同一 runbook。先保存精确 ID 的旧行快照；按 FK 顺序将可证明独有的 QF 标签/意图/附件/transfer 重指向到 owner 原行，若 owner 已有语义相同资料，则仅在文件字节/存储路径/源 part 对等、没有 `expert_document` 或其他外键悬挂且备份可恢复时删重复元数据，物理文件一律不删。任何冲突立刻 ROLLBACK；不能“跳过错误继续”。
- I-5 自检误入待处理：逐 ID 备份并经 raw IMAP/字段/依赖复核后，只删除确认为内部探针的 `MANUAL_REVIEW` 行；示例 id=383 必须纳入待核，不按固定 ID 执行。`MANUAL_RESOLVED` 行 366/369 保留。对其他 QF processing，已证明与 LuKai 同一物理来信且依赖处理完的才合并删除 QF 重复行、保留 LuKai 行；无 owner 对应而 raw To=updates 的真实来信改 `sender_account_code='LuKai'`、`mailbox_owner_code='LuKai'` 并保留 ID/标签/附件；若唯一键冲突停下而不是强删。INBOUND mail_record 只删除依赖等价并已重指向的冗余行；无 owner 对等行改账号为 LuKai，保留意图/附件/ID。所有数量以现场分类重算。
- 同一维护窗口精确 UPDATE `mail_sender_account`：仅 `account_code='LuKai_QF'`、`sender_email='lukai@qingfeitalent.com'` 且原 `inbound_mailbox_code IS NULL` 的 1 行设为 `LuKai`；核对 `LuKai` 为独立 owner，两个账号的 SMTP/IMAP/限额/`enabled` 哈希或列值不变。QF 旧游标不删除、不回拨 LuKai 游标。
- COMMIT 前执行所有前后计数、唯一键、FK/软引用、会话重复计数断言；预期与分类表逐 ID 对齐，而非依赖旧快照。失败回滚。MySQL DDL 自带提交，回滚 DDL 只能按备份/反向变更方案执行，不能宣称普通 ROLLBACK 可撤销 DDL。

### 阶段 3：受控恢复与观察（I-1～I-5）
- 文件：同一 runbook。部署/启动 01～03 完整 WAR，先只做只读检查：QF 映射为 LuKai、Owner 游标与历史行数稳定、截图中的重复待处理消失。之后用专门测试邮箱分别投递 updates/QF 各 1 封；低风险手动检查一次，不开启自动回复/批量发送。确认每封恰好 1 条 processing 和正确逻辑账号、两账号只用一个物理游标，必要时再恢复原调度。
- 回滚：若代码启动失败，停止新应用，按已保存旧配置/行快照恢复 DML；数据库两列可暂留 nullable 供旧代码忽略，但必须在测试环境验证旧 WAR 对新增列兼容，不能猜测。若数据修复已提交、回滚需人工对照备份恢复并核查 FK，禁止简单重启旧 WAR 导致双轮询复发。

## 变更文件清单

| # | 文件 | 作用 |
|---|---|---|
| 1 | `docs/runbooks/repair-lukai-shared-inbox.md` | 现场预检、精确 ID 分类、执行证据、前后校验和回滚记录；生产 SQL/配置操作本身不算仓库文件，但只在额外授权后执行 |

## 验收标准

- I-1：两登录 UIDVALIDITY 与抽样相同 UID 头一致，全部待迁移候选逐行 raw To/Cc/Message-ID/From/ReceivedAt 可追溯；QF 配置更新影响恰好 1 行。
- I-2：备份可读取，依赖清单覆盖生产 `information_schema` 中所有指向 processing/mail_record/attachment 的外键及软引用；迁移后 FK 无孤儿，保留附件数量/文件字节/材料引用可核对；无 `DELETE WHERE sender_account_code` 范围删。
- I-3：重复/缺漏分类逐 ID 有证据；专家会话的旧双份收件/待处理归零、但独有真实来信仍存在；INBOUND mail_record 相关意图/标签/附件均可读。凡无法证明的行保持原样并中止上线，不误删。
- I-4：旧 0 代际行不变，QF 旧游标不推进，Owner 游标不回退；历史无新增 OUTBOUND、SMTP 发送日志无新记录；QF `enabled=0` 和所有发信配置不变。
- I-5：经严格证明的旧内部探针待处理数从执行时基线降为 0；示例 id=383 不再出现在待处理；已人工处理的 366/369 留存；未证明的 `[self-check]` 主题邮件不得自动删；删除 ID 与备份逐项一致。
- 部署闸门：手动 DDL 与 01 migration 等价；01～03（含 self-check）全部测试通过；停写窗口内迁移，首轮各地址测试信各恰好 1 条；观察窗口内不再形成同 UID 双 processing 或内部探针待处理。

## 人工验收清单

### A-1：既有异常已修、真实行未丢
- 前置条件：runbook 保存修复前 QF/Owner 逐 ID 截图、会话计数、附件清单与脱敏 SQL 快照，且迁移已提交。
- 操作步骤：打开原截图对应专家（如 `gilad.twig@gmail.com`）会话→待处理列表→附件/意图页，对照 runbook 的保留 ID。
- 预期结果：同一原始邮件只显示 1 条收件，不再同时显示 LuKai 和 LuKai_QF 两份待处理；runbook 标成“独有真实来信”的每个现场 ID 仍有对应记录；已存在的附件/意图可打开且数量与保留清单一致。
- 覆盖：I-2、I-3、processing→会话和 mail_record→意图/附件跨路径。

### A-2：新来信两个业务账号
- 前置条件：从两个外部测试邮箱分别给 `lukai@updates.szwebotech.cn` 与 `lukai@qingfeitalent.com` 各发 1 封带不同主题的无害测试信；自动回复关闭。
- 操作步骤：手动检查回复一次→分别打开两封信的专家会话→再次检查。
- 预期结果：第一封账号为 LuKai、第二封为 LuKai_QF；各只新增 1 条收件；第二次检查新增 0 条；没有自动发送邮件。
- 覆盖：I-1、I-3、I-4、配置→路由→会话跨路径。

### A-3：发信配置与游标不变项
- 前置条件：runbook 存有迁移前两个账号的发信字段、enabled 和两条 cursor 值。
- 操作步骤：在账号页重开 LuKai 与 LuKai_QF；查看后台收信检查日志与 cursor 查询结果。
- 预期结果：QF 的“共享收件箱主账号”为 LuKai、`enabled=false`、发件邮箱仍为 `lukai@qingfeitalent.com`；LuKai 为独立收件箱；两账号的 SMTP 用户名、日限额与备份值相同；新信后只有 LuKai cursor 增加，QF 旧 cursor 不变。
- 覆盖：I-1、I-4、旧配置回归。

### A-4：缺头/附件/退信回归
- 前置条件：测试邮箱准备一封无可判收件人 BCC 信、一封 QF 附件信，以及一封引用 QF OUTBOUND Message-ID 的测试 DSN。
- 操作步骤：手动检查→查看待处理、材料下载、退信列表。
- 预期结果：BCC 信只有 1 条 `RECIPIENT_UNRESOLVED` 待处理、0 自动回复；附件下载成功且只出现 1 份材料；DSN 只给 LuKai_QF 增加 1 条退信，LuKai 增加 0 条。
- 覆盖：I-2～I-4、02/03 跨阶段契约。

### A-5：历史自检噪声与新自检
- 前置条件：runbook 有经原始 IMAP 头和全依赖核验的内部探针精确 ID 清单，包含原待处理 id=383；另记录人工已处理 id=366/369。生产代码已按 03 部署。
- 操作步骤：查看待处理列表并按 `[self-check]` 搜索→分别对 LuKai 和 LuKai_QF 触发一次自检→手动检查回复两次→再搜待处理与会话。
- 预期结果：执行前已确认的内部探针待处理剩余 0 条，id=383 不出现；id=366/369 的审计记录仍存在；新两封探针带来的待处理/会话/业务邮件新增数均为 0，第二次检查新增数为 0。
- 覆盖：I-2、I-5、03 过滤→04 历史修复→列表跨阶段契约。
