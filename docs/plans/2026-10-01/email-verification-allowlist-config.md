# Emailable 放行结果：任务配置（第 2/3 计划）

依赖：[执行与审计](email-verification-allowlist-backend.md) 验证通过；后续：[前端](email-verification-allowlist-frontend.md)。状态：待评审。证据基线 2026-10-01 当前工作树，非线上版本声明。

## 需求描述

在现有任务配置 API 保存、读取允许的 Emailable 结果，并传给定时/按配置手动执行。新建默认仅 deliverable；存量配置保留原三态放行；更新未提供字段保持现值。

必须保持：配置身份/软删除/定时重排机制不变；旧 typed 更新保留新选择；关闭验证不改变放行选择；手动覆盖只写执行快照，不回写任务配置；在途任务不受编辑影响。

范围外：KV 双写、全局策略、每账号策略、新接口、新表、自动改线上任务、前端（下一计划）。

## 关键不变量

### Invariant I-1：一列配置事实源
- Rule：新增 email_verification_allowed_states_json TEXT NULL；NULL=旧配置兼容三态全放行；非 NULL 必须为合法数组且元素仅 deliverable/risky/unknown，[] 明确全跳过。损坏 JSON/JSON null/非数组/非法元素不得当旧 NULL 或全放行。规范化顺序固定 deliverable,risky,unknown，重复值去重。
- Applies to：迁移、实体、配置服务写入/读取、toExecutionSnapshot。
- Violation consequence：静默扩大发送范围或无法区分旧配置与明确空选择。
- 来源：K-batch-send-setting-kv；BatchSendTaskConfig.kt:7-46；BatchExecutionModels.kt:450-473 已有严格解析先例。

### Invariant I-2：新建默认与更新保留
- Rule：CreateCommand.emailVerificationAllowedStates 缺省=[deliverable]；UpdateCommand.emailVerificationAllowedStates: List<String>?=null，缺字段或 null 都保留 existing 原始列（包括 SQL NULL）；显式 [] 存 []。View 必须回有效数组，旧 SQL NULL 回三项。create 显式 null 不可升级成旧模式，按非 nullable DTO 绑定拒绝。
- Applies to：create/update/updateLegacyConfig、ConfigFields/NormalizedConfig/toFields/toView、setEnabled/softDelete 的 copy。
- Violation consequence：旧客户端改 cron 清空策略，或新任务默认误放 risky。
- 来源：K-batch-config-legacy-adapter-field-preservation；BatchSendTaskConfigService.kt:103-145,199-233,640-760。

### Invariant I-3：配置进快照
- Rule：toExecutionSnapshot 解析并复制本列的有效列表。定时、按配置手动、旧实体启动路径都消费相同列表；已启动任务仅消费自己的 snapshot。手动请求传 [] 与三态数组覆盖来源配置，不更新配置列。
- Applies to：BatchExecutionModels.kt 的实体转换、BatchSendControlService 既有调用、request_payload 序列化。
- Violation consequence：前端保存但执行失效，或手动操作污染定时配置。
- 来源：K-batch-task-config-snapshot-log-identity。

### Invariant I-4：既有配置生命周期不变
- Rule：setEnabled/softDelete 保留列；cron 重排事件不新增、不取消；验证关闭仍保存选择，材料提醒仍禁止开启验证；历史 emailVerificationEnabled/excludeVerifiedUnavailableEmails 不因迁移或保存自动开启。
- Applies to：V145、配置服务 create/update/setEnabled/softDelete、旧 typed API。
- Violation consequence：保存策略误开自动发信或丢失原设置。
- 来源：BatchSendTaskConfigService.kt:148-185,370-375,600-602。

## 现状审计

