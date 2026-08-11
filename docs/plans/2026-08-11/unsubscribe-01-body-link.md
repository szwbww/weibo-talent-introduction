# Plan 01 — 冷外联邮件正文退订链接落地

> 顺序位置：退订链路补全的第 1 个子计划，无前置依赖。见 [unsubscribe-closure-master.md](unsubscribe-closure-master.md)
> 优先级：P0 —— Gmail 实际收信测试的前置阻塞项
> 共享证据引用：主索引 E-1、E-5、E-6、E-7

## 需求描述

### Observable outcome

1. INTRODUCTION 与 MATERIAL_REMINDER 外发邮件正文末尾出现一行真实可点击的退订链接，形如
   `https://<baseUrl>/u/unsubscribe?token=<base64email>.<base64hmac>`。
2. 迁移在**开启 Flyway 占位符替换**的既有生产配置下不会导致启动失败。

### What must NOT change

- `mail_compose_template_block` 中 INTRODUCTION / MATERIAL_REMINDER 现有正文文字一字不变，退订行只能**追加**在末尾。
- 其他模板（MEETING_INVITATION、MEETING_CONFIRMATION、QA 相关）正文完全不变。
- `unsubscribeUrl` 的生成逻辑（`UnsubscribeTokenService.unsubscribeUrl()` `:37-38`）不变。
- 退订 header 的生成条件（`SmtpMailDeliveryService.kt:57`）不变 —— 收窄是 Plan 03 的事。
- 已应用的迁移文件（`V1`..`V86`）一律不改（CLAUDE.md 硬规则）。
- 运营在后台手工编辑过的模板正文不得被迁移覆盖。

### Out of scope

- 会议邮件族补 `unsubscribeUrl` 注入 → Plan 04。
- 退订 header 收窄 → Plan 03。
- `enabled()` 为 false 时的 fail-fast → Plan 05。
- 退订行的英文文案措辞优化 / 多语言 → 运营内容决策，本计划给出一版即可。
- HTML 版正文的退订链接样式（当前 INTRODUCTION 走纯文本 `html = false`，`IntroductionMailComposer.kt:36-43` 未设 html=true）。

## 关键不变量

### Invariant I-1：正文 SSOT 唯一

- Rule：邮件正文的唯一权威存储是 `mail_compose_template_block.custom_text`（`template_id` 关联 `mail_compose_template.template_code`，`block_type = 'CUSTOM_TEXT'`）。**禁止**为同一内容变更同时写 `mail_template.body`。
- Applies to：本计划新增的 `V87` 迁移。
- Violation consequence：双写产生两份漂移的正文；`mail_template` 已无读取方（主索引 E-6），写它是纯噪声，且会让后续排查误以为它是 SSOT。
- 来源：original（证据见主索引 E-6；`V71__update_material_reminder_template.sql:2-18` 的双写是须停止沿用的历史做法）

### Invariant I-2：迁移对运营运行时改动幂等且不覆盖

- Rule：迁移只能用 `CONCAT(custom_text, <退订行>)` **追加**，且 `WHERE` 必须带 `custom_text NOT LIKE '%unsubscribeUrl%'` 守卫。禁止 `SET custom_text = '<整段新正文>'` 形式的整体覆盖。
- Applies to：`V87__append_unsubscribe_line_to_cold_outreach_templates.sql`。
- Violation consequence：整体覆盖会抹掉运营在后台编辑器里做过的正文调整；无 NOT LIKE 守卫会在重复执行或已手工添加过退订行的库上产生两行重复退订链接。
- 来源：K-qa-rule-runtime-vs-migration-writes（CLAUDE.md 团队沉淀知识：Flyway 对 `qa_rule` 的 UPDATE 会覆盖运营运行时改动，关键词/正文迁移须 CONCAT 带 NOT LIKE、INSERT 带 NOT EXISTS）

### Invariant I-3：迁移文本中的 `${...}` 必须能在 Flyway 占位符替换开启时存活

