# 计划 CV-6：旧变体字段清理（cv-6-legacy-cleanup）

> 系列：变体机制二次重构 6/6（contract 阶段）。**依赖 CV-1..CV-5 全部合入并通过人工验收**——本计划执行前，旧字段必须已无任何读写消费（CV-1 停读、CV-4 前端停发）。

## 需求描述

可观测结果：`mail_compose_template.subject_variants` 与 `reply_snippet.variant_group` 两列删除，对应 domain 字段、DTO 字段、命令字段全链清除；系统一切行为不变。

不得改变：
- 一切运行时行为——本计划是纯清理，任何可观测输出变化都是缺陷。
- 已应用迁移 V1..V67（新删列走 V68）。

超出范围：content_variant 相关一切（已是现役）；前端（CV-4/5 已清）；QaRule 结构。

## 关键不变量

### Invariant I-1: 死字段零消费前置检查
- Rule: 动手前 grep 验证 `subjectVariants`（main 源码）仅剩 domain 字段/Command/Detail/Request 声明与透传，`variantGroup` 仅剩 domain/Command/Request 声明与 create/update 落库——若发现任何逻辑消费（条件判断、渲染、查询），停止并回退到产生消费的计划修复。
- Applies to: 执行前检查步骤。
- Violation consequence: 删列后运行时报错。
- 来源: original（expand-contract 收尾纪律）

### Invariant I-2: 迁移不可逆声明
- Rule: V68 为 `ALTER TABLE ... DROP COLUMN`（两条）；执行即丢历史数据（用户决策：丢弃）；不写回滚脚本；不动 V64（该列的创建迁移保持原样）。
- Applies to: V68 迁移文件。
- Violation consequence: 编辑已应用迁移导致 Flyway 校验失败。
- 来源: CLAUDE.md 迁移纪律

### Invariant I-3: Spring Data JDBC 全字段映射
- Rule: 删列必须与删 domain 字段同计划原子完成——JDBC 映射所有构造器字段，列不存在而字段存在会在读写时报错；反之字段删而列在无害（列成孤儿也一并删）。
- Applies to: MailComposeTemplate.kt、ReplySnippet.kt 与 V68 同步。
- Violation consequence: 启动即报 SQL 异常。
- 来源: K-variant-pool-dto-chain（JDBC 映射特性）

## 现状审计

### 残留清单（CV-1..CV-5 合入后的预期态，执行时按 I-1 复核）
- `mail_compose_template.subject_variants`：写 create/update（值恒 null——前端已停发）；读 toDetail 透传。声明点：MailComposeTemplate.kt:13、MailComposeTemplateService Command/Detail、MailComposeTemplateController Request（:66/:77）。
- `reply_snippet.variant_group`：写 create/update（:63/:89）；无读。声明点：ReplySnippet.kt:16、ReplySnippetService 两 Command（:172/:180）、ReplySnippetController DTO（:63/:81）。
- 测试残留：两个 ServiceTest 中构造 domain 对象若带这两字段需同步删参。
- Interaction points: 无（前置条件即零消费）。

## 实现方案

### T1 — V68 迁移（I-2）
`V68__drop_legacy_variant_columns.sql`：
```sql
ALTER TABLE mail_compose_template DROP COLUMN subject_variants;
ALTER TABLE reply_snippet DROP COLUMN variant_group;
```

### T2 — 字段与 DTO 清除（I-1, I-3）
六个源文件按残留清单逐点删除字段/参数/透传；两个测试文件同步。

## 变更文件清单

| # | 文件 | 变更 |
|---|------|------|
| 1 | src/main/resources/db/migration/V68__drop_legacy_variant_columns.sql | T1 新建 |
| 2 | src/main/kotlin/com/weibo/talentintroduction/template/domain/MailComposeTemplate.kt | T2 |
| 3 | src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt | T2 |
| 4 | src/main/kotlin/com/weibo/talentintroduction/template/controller/MailComposeTemplateController.kt | T2 |
| 5 | src/main/kotlin/com/weibo/talentintroduction/reply/domain/ReplySnippet.kt | T2 |
| 6 | src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt | T2 |
| 7 | src/main/kotlin/com/weibo/talentintroduction/reply/controller/ReplySnippetController.kt | T2 |
| 8 | src/test/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateServiceTest.kt | T2 |
| 9 | src/test/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetServiceTest.kt | T2 |

文件数 9 ≤ 10；子系统 2（template、reply）。

## 验收标准

- I-1: 执行前 grep 记录附在 PR 描述；`subjectVariants|variantGroup|subject_variants|variant_group` 在 src/（含测试与前端）grep 零命中（迁移文件 V64/V68 除外）。
- I-2/I-3: 本地空库 Flyway 全量跑通；带 V67 旧库增量跑 V68 通过；应用启动无 JDBC 映射异常。
- `mvn clean package` 全绿。

## 人工验收清单

### A-1: 全链路行为回归（覆盖需求 + must-NOT-change 第 1 条）
- 前置条件: V68 已执行的环境；CV-1..5 功能在用（存在带变体的规则/片段）。
- 操作步骤: 1) 打开模板/片段/QA 编辑器各保存一次；2) 手工发送一封模板邮件；3) 触发一次自动回复；4) 人工回复勾选变体发送一次。
- 预期结果: 四步全部成功且输出与 CV-5 验收时一致；控制台与服务端日志无异常。

### A-2: 库表确认（覆盖需求）
- 前置条件: 同 A-1。
- 操作步骤: `SHOW COLUMNS FROM mail_compose_template LIKE 'subject_variants'; SHOW COLUMNS FROM reply_snippet LIKE 'variant_group';`
- 预期结果: 两查询均空；`flyway_schema_history` 末行为 V68 success。

### A-3: 旧客户端容错（覆盖 must-NOT-change 第 1 条）
- 前置条件: 同 A-1。
- 操作步骤: curl PUT 模板/片段，body 故意带 `"subjectVariants": "[\"x\"]"` 与 `"variantGroup": "g"`。
- 预期结果: 保存成功（未知字段被忽略），响应不含这两个字段，库中无痕。