### batch_send_task_config（本计划唯一新增列）
- Schema：V72:1-31 建表；id、legacy_code/active_config_name 唯一键、deleted_at 软删除、template_id 外键；V139:3-4 验证开关默认 false；V142:1-2 历史不可用过滤默认 false。已有数组字段落 JSON 文本，无需新增关联表。
- 生产写路径：BatchSendTaskConfigService.create/update → saveConfig → repository.save（:65-145,608-614）；setEnabled → repository.save(copy)（:149-169）；softDelete → repository.save(copy)（:173-184）；updateLegacyConfig → update（:199-233）。旧接口 /config、/types/{sendType}/config 使用实体适配器，不扩展 BatchSendSettingService KV。（来源：K-batch-send-setting-kv、K-batch-send-legacy-routes-entity-ssot）
- 历史迁移数据写路径：V72 插入旧 KV 转实体，V74 编码修复，V91 回填轮次，V93/V97/V98/V108/V110/V135 回填数组字段；V92/V95/V99/V103/V128/V139/V142 结构变更。它们已应用，不修改。
- 读路径：BatchSendTaskConfigService.list/get/toView/getLegacyConfig/updateLegacyConfig/setEnabled；BatchSendTaskConfigRepository 的 findByIdAndDeletedAtIsNull、列表、名称唯一检查、findByLegacyCode、findAllByAutoEnabledTrueAndDeletedAtIsNullOrderByIdAsc；BatchSendControlService:66,107,124,154,258 的 toExecutionSnapshot；task/service/BatchSendScheduler 读取启用实体安排 cron。仓储使用 SELECT * 或派生查询，新列由 Spring Data JDBC 映射，无手写列清单需要扩大。
- 检索回执见 [证据附件](email-verification-allowlist-evidence.md) E-1/E-2。
- Interaction points：IP-1 API create/update → DB → View 回显；IP-2 DB → 所有实体启动快照；IP-3 旧 typed update/启停/软删除 → 新列保留。

### task_execution.request_payload（不加 DB 列）
- V4__create_task_execution.sql:6 已有 TEXT request_payload；TaskExecutionService:152,231 序列化请求写入；BatchSendControlService:356 传入完整请求；第 1 计划已扩展 snapshot。历史明细读取 requestSnapshot，不读当前配置。
- Interaction points：IP-4 来源配置/手动覆盖 → 固定快照 → 历史详情；本计划不改 task 表写法。

### 迁移与测试边界
- 当前迁移目录最大 V144；V145 暂未占用。执行前再次核对，发生冲突仅顺延新迁移版本并同步计划及测试，禁止覆盖历史文件。
- FlywayMigrationIntegrationTest.kt:2030-2040 flyway() 默认迁移到最新，但 :59,87,123 等目标版本断言还写死 142。这是现有测试基线问题；本计划加 V145 时只把「最新版本」断言改为 145，固定中间目标 139/140/141 等保持原值，不能当成本次业务失败。
- pom.xml:173-177 注入 migrationIt/mysqlIt；:240-250 对应 profile。IT 需要 Testcontainers Docker，不以跳过代替通过。

## 实现方案

### T-1：迁移及模型（I-1、I-2、I-4）
文件：
- src/main/resources/db/migration/V145__add_batch_email_verification_allowed_states.sql
- src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt

新增迁移逐字：
~~~sql
ALTER TABLE batch_send_task_config
    ADD COLUMN email_verification_allowed_states_json TEXT NULL;
~~~
实体 nullable String 默认 null；View 非 nullable List 输出有效选择；Create 非 nullable List 默认 [deliverable]；Update nullable List 默认 null。不回填存量、不修改旧迁移、不加字段到 KV DTO。

### T-2：配置映射和解析（I-1～I-4）
文件：
- src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt
- src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt

复用第 1 计划允许值校验，在 BatchExecutionModels.kt 增加严格 JSON 解析 helper：SQL NULL→三项；JSON 文本必须 array/textual/合法值，空数组保持空。所有读取统一使用它。create 规范化并 writeValueAsString；update 在入口合并，缺字段保留原始 JSON；不要把 null 转 []。覆盖 create、update、updateLegacyConfig、toView、ConfigFields、NormalizedConfig、三个 toFields 和 toExecutionSnapshot。旧 typed 更新明确保留既有列语义，启停/软删 copy 保持。新数据消费者为现有 View 和启动快照，无新接口。

### T-3：回归（I-1～I-4）
文件：
- src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt
- src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlServiceTest.kt
- src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt

