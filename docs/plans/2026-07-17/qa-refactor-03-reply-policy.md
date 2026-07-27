# QA 重构 03：单一回复策略

## 需求描述

新增 `replyPolicy=AUTO/REVIEW/NEVER`，取代运营侧的 `autoReplyEnabled + handoffRequired` 两个开关。可观察结果：QA 列表与编辑弹窗只展示一个策略；旧自动回复在 grounded 切换前仍通过派生影子字段保持原行为。

必须不变：

- 27 条旧自动规则仍映射 AUTO，2 条旧转人工规则映射 REVIEW。
- 旧运行时读取 `auto_reply_enabled/handoff_required` 时得到与 policy 一致的结果。
- `enabled=false` 仍表示规则完全不参与匹配；它不等于 NEVER。

Out of scope：切换到 answerBody；删除旧布尔列；更改自动外发生成方式；创建 TrustProfile。

## 关键不变量

### Invariant I-1：policy 唯一权威
- Rule：V80 后 `reply_policy` 是业务权威；所有 runtime create/update 先校验 policy，再由同一映射函数派生旧布尔影子。禁止根据两个布尔反推并覆盖已有 policy。
- Applies to：V80、QaRuleManagementService、QaMatchService。
- Violation consequence：出现 `AUTO + handoff=true` 等矛盾组合，不同路径决策漂移。
- 来源：original。

### Invariant I-2：固定兼容映射
- Rule：`AUTO -> (auto=1,handoff=0)`；`REVIEW -> (0,1)`；`NEVER -> (0,1)`。迁移回填优先级：`handoff=1 OR auto=0 => REVIEW`，其余 AUTO；V80 不自动生成 NEVER。
- Applies to：V80、create/update、测试 fixture。
- Violation consequence：存量规则行为扩大或静默自动发送。
- 来源：original。

### Invariant I-3：NEVER 不可进入候选事实
- Rule：enabled 的 NEVER 可在后台查看/编辑，但 `suggestComposition/matchAllRuleIds/match` 必须过滤；仅命中 NEVER 等价于无可用 QA 事实。
- Applies to：QaMatchService 三个入口、工作台 suggestion API 的后续消费者。
- Violation consequence：内部/过期事实进入 prompt 或外发。
- 来源：original。

### Invariant I-4：多规则取最严格策略
- Rule：候选中全部 AUTO 才返回聚合 AUTO；出现任一 REVIEW 则聚合 REVIEW；NEVER 已在候选前过滤。旧 `autoReplyEnabled/handoffRequired` response 由聚合 policy 派生。
- Applies to：QaMatchResult、自动预览、旧 AutoMailReplyService。
- Violation consequence：一个需审核事实被其他 AUTO 事实掩盖并自动发送。
- 来源：original。

### Invariant I-5：enabled 与 policy 正交
- Rule：disabled 规则不匹配；enabled+NEVER 只供内部保留；enabled+REVIEW 可供人工草稿；enabled+AUTO 才有自动发送资格。
- Applies to：管理 service、repository reader、QaMatchService。
- Violation consequence：无法区分归档、人工事实与自动事实。
- 来源：original。

## 样式契约

### S-1：policy 表单控件
- 复用：`form.form-grid`、label/select 的完整样式 `styles.css:803-849`；不新增 class。
- 新增：无 CSS。
- DOM 结构：在 priority 后、answerBody 前加入：

```html
<label>回复策略
  <select name="replyPolicy" required>
    <option value="AUTO">AUTO（可自动回复）</option>
    <option value="REVIEW">REVIEW（仅人工草稿）</option>
    <option value="NEVER">NEVER（禁止外发）</option>
  </select>
</label>
```

- 禁止项：恢复两个 checkbox；用中文 label 值代替稳定 enum；新增 inline style/CSS。

### S-2：policy 状态 badge
- 复用：AUTO=`.badge.ok` (`styles.css:766-770`)；REVIEW=`.badge.warn` (`772-776`)；NEVER=`.badge.error` (`778-782`)。
- 新增：无 CSS。
- DOM 结构：QA 表格“回复策略”单元格只含一个 badge；enabled 在相邻“状态”列显示。
- 禁止项：组合多个互相冲突 badge；自行定义颜色。