- Rule：本计划**必须**在 `src/main/resources/application.yml` 的 `spring.flyway` 段显式设置 `placeholder-replacement: false`。设置该项与新增含 `${unsubscribeUrl}` 的迁移是**同一次提交内不可分割的一对**。
- Applies to：`V87` 迁移文件；`src/main/resources/application.yml`。
- Violation consequence：只加迁移不改配置 → 生产启动时 Flyway 抛 `No value provided for placeholder expressions: ${unsubscribeUrl}`，应用起不来。这是**部署即挂**级别的后果，不是隐患。
- 反向风险已核：`grep -l '\${' src/main/resources/db/migration/*.sql` 命中的 `V2`/`V9`/`V56`/`V71` 中的 `${...}` 全部是**邮件模板变量（数据）**，无任何迁移依赖 Flyway 占位符做真实替换，故关闭该功能对存量迁移无行为影响。
- 来源：original（证据见主索引 E-5）

### Invariant I-4：退订行渲染后不得残留字面占位符

- Rule：追加的退订行经 `MailComposeTemplateService.renderText()` 渲染后，输出中不得包含子串 `${unsubscribeUrl}`；`unsubscribeTokenService.enabled()` 为 true 时必须包含 `/u/unsubscribe?token=`。
- Applies to：INTRODUCTION 路径（`IntroductionMailComposer.compose()` `:18` → `MailVariableService.buildVariables()`）、MATERIAL_REMINDER 路径（`ManualExpertMailService.composeComposeTemplate()` `:197-199`）。
- Violation consequence：`renderText()` `:594-596` 的 fold 只替换 map 中存在的 key；若把退订行加到不注入该变量的模板上，外发正文会出现字面的 `${unsubscribeUrl}` 六个字符（主索引 E-1）。本计划只动这两个模板正是因为它们**已验证**能拿到该变量。
- 来源：original（证据见主索引 E-1）

## 现状审计

> Step 1b-fe **未触发**：本计划变更文件清单中无任何 `frontend_paths`（`src/main/resources/static`）下的文件，也无 `.html` / `.css` / 前端 `.js`。故本计划无 `## 样式契约` 节。

### Store：MySQL `mail_compose_template` + `mail_compose_template_block`

**Schema**（`V61__create_mail_compose_template.sql`，后续 `V62` / `V84` 增列）：

```sql
mail_compose_template(id, template_name, subject, description, enabled, created_at, updated_at,
                      template_code /*V62, UNIQUE*/, mail_type /*V62*/, required_keys /*V84, VARCHAR(500) NULL*/)
mail_compose_template_block(id, template_id FK->mail_compose_template(id) ON DELETE CASCADE,
                            block_order INT, block_type VARCHAR(30) /*QA_RULE|REPLY_SNIPPET|CUSTOM_TEXT*/,
                            ref_id BIGINT NULL, custom_text TEXT NULL)
```

INTRODUCTION 与 MATERIAL_REMINDER 当前形态：单块 `block_order = 0`、`block_type = 'CUSTOM_TEXT'`、`ref_id = NULL`，`custom_text` 来自
- INTRODUCTION：`V2__seed_mail_templates.sql` 的 body，经 `V62__unify_mail_templates.sql:56-72` 搬入；
- MATERIAL_REMINDER：`V71__update_material_reminder_template.sql:37-52` 重建。

**Write paths（全量 grep `mail_compose_template_block`）：**

1. `V61__create_mail_compose_template.sql` — 建表。
2. `V62__unify_mail_templates.sql:38-72` — DELETE 后 INSERT，从 `mail_template.body` 搬入 4 个模板。
3. `V64__add_subject_variants_and_snippet_variant_group.sql` — 变体相关列。
4. `V71__update_material_reminder_template.sql:29-52` — MATERIAL_REMINDER 的 DELETE + INSERT。
5. `V78__decouple_compose_templates_from_qa_rule.sql` — 解耦 QA_RULE 引用。
6. `MailComposeTemplateService` 的后台增删改（运营在模板编辑器保存块）。
7. **本计划新增** `V87` — 仅 UPDATE `custom_text`（追加）。

**Read paths：**

