# p2 执行报告 · 运营可见性（列表按模板门禁筛选）

- Agent: `P2Implementer`
- Plan: `docs/plans/2026-08-09/personalization-gate-p2-operator-visibility.md` (sha256 `611523e002ea2c4bb579b6c4fc2cc5e451fd04f81a046c982c0ab4f8a4a49ef6`, 未变更)
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/personalization-gate` @ `fast/personalization-gate`
- Child base: `07a77f3e15da0d56317ec413412a5ca15ece913b`（p1 终态）；HEAD 前置 evidence 提交 `4488b8642a374d6fd8a2be6310b8a825d8a5d226`
- Implementation commit: `d848b8c3999fd7d67388be6d7b340ab48db43ff2` — `feat(fast-p): implement p2`（8 files, +672 −3；未含 docs/plans/fast/** 与 docs/plans/2026-08-09/**）
- 提交后 HEAD 即本提交，为分支 tip，未 push/merge/rebase/amend。

## 命令结果（全部在 JDK 11 下新执行）

| 命令 | Exit | 结果 |
|---|---|---|
| `JAVA_HOME=... mvn test -Dtest='ExpertSearchServiceTest,ComposeTemplateGateControllerTest'` | 0 | ExpertSearchServiceTest: `Tests run: 38, Failures: 0, Errors: 0, Skipped: 0`；ComposeTemplateGateControllerTest: `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0` |
| `node --test src/test/js/gateTemplateFilter.test.js` | 0 | 4 pass, 0 fail |
| `JAVA_HOME=... mvn test` | 0 | `Tests run: 2231, Failures: 0, Errors: 0, Skipped: 4`（基线 2223 → +5 服务测试 +3 控制器测试；Node 全量并入：478 pass, 0 fail，基线 474） |
| `JAVA_HOME=... mvn clean package` | 0 | `BUILD SUCCESS`，`Tests run: 2231, Failures: 0, Errors: 0, Skipped: 4` |
| `git diff --check` | 0 | 空（无空白/换行问题） |

开发期迭代记录（最终态均通过）：JS 提取正则（`\n\s*\}` 缩进）修正一次；服务测试断言谓词修正一次（`exists` 内层为 `{field=…}`，原用 `containsKey("institution")` 恒假 → 改为 `exists["field"] == "institution"`）；app.js 曾误插入重复 action 块已完全回退（最终 diff 纯新增 +163 行）。

## 变更文件（恰好 8 个授权文件）

生产 5：`expert/service/ExpertSearchService.kt`、`template/controller/MailComposeTemplateController.kt`、`static/index.html`、`static/styles.css`、`static/app.js`
测试 3：`expert/service/ExpertSearchServiceTest.kt`（+5 用例）、`template/controller/ComposeTemplateGateControllerTest.kt`（新建，3 用例）、`src/test/js/gateTemplateFilter.test.js`（新建，4 用例）

`git status` 确认无其他文件被改；P1 交付文件（mail/**、campaign/**、`MailComposeTemplate.kt`、`MailComposeTemplateService.kt`、migrations、`ExpertDiscoveryService.kt`、`.tag-chip` 三条规则、既有五个 chip button）均未触碰。

## 不变量证据

- **I-9（计数近似且只可高估）**：`ExpertSearchServiceTest` 新增用例断言 `researchFields` 过滤器 JSON 同时含 `exists` 与 `must_not/term/""`，`institution` 只含裸 `exists`（列表 `buildExpertFilters` 与计数 `buildFieldPresenceFilters` SATISFY_ALL 共用同一 `fieldPresenceFilter`，口径一致）。前端计数文案为 `符合 N / M`；grep `index.html`/`app.js` 无「可发送」「可发」字样。
- **I-10（前端不持有必填集）**：新增端点返回 `requiredKeys`/`esFields`，全部来自 P1 的 `effectiveRequiredKeys`/`requiredEsFields`（控制器测试用 Mockito 验证逐字透传，`esFields` 为空时也不臆造字段）。app.js 门禁代码无 `${...}` 占位符解析、无硬编码字段数组（JS 测试断言 gate 源码不含 `recentWorkTitles`/`researchFields` 字面量）；`gateTemplateFilter.test.js` 断言 gate-fields 500 时无任何 `hasField` 参数、无 chip 被勾选、`#expertGateSummary` 保持 hidden、仅一次失败提示；下拉选项加载失败仅 console 记录，无回退集合（下次聚焦重试）。
- **I-11（白名单容纳门禁字段）**：`ALLOWED_HAS_FIELDS` 增加 `recentWorkTitles`（测试断言包含），`require(field in ALLOWED_HAS_FIELDS)` 原样保留，未知字段仍抛 `IllegalArgumentException`（测试断言）。
- **I-12（空串排除只施加于 keyword 字段）**：`BLANK_EXCLUDABLE_FIELDS = {researchFields, recentWorkTitles, patentTitles, degree, country}`，测试断言恰好等于该集合，与 `orcid_info_candidate.json` 中 keyword 类型可筛字段一致；`text` 字段（institution 等）只用 `exists`。
- **S-1**：`index.html` diff 新增行逐字等于契约 button 片段（`data-value="recentWorkTitles"` `有近期论文`，位于 researchFields 与 patentTitles 之间，缩进与相邻行一致）；`styles.css` `.tag-chip` 三条规则零改动。
- **S-2**：`styles.css` 新增的四条 `.gate-filter-summary` 规则与契约代码块逐字一致（含属性顺序），位置在 `.tag-chip.active` 之后、`/* Back to list button */` 之前；`index.html` 门禁块与契约骨架一致；全 diff 无 inline style、无契约外新增 class。
- **S-3**：计数文案为 `符合 <span…match>0</span> / <span…total>0</span>` 结构；「不限」时 `#expertGateSummary` 保持 hidden，选中模板且计数返回后移除 hidden 并加 `has-value`（JS 测试覆盖「不限 恢复快照并隐藏」）。

