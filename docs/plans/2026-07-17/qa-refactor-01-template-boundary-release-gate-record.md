# QA 重构 01 — V78 发布门禁执行记录

- 子计划：`docs/plans/2026-07-17/qa-refactor-01-template-boundary.md` T1/T2
- 修复：`docs/plans/fix/qa-refactor-01-template-boundary/fix-1.md` P1-1
- 门禁脚本：`docs/plans/2026-07-17/qa-refactor-01-template-boundary-release-gate.sh`

## 执行方式

```bash
chmod +x docs/plans/2026-07-17/qa-refactor-01-template-boundary-release-gate.sh

# V78 应用前（Flyway 尚未写入 V78 或应用重启前）
DB_HOST=... DB_USER=... DB_PASSWORD=... \
  docs/plans/2026-07-17/qa-refactor-01-template-boundary-release-gate.sh pre \
  | tee /tmp/qa-refactor-01-gate-pre.log

# 部署含 V78 的版本并确认 Flyway 成功后
DB_HOST=... DB_USER=... DB_PASSWORD=... \
  docs/plans/2026-07-17/qa-refactor-01-template-boundary-release-gate.sh post \
  | tee /tmp/qa-refactor-01-gate-post.log
```

任一阶段脚本 exit 1 → **停止发布**；post 失败 → 回滚应用版本，不得进入 Phase 2。

## 门禁断言（I-2）

| 阶段 | 指标 | 允许值 |
|---|---|---|
| pre | `content_variant WHERE owner_type='QA_RULE'` | 0 |
| pre | `mail_compose_template_block WHERE block_type='QA_RULE'` | 1 |
| pre | 悬空 `QA_RULE` 引用（`ref_id` 无对应 `qa_rule`） | 0 |
| post | `mail_compose_template_block WHERE block_type='QA_RULE'` | 0 |

> 生产基线（2026-07-17）：28 条 QA 启用、QA 变体 0、`INTRODUCTION` 含 1 个有效 `QA_RULE` 块。若 pre 阶段 `qa_rule_blocks≠1`，说明目标库与基线不一致，须人工核对后再决定是否继续。

## 复验记录（fix-1 关闭 P1-1）

| 项 | 时间 (UTC) | 结果 |
|---|---|---|
| 门禁脚本与发布单入库 | 2026-07-17 | ✅ 本文件 + `qa-refactor-01-template-boundary-release-gate.sh` |
| Kotlin 测试 | 2026-07-17 | ✅ `MailComposeTemplateServiceTest` + `IntroductionMailComposerTest` — 38 passed |
| JS 测试 | 2026-07-17 | ✅ `composeTemplatePreview.test.js` — 7 passed |
| pre SQL 四项（目标库实跑） | 2026-07-17T14:52:10Z | ✅ 目标机实跑，`0 / 1 / 0`，脚本 exit 0 |
| post SQL（目标库实跑） | BLOCKED | ⛔ 当前制品最高 V77，且线上 `SPRING_FLYWAY_ENABLED=false`；等待已授权的 V78 部署方案 |

### 发布窗口 — pre 实跑输出（粘贴区）

```
=== qa-refactor-01 release gate (pre) @ 2026-07-17T14:52:10Z ===
database: 127.0.0.1:3306/talent_introduction
qa_rule_variants=0
qa_rule_blocks=1
dangling_qa_rule_refs=0
expected: qa_rule_variants=0 qa_rule_blocks=1 dangling_qa_rule_refs=0
PASS: pre-deploy gate
```

- 目标机：`VM-4-16-centos`（SSH `150.158.92.103`）
- 执行方式：将仓库门禁脚本通过 SSH stdin 原样交给远端 Bash，使用 Tomcat 进程现有 DB 环境变量；未写远端文件，未修改数据库。
- 当前线上制品：`/opt/apache-tomcat-9.0.71/webapps/talent.war`
- 制品 SHA-256：`ee7311b86c76dd8ec893710b738aac96622d72609a101e90262204bf79f86226`
- 制品版本：`1.0.0-SNAPSHOT`；WAR 内最高 migration 为 V77。
- 迁移状态佐证：目标库无 `flyway_schema_history`；`qa_rule` 有 V76 的 `coverage_keys`，无 V79/V80 的 `answer_body/reply_policy`；模板仍有 1 个 `QA_RULE` 块。线上环境明确设置 `SPRING_FLYWAY_ENABLED=false`。
- 执行人：Codex（root SSH，只读门禁）。

### 发布窗口 — post 实跑输出（粘贴区）

```
BLOCKED: 尚未部署 V78，禁止提前执行或伪造 post 输出。
线上运行环境设置 SPRING_FLYWAY_ENABLED=false，且数据库无 flyway_schema_history 表；
必须先明确并授权 V78 的实际迁移执行方式，再部署、执行 post 门禁并替换本段。
```

## 复验测试命令

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn -q -Dtest=MailComposeTemplateServiceTest,IntroductionMailComposerTest test

node --test src/test/js/composeTemplatePreview.test.js
```

## 与 V78 的关系

- V78 仍仅做 `INNER JOIN` 原位快照；**不在 SQL 内嵌断言**（保持迁移幂等与 Flyway 惯例）。
- 解耦完整性由 **pre/post 门禁脚本 + 本执行记录** 在发布窗口追溯；悬空引用导致 V78 跳过块时，pre 阶段 `dangling_qa_rule_refs≠0` 或 post 阶段 `qa_rule_blocks≠0` 会 fail closed。