1. `MailComposeTemplateService.resolveBlocks()`（`:248` QA_RULE / `:279` REPLY_SNIPPET / `:294` CUSTOM_TEXT）→ `renderText()` `:588`。
2. `renderByCode(templateCode, variables, variantSeed)` — `IntroductionMailComposer`、`MeetingInvitationMailComposer`、`AutoMailReplyService.sendMeetingInvitation`、`AutoReplyPreviewService`、`MeetingScheduleService` 使用。
3. `render(templateId, variables, variantSeed)` — `ManualExpertMailService.composeComposeTemplate()` `:206-210`。
4. 后台预览接口 `MailComposeTemplateController`。
5. `mail_template` — **零读取方**（主索引 E-6），不构成 read path。

**Interaction points：**

- **IP-1**：`V87` 写入的退订行 × `MailComposeTemplateService.renderText()` 的变量替换。只有当渲染方 variables map 含 `unsubscribeUrl` 时才安全 → 决定了本计划只覆盖 INTRODUCTION 与 MATERIAL_REMINDER 两个 `template_code`（I-4）。
- **IP-2**：`V87` 写入 × 运营在后台编辑器对同一块的历史修改。追加式 + NOT LIKE 守卫处理（I-2）。
- **IP-3**：`V87` 文件文本 × Flyway 占位符解析器。`placeholder-replacement: false` 处理（I-3）。
- **IP-4**：`V87` 追加的正文 × `PersonalizationGateService.evaluate()`（`ManualExpertMailService.kt:215-220`、`IntroductionMailComposer.kt:27-28`）。该门禁只对 `required_keys` 中声明的 key 做 `fallbackKeys` 判定（`PersonalizationGateService.kt:44-53`，`requiredKeys` 为空即不阻断）。`unsubscribeUrl` 不可能出现在任何模板的 `required_keys` 中，三重证据：① `V84__add_required_keys_to_compose_template.sql` 只 `ADD COLUMN required_keys VARCHAR(500) NULL`，不填值；② 无任何后续迁移写该列（`grep -l required_keys src/main/resources/db/migration/*.sql` 仅 V84）；③ **无运行时写入路径** —— `MailComposeTemplateCommand`（`MailComposeTemplateService.kt:612-621`）不含 `requiredKeys` 字段，`MailComposeTemplateController` 只在 `:55` 读取。故追加退订行**不会**触发个性化门禁，本计划无需改动门禁相关代码。

### Store：`src/main/resources/application.yml`（Flyway 段）

- 当前 `:8-10` 仅 `enabled` 与 `locations`，`placeholder-replacement` 缺省为 true。
- 4 个测试类各自显式关闭（主索引 E-5 列出行号），说明缺省值与本仓库迁移内容不兼容。
- 本计划把该设置从"每个测试各自打补丁"上移为"生产配置显式声明"。测试中已有的显式关闭保留不动（它们通过 `@TestPropertySource` 覆盖，与主配置同值，无冲突）。

## 实现方案

### 任务 T-1：生产配置显式关闭 Flyway 占位符替换（遵循 I-3）

文件：`src/main/resources/application.yml`

在 `spring.flyway` 段（当前 `:8-10`）追加一行，并写明原因注释：

```yaml
  flyway:
    enabled: true
    locations: classpath:db/migration
    # 迁移中的 ${...} 是邮件模板变量（数据），不是 Flyway 占位符。
    # 开启替换会导致含 ${} 的正文迁移抛 "No value provided for placeholder expressions"。
    placeholder-replacement: false
```

不改 `src/test/resources/application.yml` —— 测试侧已有 4 处显式关闭，同值，不动以缩小 diff。

### 任务 T-2：新增 V87 迁移追加退订行（遵循 I-1、I-2、I-4）

文件：`src/main/resources/db/migration/V87__append_unsubscribe_line_to_cold_outreach_templates.sql`（新建）

```sql
-- V87: 冷外联邮件（INTRODUCTION / MATERIAL_REMINDER）正文追加退订链接行。
-- 只追加不覆盖：保护运营在后台编辑器里的历史修改（见 plan I-2）。
-- NOT LIKE 守卫保证幂等：已含退订占位符的块跳过。
-- 只覆盖这两个 template_code：它们的渲染路径已注入 unsubscribeUrl 变量（见 plan I-4）。
-- 不写 mail_template：该表已无代码读取方（见 plan I-1）。

UPDATE mail_compose_template_block b
JOIN mail_compose_template t ON t.id = b.template_id
SET b.custom_text = CONCAT(
        b.custom_text,
        '\n\n---\nIf you would prefer not to receive further emails from us, you can unsubscribe here: ${unsubscribeUrl}'
    )
WHERE t.template_code IN ('INTRODUCTION', 'MATERIAL_REMINDER')
  AND b.block_type = 'CUSTOM_TEXT'
  AND b.custom_text IS NOT NULL
  AND b.custom_text NOT LIKE '%unsubscribeUrl%';
```