## 现状审计

### `qa_rule`
- Schema/mapping：子计划 02 后新增 `answer_body`，旧 `auto_reply_enabled/handoff_required` 仍 NOT NULL；尚无 policy。
- Write paths：
  1. V80 新增/回填 policy。
  2. `QaRuleManagementService.create/update` — 本计划改为写 policy + 派生影子。
  3. `setRuleEnabled` — copy enabled，必须自然保留 policy/影子。
  4. 历史 Flyway 不得修改；后续迁移若改旧布尔不得作为业务权威。
- Read paths：
  1. QA 管理 API/UI。
  2. `QaMatchService.match` 当前聚合旧布尔；`suggestComposition/matchAllRuleIds` 当前不看旧布尔。
  3. `AutoMailReplyService/AutoReplyPreviewService` 通过 `QaMatchResult` 决策。
  4. `AiReplyDraftService` 当前不读策略；子计划 04 接入。
- Interaction points：管理写 policy/shadow → QaMatchService 读；match 聚合 → auto/preview 读；旧应用写布尔与新应用写 policy 的滚动窗口。

### QA 管理 API/UI
- 子计划 02 后 request/response 已有 answerBody，旧布尔只读返回，UI 显示旧路由 badge但不可编辑。
- 本计划新增 replyPolicy request/response，列表/弹窗切换为唯一策略。

### 前端样式盘点
- 可复用 class、token、DOM 见子计划 02 S-1/S-2；本计划只增加一个普通 select并替换一个 badge 数据源。
- 改动前基线：表格显示由旧布尔推导的“当前旧路由”，modal 无可编辑策略。

## 实现方案

### T1：V80 policy 扩展与回填
- 文件：`src/main/resources/db/migration/V80__add_qa_reply_policy.sql`
- 新增 `reply_policy VARCHAR(16) NOT NULL DEFAULT 'REVIEW'`。
- 单次 UPDATE 按 I-2 回填；再显式同步全部旧布尔为映射值，清除潜在异常组合。
- 不改 answer/reply body、enabled、ID、时间戳；上线前导出 policy pair count 与当前 27/2 基线比对。遵守 I-1、I-2。

### T2：domain enum 与映射
- 文件：`src/main/kotlin/com/weibo/talentintroduction/qa/domain/QaRule.kt`
- 在同文件定义 `enum class QaReplyPolicy { AUTO, REVIEW, NEVER }` 及 parse/legacy-shadow helper，避免多一个共享文件。
- `QaRule.replyPolicy` 延续项目现有 domain 模式存 `String`，默认 `REVIEW`；业务入口统一通过 `QaReplyPolicy.fromName()` 校验，写库统一使用 enum `.name`。repository round-trip 必须断言三个固定字符串。遵守 I-1、I-2、I-5。

### T3：管理 service/API 切换
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt`
  - `src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt`
- create/update command 只接收 replyPolicy；旧布尔 request 字段保留 nullable 兼容但忽略。
- service 用唯一 helper 同时写 policy 与 shadow；update 缺 policy 返回 400，不从旧布尔猜测。
- response policy 为 enum name；deprecated 两个布尔按 policy 动态派生，若数据库影子不一致则测试/日志暴露，不把矛盾返回前端。遵守 I-1、I-2。

### T4：匹配层接入 policy
- 文件：`src/main/kotlin/com/weibo/talentintroduction/qa/service/QaMatchService.kt`
- 读取 enabled rules 后立即过滤 NEVER；rulesByCategory、suggestedRules、GapItem candidates 同源过滤。
- `SuggestQaRule` 增加 replyPolicy；`QaMatchResult` 增加聚合 replyPolicy，并由 I-4 派生旧字段。
- disabled/NEVER-only 结果按 I-3/I-5 处理。不得改 replyBody、variant、supersede、gap 旧语义；这些由后续子计划替换。

### T5：管理 UI
- 文件：
  - `src/main/resources/static/index.html`
  - `src/main/resources/static/app.js`
- 按 S-1 增加 select；fill/save round-trip；表格按 S-2 显示。
- 删除旧路由布尔推导逻辑；不得改回复片段 variant UI。
- 遵守 I-1、I-5、S-1、S-2。

### T6：测试
- 文件：
  - `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementServiceTest.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaMatchServiceTest.kt`
  - `src/test/js/qaFactCardEditor.test.js`
- 覆盖三种 policy、非法/缺失值、shadow 映射、NEVER 过滤、多规则最严格、disabled 正交、UI enum round-trip。
- 测试必须逐项断言 I-1 至 I-5、S-1、S-2。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V80__add_qa_reply_policy.sql` | 新增并回填 policy |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/qa/domain/QaRule.kt` | enum、字段、兼容映射 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt` | policy 写路径 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt` | policy API |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaMatchService.kt` | policy 过滤与聚合 |
| 6 | `src/main/resources/static/index.html` | policy select/表头 |
| 7 | `src/main/resources/static/app.js` | policy fill/save/render |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementServiceTest.kt` | 写路径测试 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaMatchServiceTest.kt` | 匹配测试 |
| 10 | `src/test/js/qaFactCardEditor.test.js` | UI 契约测试 |

