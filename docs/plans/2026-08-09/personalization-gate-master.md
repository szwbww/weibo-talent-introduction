# Master 计划：个性化字段门禁

- master-slug: `personalization-gate`
- 创建日期: 2026-08-09
- 状态: 待人工批准

## 子计划顺序（严格串行）

| 序号 | 子计划 | 路径 | 子系统 | 生产文件数 |
|---|---|---|---|---|
| 1 | P1 · 发送侧硬闸门 | `docs/plans/2026-08-09/personalization-gate-p1-send-gate.md` | 后端（模板/邮件/批量） | 10 |
| 2 | P2 · 运营可见性 | `docs/plans/2026-08-09/personalization-gate-p2-operator-visibility.md` | 查询 + 前端 | 5 |

**P2 依赖 P1**：P2 消费 P1 建立的 `mail_compose_template.required_keys` 列与 `MailComposeTemplateService.effectiveRequiredKeys()`。反向不成立——P1 单独上线即可独立交付价值（停止发出未替换占位符与套话邮件）。

**禁止合并执行。** 合并后生产文件数为 15，跨前后端两个以上子系统，违反单计划规模上限。

## 需求描述（master 级）

### Observable outcome

1. 外发邮件正文与主题中不再出现任何未被替换的 `${...}` 占位符。
2. 模板可声明「必填个性化变量」；任一必填变量在当前专家上取不到非空值时，该专家被跳过而不是使用默认值发出。
3. 批量执行详情的「跳过原因」中出现「个性化字段缺失」及其数量。
4. 专家列表可按某个模板的必填字段筛选，并显示「符合 / 总数」。

### What must NOT change

1. `IntroductionMailComposer.compose()` 现有的变量来源（`MailVariableService.buildVariables`）与其两个调用方的行为，除新增闸门外不变。
2. 现有 `hasField`「数据完整度」chip 的既有五个选项（`employment` / `degree` / `institution` / `researchFields` / `patentTitles`）语义与查询结果不变。
3. `researchFields` 在 ES 中的存储格式（`topics.joinToString(", ")`）不变；专家列表展示、AI 回复上下文等既有消费方不受影响。
4. `requireValidPlaceholders` 的现有五处调用方语义不变（`ContentVariantService`、`QaFactBodyPolicy`、`ReplySnippetService` ×2、`PendingMailOperationService`）。
5. 未配置 `required_keys` 的模板，发送行为与当前完全一致（除占位符残留拦截外）。

### Out of scope（显式推迟）

1. **块级条件渲染**（按变量有无跳过整个内容块）。`MailComposeTemplateService` 的块解析当前只有 `text.isNotBlank()` 与引用对象 `enabled` 两个纳入条件，没有按数据选择块的机制。新增该能力需要 `mail_compose_template_block.required_vars` 列 + 改块解析 + 前端块编辑器，列为 P3，本次不做。
2. 门禁命中后自动触发 OpenAlex enrichment 补数据。
3. 主题行的个性化改写（当前主题为字面量，不含占位符）。
4. `IntroductionMailComposer` 发出的是纯文本单部分邮件（未设 `html`/`text`），与 `ManualExpertMailService` 的 multipart 不一致。**这是一处已观察到的差异，本次不改**，记录供后续评估。
5. Postmaster `domains.getComplianceStatus` 接入。

## Master 关键不变量（跨子计划）

### I-M1: 占位符零残留
- Rule: 进入 SMTP 的 subject、plain text body、HTML body 中，均不得存在匹配 `\$\{[^}]*\}` 的子串。
- Applies to: `ManualExpertMailService.sendManualMail`、`IntroductionMailComposer.compose` 的产物。
- Violation consequence: 收件人看到 `${unsubscribeUrl}` 原文，可见退订入口失效，触发垃圾邮件判定。
- 来源: original（由 2026-08-09 实际外发样本证实）

### I-M2: 门禁判定的唯一权威在发送时
- Rule: 是否发送只由发送时刻针对当前服务端事实的判定决定。ES 预筛（P2）只用于减少候选集与展示计数，**不得**作为「已通过门禁」的证据被发送路径消费。
- Applies to: P1 的 `PersonalizationGateService`；P2 的 ES 查询与列表计数。
- Violation consequence: ES `exists` 不排除空字符串，且快照与发送之间数据可能被 enrichment 改写，预筛通过不代表实际非空，会漏发套话邮件。
- 来源: 同型约束 K-preview-mirrors-pipeline / K-ai-adopt-direct-send-no-residual-gates