约束说明（执行 agent 不得偏离）：

- 退订行文案逐字如上，含前导的 `\n\n---\n` 分隔。
- `WHERE` 四个条件全部保留，不得删减。
- 不得追加任何 `mail_template` 的 UPDATE。
- 不得使用 `SET b.custom_text = '<完整正文>'` 形式。

### 任务 T-3：迁移文本断言测试（遵循 I-1、I-2、I-3）

文件：`src/test/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribeBodyLinkMigrationTest.kt`（新建）

沿用 `QaSeedEncodingRepairMigrationTest` 的文本断言范式（主索引 E-7），无需 Docker。用例：

1. `V87 appends unsubscribe placeholder to both cold outreach templates` — 读 V87 文件，断言同时含 `'INTRODUCTION'`、`'MATERIAL_REMINDER'`、`\${unsubscribeUrl}`。
2. `V87 uses CONCAT append and never whole-body overwrite` — 断言含 `CONCAT(`，且**不含**正则 `SET\s+b\.custom_text\s*=\s*'`（整体覆盖形态）。
3. `V87 guards against duplicate application` — 断言含 `NOT LIKE '%unsubscribeUrl%'`。
4. `V87 does not write the dead mail_template table` — 断言不含 `mail_template`（注意需排除注释中的提及：断言时先剔除以 `--` 开头的行再匹配）。
5. `production flyway config disables placeholder replacement` — 读 `src/main/resources/application.yml`，断言含 `placeholder-replacement: false`。**这条是 I-3 的守卫**，防止后人删掉配置导致部署即挂。
6. `no other template code is touched by V87` — 断言不含 `MEETING_INVITATION`、`MEETING_CONFIRMATION`。

### 任务 T-4：退订行渲染断言（遵循 I-4）

文件：`src/test/kotlin/com/weibo/talentintroduction/mail/service/MailVariableServiceTest.kt`（既有，追加用例）

该文件已有 `${unsubscribeUrl}` 相关用例（`:117-161`：enabled/disabled/fallbackKeys 三种），沿用其既有 fixture 追加两条：

1. `cold outreach unsubscribe line renders a real url` — 输入即 T-2 追加的**逐字**退订行文本，断言渲染结果含 `/u/unsubscribe?token=`，且 `!rendered.contains("\${unsubscribeUrl}")`。
2. `cold outreach unsubscribe line renders empty when token service disabled` — `UnsubscribeProperties(baseUrl = "", secret = "")` 下，断言渲染结果**不含** `${unsubscribeUrl}` 字面量（当前实现返回空串，见 `MailVariableService.kt:251-263`）。此用例同时把"配置为空时正文出现一句无链接的悬空文案"这一已知缺陷**固化为可见事实**，供 Plan 05 处理；本计划不修复它。

## 变更文件清单

| # | 文件 | 类型 | 改动 |
|---|---|---|---|
| 1 | `src/main/resources/application.yml` | 修改 | flyway 段加 `placeholder-replacement: false` + 原因注释（T-1） |
| 2 | `src/main/resources/db/migration/V87__append_unsubscribe_line_to_cold_outreach_templates.sql` | 新建 | 追加退订行（T-2） |
| 3 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/UnsubscribeBodyLinkMigrationTest.kt` | 新建 | 6 条迁移/配置文本断言（T-3） |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailVariableServiceTest.kt` | 修改 | 追加 2 条渲染断言（T-4） |

文件数：4（≤10 ✅）。子系统数：1（模板正文内容 + 其迁移配置前提）（≤2 ✅）。新增数据字段：0（≤1 ✅）。

## 验证命令