共 10 个文件、2 个子系统（QA backend + QA admin frontend），符合限制。

## 验收标准

- I-1：任意 runtime create/update 后 `reply_policy` 等于请求 policy；不存在从旧布尔反向覆盖 policy 的代码路径。
- I-2：迁移后数据库为 27 AUTO、2 REVIEW、0 NEVER；AUTO/REVIEW/NEVER fixture 的两个影子列逐列符合固定映射。
- I-3：NEVER 不出现在 suggest IDs、category rules、gap candidates、matchAll IDs、match result；管理列表仍可见。
- I-4：AUTO+REVIEW 聚合 REVIEW，旧 response 为 `autoReplyEnabled=false/handoffRequired=true`。
- I-5：disabled AUTO 不命中；enabled NEVER 不命中；enabled REVIEW 可建议但不可自动。
- S-1：`styles.css` diff 为空；select 只有三个固定值；表单无旧双 checkbox、inline style 或新 class。
- S-2：AUTO/REVIEW/NEVER 分别只渲染 `.badge.ok/.badge.warn/.badge.error`；单元格无组合 badge。
- 集成：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=QaRuleManagementServiceTest,QaMatchServiceTest,AutoMailReplyServiceTest,AutoReplyPreviewServiceTest test
node --test src/test/js/qaFactCardEditor.test.js
```

## 人工验收清单

### A-1：存量 policy 回填
- 前置条件：记录发布前旧开关分布 27 条自动、2 条转人工。
- 操作步骤：执行 V80，打开 QA 列表。
- 预期结果：显示 27 个 AUTO badge、2 个 REVIEW badge、0 个 NEVER；enabled 数仍为 28。
- 覆盖：I-2、must-NOT-change。

### A-2：策略 round trip
- 前置条件：选择一条无历史发送影响的测试规则。
- 操作步骤：依次保存 AUTO、REVIEW、NEVER并刷新。
- 预期结果：每次唯一 select/badge 与保存值一致；数据库影子依次为 `1/0`、`0/1`、`0/1`。
- 覆盖：I-1、S-1、S-2。

### A-3：NEVER 不进入工作台
- 前置条件：将能命中测试来信的唯一规则设为 NEVER 且 enabled=true。
- 操作步骤：打开该来信的旧组装建议和自动预览。
- 预期结果：建议列表候选不含该规则；自动预览显示 QA_NO_MATCH/转人工，不生成正文。
- 覆盖：I-3、I-5、写→读 interaction point。

### A-4：最严格策略
- 前置条件：一封来信同时命中一条 AUTO、一条 REVIEW。
- 操作步骤：查看自动预览。
- 预期结果：不自动回复，原因进入人工路径；两条事实仍可在人工建议中看到。
- 覆盖：I-4。

### A-5：旧运行时兼容
- 前置条件：新数据库 schema + 尚未切 grounded 的应用版本。
- 操作步骤：分别预览 AUTO 与 REVIEW 规则。
- 预期结果：AUTO 保持旧自动预览；REVIEW 保持转人工；无异常 enum/列映射错误。
- 覆盖：must-NOT-change、I-2。