配置保存→捕获实体→View→toExecutionSnapshot 验证完整链；旧 NULL、显式 []、去重/顺序、坏 JSON 拒绝；旧 typed update/null update/启停/软删保留；定时及按配置手动传相同列表，运行中修改不影响旧 snapshot；迁移前 V144 旧数据→V145 列为 SQL NULL，新增保存数组可回读。最新迁移断言同步至实际版本。

## 变更文件清单

| # | 文件 |
|---|---|
| 1 | src/main/resources/db/migration/V145__add_batch_email_verification_allowed_states.sql |
| 2 | src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchSendTaskConfig.kt |
| 3 | src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt |
| 4 | src/main/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigService.kt |
| 5 | src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendTaskConfigServiceTest.kt |
| 6 | src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchSendControlServiceTest.kt |
| 7 | src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt |

7 文件，任务配置一个子系统；同第 1 计划共享模型/测试，严格顺序执行。

## 验收标准

- I-1：DB NULL/[]/三项/坏 JSON/JSON null/未知值/undeliverable 全覆盖；坏值禁止降级。新列仅一列，无新表/KV key。
- I-2：新建缺字段仅 deliverable；更新缺字段/null 逐字保留旧列；显式 [] 回显 []；旧 SQL NULL 的 View 回三项。
- I-3：定时、配置手动、独立手动与兼容实体启动快照均传值；source 配置不被手动覆盖改写。
- I-4：启停/软删保留选择，旧 typed 更新保留；验证开关关闭后选择仍在，cron/autoEnabled 不变。
- 命令：
~~~sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=BatchSendTaskConfigServiceTest,BatchSendControlServiceTest test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test
~~~
不得在生产运行测试迁移；Docker 不可用需明确记 IT 未验证。

## 人工验收清单

以下可先用浏览器开发者工具复制现有配置 API 请求作预发测试；不是要求改生产 DB。

### A-1：新旧配置回显
- 前置条件：预发迁移前已有旧任务 C；保留 C 的 autoEnabled/cron/验证开关基线；升级 V145。
- 操作步骤：1. GET /api/mail/batch-send/configs/{C}。2. 复制一个合法 create 请求，删除 emailVerificationAllowedStates 字段，POST /configs 新建 N。
- 预期结果：C 返回三项 [deliverable,risky,unknown]；N 返回 [deliverable]；C 的 autoEnabled/cron/emailVerificationEnabled 与升级前相等。
- 覆盖：I-1、I-2、I-4；IP-1。

### A-2：空选择与禁发值
- 前置条件：预发存在合法配置 C，先保存 [risky,unknown]。
- 操作步骤：1. PUT C 显式数组 []，再 GET。2. PUT C 数组 [undeliverable]。3. 再 GET。
- 预期结果：第 1 次回显 []；第 2 次 HTTP 400，配置仍为 []，不启动发信。
- 覆盖：I-1、I-2；IP-1。

### A-3：旧入口与生命周期保留
- 前置条件：预发 seeded legacy INTRODUCTION 配置设 [unknown]，记录当前 cron；邮件验证开关关闭。
- 操作步骤：1. 通过 /api/mail/batch-send/types/INTRODUCTION/config 的既有 PUT 请求只改每封间隔。2. GET 配置；启用再停用该任务（测试 cron 设未来日期）。3. 再 GET；最后软删除并只读查询该行。
- 预期结果：每一步列仍为 ["unknown"]；验证开关仍 false；最终 deleted_at 非空且 auto_enabled=false；未到 cron 前没有新执行。
- 覆盖：I-2、I-4；IP-3。

### A-4：定时与手动快照隔离
- 前置条件：隔离预发测试任务 C=[deliverable]，SMTP 指向测试箱；仅一条具有效历史 risky 原始记录的未联系候选人；验证开启。
- 操作步骤：1. 由定时触发执行 X。2. 手动从 C 复制请求，改列表 [risky] 启动 Y。3. GET C 和执行 X/Y 的详情 requestSnapshot。
- 预期结果：X 快照仅 deliverable，risky 行 SKIP；Y 快照仅 risky，risky 行 PASS；C 仍只含 deliverable。修改 C 后 X 的 requestSnapshot 不变。
- 覆盖：I-3、I-4；IP-2、IP-4。