### I-M3: 必填集合由服务端单源提供
- Rule: 「某模板的必填变量集」与「其对应 ES 字段集」只能由 `MailComposeTemplateService` / `MailPlaceholderService` 在服务端推导。前端不得解析占位符、不得内置默认必填集。
- Applies to: P1 的服务端推导；P2 的只读接口与前端。
- Violation consequence: 前后端默认值漂移，运营看到的筛选结果与实际发送口径不一致。
- 来源: 同型约束 K-prompt-config-effective-default

### I-M4: 新增占位符不改动 ES 存储
- Rule: `primaryResearchField` 是从既有 ES 字段 `researchFields` 派生的展示层变量。不得新增 ES 字段、不得改写 `ExpertDiscoveryService.updateExpertAcademicFields()` 的 doc map、不得回填。其 `ES_FIELD_BY_KEY` 值必须为 `"researchFields"`。
- Applies to: P1 的 `MailPlaceholderService` / `MailVariableService`；P2 的字段筛选映射。
- Violation consequence: 破坏 K-enrichment-write-three-layers 约定的单一写入点；且门禁与列表筛选会指向一个不存在的 ES 字段。
- 来源: K-enrichment-write-three-layers

### I-M5: 两条外发路径都必须过闸门
- Rule: 门禁必须同时覆盖 `ManualExpertMailService.sendManualMail`（手动发送 + MATERIAL_REMINDER 批量）与 `IntroductionMailComposer.compose`（INTRODUCTION 批量 + 定时首轮外发）。
- Applies to: P1。
- Violation consequence: 只改前者会让 INTRODUCTION 批量完全绕过门禁——而那正是主发送路径。
- 来源: original（`ManualInitialOutreachService.kt:304` 与 `:589` 为两条独立发送路径）

## 跨子计划接口（P1 → P2）

P2 只允许通过以下由 P1 建立的契约消费数据，**不得**自行推导必填集：

| 接口 | 由 P1 定义 | P2 的消费方式 |
|---|---|---|
| `mail_compose_template.required_keys` | V84 迁移新增，JSON 数组文本，NULL 表示未配置 | 不直接读表 |
| `MailComposeTemplateService.effectiveRequiredKeys(templateId): List<String>` | 返回该模板生效的必填**变量 key** 列表 | 供只读接口调用 |
| `MailComposeTemplateService.requiredEsFields(templateId): List<String>` | 把上者经 `ES_FIELD_BY_KEY` 映射成去重后的 **ES 字段**列表 | 供只读接口 + ES 筛选使用 |
| `MailPlaceholderService.ES_FIELD_BY_KEY["primaryResearchField"] == "researchFields"` | P1 新增 | P2 的 `ALLOWED_HAS_FIELDS` 必须能接受该映射结果 |

P2 若发现上述任一方法签名与本表不符，属 P1 交付缺陷，应停止并要求修订，不得在 P2 内自行补写推导逻辑。

## 验证命令

> 本项目必须使用 JDK 11（zulu-11）。裸 `mvn` 会因 JDK 版本不符导致构建失败。以下命令可原样复制执行。

```bash
# 全量测试（回归门禁）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 构建
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# P1 新增/受影响测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='PersonalizationGateServiceTest,ManualExpertMailServiceGateTest,MailVariableServiceTest,IntroductionMailComposerTest,MailComposeTemplateServiceTest'

# P2 新增/受影响测试类
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest='ExpertSearchServiceTest,ComposeTemplateGateControllerTest'

# 前端 JS 测试单文件（P2 快速迭代用）
node --test src/test/js/gateTemplateFilter.test.js

# 空白/换行卫生
git diff --check
```

> **注意**：`pom.xml:184-203` 通过 exec-maven-plugin 把 `node --test src/test/js/*.test.js` 绑定在 `test` 阶段（另有 `node-check-app` 执行）。因此上面的全量 `mvn test` **已经包含前端 JS 测试**，无需额外单独执行；单文件命令仅用于开发期快速迭代。若需跳过 Node 测试，项目提供 `-DskipNodeTests=true`。

通过判据：`mvn` 命令退出码 0，输出含 `Tests run: N, Failures: 0, Errors: 0` 且 Node 测试段无 `fail 1` 以上；`node --test` 单文件命令退出码 0；`git diff --check` 无输出。