> 本项目**必须**用 JDK 11（zulu-11），裸 `mvn` 会构建失败。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 本计划新增测试类（快速迭代用）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=UnsubscribeBodyLinkMigrationTest

# 本计划修改的既有测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailVariableServiceTest

# 单方法（定位失败时）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='UnsubscribeBodyLinkMigrationTest#production flyway config disables placeholder replacement'

# 真实执行迁移（可选，需本机 Docker；默认被 @EnabledIfSystemProperty 跳过）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：退出码 0，且输出含 `Tests run: N, Failures: 0, Errors: 0`（`mvn clean package` 额外要求 `BUILD SUCCESS` 与 WAR 产物生成）。
来源：CLAUDE.md 「Commands」章节与项目元信息 `test_command` / `build_command`；`-DmigrationIt=true` 来自 `FlywayMigrationIntegrationTest.kt:21` 的 `@EnabledIfSystemProperty` 实测。

## 验收标准

- **I-1**：`grep -n "mail_template" src/main/resources/db/migration/V87__*.sql` 除注释行外零命中；`UnsubscribeBodyLinkMigrationTest` 第 4 条用例通过。
- **I-2**：V87 含 `CONCAT(` 且含 `NOT LIKE '%unsubscribeUrl%'`；不含 `SET\s+b\.custom_text\s*=\s*'` 形态；`UnsubscribeBodyLinkMigrationTest` 第 2、3 条用例通过。人工另需在预发库上**连跑两次** `V87` 语句体（不经 Flyway），断言第二次 `affected rows = 0`。
- **I-3**：`src/main/resources/application.yml` 含 `placeholder-replacement: false`；`UnsubscribeBodyLinkMigrationTest` 第 5 条用例通过。
- **I-4**：`MailVariableServiceTest` 新增 2 条用例通过 —— enabled 时渲染出 `/u/unsubscribe?token=`，两种配置下均不残留 `${unsubscribeUrl}` 字面量。
- **IP-1**：V87 的 `template_code IN` 列表严格等于 `('INTRODUCTION', 'MATERIAL_REMINDER')`；`UnsubscribeBodyLinkMigrationTest` 第 6 条用例断言不含会议类 code。
- **IP-4**：`git diff` 显示以下 5 个文件零改动 —— `mail/service/PersonalizationGateService.kt`、`template/domain/MailComposeTemplate.kt`、`template/service/MailComposeTemplateService.kt`、`template/controller/MailComposeTemplateController.kt`、`mail/service/IntroductionMailComposer.kt`。
- 回归：执行「验证命令」节的全量测试命令通过；执行「验证命令」节的构建命令通过。

## 人工验收清单

### A-1：INTRODUCTION 邮件正文出现真实退订链接

- 前置条件：预发环境已应用至 V87；`UNSUBSCRIBE_BASE_URL` 设为 HTTPS 地址、`UNSUBSCRIBE_SECRET` 非空；存在一条 L2 候选专家（有合法邮箱）且未在抑制名单中；至少一个 enabled 发件账号。
- 操作步骤：
  1. 后台 → 模板管理 → 打开 `INTRODUCTION` 模板，查看正文预览。
  2. 触发一次单个专家的人工首封发送（或批量发送限 1 封），收件人用自己可访问的测试邮箱。
  3. 在收件箱打开该邮件，拉到正文末尾。
- 预期结果：
  - 后台预览末尾出现独立一段：`---` 换行后 `If you would prefer not to receive further emails from us, you can unsubscribe here: https://example.com/u/unsubscribe?token=preview`（预览走 `previewFallbacks`，显示示例 URL）。
  - 实际收到的邮件末尾同一段，URL 为 `https://<你配置的 baseUrl>/u/unsubscribe?token=` 加一串 `xxx.yyy` 形式的 token，**不是** `${unsubscribeUrl}` 字面量，**不是** `example.com`。
  - 正文其余文字与改动前逐字一致（开头 `Dear Professor,`，`This is my e-mail: <发件邮箱>` 一行仍在）。
- 覆盖：需求描述 observable outcome 1；I-4；IP-1

### A-2：MATERIAL_REMINDER 邮件正文出现真实退订链接