## 实现要点（任务 1..5）

1. `ExpertSearchService.kt`：`ALLOWED_HAS_FIELDS += recentWorkTitles`；新增 `BLANK_EXCLUDABLE_FIELDS` 与私有 `fieldPresenceFilter(field)`；`buildExpertFilters` hasField 分支与 `buildFieldPresenceFilters` SATISFY_ALL 分支改用之；MISSING_ANY 未动。
2. `MailComposeTemplateController.kt`：`GET /api/compose-templates/{id}/gate-fields` → `{templateId, requiredKeys, esFields}`，纯透传 P1 接口，无推导。
3. `index.html`：S-1 chip 行 + S-2 门禁 toolbar-label 块逐字插入。
4. `styles.css`：S-2 四条规则逐字追加。
5. `app.js`：门禁逻辑（顶层函数 `initExpertGateFilter(reloadContactsFromStart)`，由 `bindEvents` 注册）；选项由顶层 `populateExpertGateTemplateFilter()` 填充；选中模板 → gate-fields → esFields 逐项应用到 chip（沿用 `initHasFieldTags` 的 `.active`/shim 机制，应用前快照手动勾选，切回「不限」恢复）；计数复用 `/api/experts` totalHits（带 hasField 查询=符合，不带 hasField 查询=总数，未新增计数端点）。

## 偏差

1. **下拉填充时机**：计划任务 5.1 为「页面加载时填充」，实现改为「首次聚焦时填充」（`select` focus 事件触发顶层 `populateExpertGateTemplateFilter()`，一次性、失败可重试、无硬编码回退）。原因：仓库既有预认证安全扫描（`authFlow.test.js` 的 "pre-auth init safety scan"）禁止 `bootstrap` 阶段（`bindEvents`，先于 `checkAuth`）出现任何网络调用（含词法 `api(`/`fetch(`）；将门禁整体移出 `bindEvents` 到顶层函数亦为同一约束（`refreshGateSummary`/`handleExpertGateChange` 含 `api(`）。操作员侧可观察行为等价：下拉在可选择前即已填充。所有 JS 测试（含 authFlow 19/19）通过。
2. 无其他偏差；未实现 master「Out of scope」项与 P1 后续阶段。

---
Epoch 1 证据补录（控制器）：本子计划无修复轮次（fix_round=0），fix-log.md 与 execution.md/verify-log.md 在同一证据提交中记录。