来源：`CLAUDE.md` 项目元信息的 `test_command` / `build_command`；单测过滤语法取自 `CLAUDE.md`「Commands」章节的 `-Dtest=QaMatchServiceTest` 示例；Node 测试命令逐字取自 `pom.xml:199`。

> **子计划不得重写本节命令。** P1/P2 的验收标准中凡涉及「跑测试」「构建通过」，一律引用本节。

## 人工验收清单（master 级，跨子计划）

以下为 master 级黑盒验收项。子计划各自的 A-n 另见其自身文档；review-fast-p 的 `manual-acceptance.md` 应由本节 + 两份子计划的 A-n 合并导出。

### AM-1: 未替换占位符不再外发
- 前置条件: 存在一个启用的 compose 模板，正文含 `${unsubscribeUrl}`；存在一个有邮箱的专家联系人；`UNSUBSCRIBE_BASE_URL` 与 `UNSUBSCRIBE_SECRET` 均已配置为非空。
- 操作步骤:
  1. 在专家详情页选择该模板，执行「手动发送」。
  2. 到收件邮箱查看原始邮件源码。
- 预期结果: 邮件 text/plain 与 text/html 两部分中，均不含字符串 `${unsubscribeUrl}`；该处为一个以 `UNSUBSCRIBE_BASE_URL` 开头、含 `/u/unsubscribe?token=` 的完整 URL。
- 覆盖: I-M1、需求 observable outcome 1

### AM-2: 门禁按模板配置生效并跳过
- 前置条件: 模板 A 的必填变量配置为「研究方向、近期论文标题」；准备两个专家联系人——专家甲 ES 中 `researchFields` 与 `recentWorkTitles` 均非空，专家乙 `recentWorkTitles` 缺失。
- 操作步骤:
  1. 用模板 A 对甲、乙两人执行批量发送。
  2. 打开该批次的执行详情。
- 预期结果: 成功数为 1；「跳过原因」区域出现一行文案为「个性化字段缺失」、数量为 `1`；乙未收到邮件，甲收到的邮件正文含其真实研究方向与论文标题。
- 覆盖: I-M2、I-M5、需求 observable outcome 2 与 3

### AM-3: 未配置 required_keys 的模板行为不变（回归）
- 前置条件: 模板 B 的必填变量一项未勾选（`required_keys` 为 NULL）；专家丙的 `researchFields` 为空。
- 操作步骤: 用模板 B 对专家丙执行手动发送。
- 预期结果: 邮件正常发出，未被跳过；正文中该变量位置显示模板里写的默认值文案。
- 覆盖: must-NOT-change 第 5 项

### AM-4: 既有五个「数据完整度」选项行为不变（回归）
- 前置条件: 记录改动前，专家列表在仅勾选「有研究方向」时的总数 N。
- 操作步骤: P1 与 P2 均上线后，重复该筛选。
- 预期结果: 总数仍为 N（允许因期间 enrichment 导致的自然变化，需人工确认变化来源不是本次改动）。
- 覆盖: must-NOT-change 第 2 项

### AM-5: 列表筛选口径与实际发送口径一致
- 前置条件: 完成 AM-2 的数据准备。
- 操作步骤:
  1. 专家列表选择模板 A 的门禁筛选，记录「符合」数量。
  2. 用模板 A 发起批量发送，记录成功数 + 因个性化字段缺失跳过数。
- 预期结果: 列表「符合」数量 ≥ 实际成功数（允许 ES 近似导致的高估）；且被列表判为不符合的专家，不应出现在成功发送名单中。
- 覆盖: I-M2、I-M3、需求 observable outcome 4

### AM-6: 前端不持有必填集默认值
- 前置条件: 无。
- 操作步骤: 在浏览器中把模板门禁接口的响应改为空数组（可用开发者工具断点或临时禁用该接口）。
- 预期结果: 前端不再应用任何字段筛选，且不出现内置的字段默认勾选；不得表现出「接口失败时回退到某个硬编码字段集」的行为。
- 覆盖: I-M3

### AM-7: 人工签核
- 由验收人签署姓名与日期，明确接受被审查的 master identity 与最终 code SHA。

## 修正记录

（暂无。任何对本 master 或子计划要求的修正，须在此追加一行说明与依据。）