- 前置条件：存在一条 APPLICATION 层、带「承诺回复材料」标签、有历史入站记录的联系人。
- 操作步骤：
  1. 专家详情页 → 发送邮件 → 选择 `Material Reminder Email` 模板 → 预览。
  2. 发送到自己的测试邮箱。
  3. 打开收到的邮件，拉到末尾。
- 预期结果：同 A-1 的链接形态；`Best regards,` 三行签名之后才是 `---` 与退订行。
- 覆盖：需求描述 observable outcome 1；I-4

### A-3：会议邮件正文未被误改（回归）

- 前置条件：存在一条 `WAITING_REPLY` 状态、可触发会议邀请的联系人。
- 操作步骤：
  1. 后台 → 模板管理 → 打开 `MEETING_INVITATION` 与 `MEETING_CONFIRMATION`，查看正文。
  2. 触发一次会议邀请发送到测试邮箱。
- 预期结果：两个模板正文**均无** `---` 分隔与退订行；收到的会议邮件正文末尾是 `Looking forward to hearing from you.`，其后无退订行、无 `${unsubscribeUrl}` 字面量。
- 覆盖：What must NOT change 第 2 项；IP-1

### A-4：运营手工编辑过的正文未被迁移覆盖（回归）

- 前置条件：在**应用 V87 之前**，于后台把 `INTRODUCTION` 正文任意一句改掉（例如把 `Sincerely,` 改成 `Best regards,`）并保存；记录改后全文。
- 操作步骤：
  1. 部署新版本，让 Flyway 应用 V87。
  2. 回到后台查看 `INTRODUCTION` 正文。
- 预期结果：你手工改的那句仍是改后的样子（`Best regards,`），末尾多出退订行。**不是**回退成 V2 的原始文案。
- 覆盖：What must NOT change 第 6 项；I-2；IP-2

### A-5：迁移重复执行不产生重复退订行

- 前置条件：预发库已应用 V87 一次。
- 操作步骤：
  1. 在预发库上手工执行一次 V87 的 UPDATE 语句体（不经 Flyway）。
  2. 观察返回的 affected rows。
  3. 后台查看 `INTRODUCTION` 正文。
- 预期结果：affected rows = 0；正文中 `unsubscribe here:` 只出现一次。
- 覆盖：I-2

### A-6：应用能在生产配置下正常启动（部署闸门）

- 前置条件：预发环境使用与生产同构的配置（不额外设置任何 flyway 属性）。
- 操作步骤：
  1. 部署 WAR 到预发 Tomcat，观察启动日志。
  2. 搜索日志中的 `flyway` 与 `placeholder`。
- 预期结果：启动成功；日志出现 V87 迁移成功记录；**不出现** `No value provided for placeholder expressions`；不出现任何 `FlywayException`。
- 覆盖：需求描述 observable outcome 2；I-3；IP-3

### A-7：退订链接可点开并完成退订闭环

- 前置条件：A-1 已完成，手上有一封真实收到的介绍邮件。
- 操作步骤：
  1. 复制正文里的退订链接（不要直接点，先看清域名是你自己配置的 baseUrl）。
  2. 浏览器打开该链接。
  3. 点击页面上的 `Unsubscribe` 按钮。
  4. 后台 → 抑制名单，搜索该收件邮箱。
- 预期结果：
  - 第 2 步出现极简确认页，含文案 `Confirm that you want to unsubscribe from future emails.` 与一个 `Unsubscribe` 按钮（GET 不直接退订）。
  - 第 3 步页面显示 `You have been unsubscribed.`。
  - 第 4 步抑制名单中出现该邮箱，来源为 `ONE_CLICK`。
- 覆盖：需求描述 observable outcome 1 的端到端可用性
- 备注：若第 3 步返回 404，说明命中了 `UnsubscribeController.kt:49` 硬编码根路径与 context path 的冲突 —— 这是 **Plan 05 的已知项**，不算本计划的缺陷，但须在验收记录中标注实际部署是否带 context path。

> 人工验收开始时，从本节导出 `docs/plans/2026-08-11/unsubscribe-01-body-link-acceptance.md`（A-n 逐条 + 勾选框 + 验收人 + 日期 + 结果/备注）。清单本身有误时先改本节再重新导出。
