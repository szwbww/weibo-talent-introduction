## Execution Result: READY_FOR_VERIFICATION

- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-mail-preview/docs/plans/2026-08-14/expert-mail-preview-p2-detail-tab.md`
- Plan SHA-256: `1e6257ca205734d7dc5f0329427b6712c56c4f6088ea98a17351a8035fdb2089`
- Execution ID: `.../docs/plans/2026-08-14/expert-mail-preview-p2-detail-tab.md@1e6257ca205734d7dc5f0329427b6712c56c4f6088ea98a17351a8035fdb2089`
- Execution epoch: NEW
- Approval basis: current invocation — approved master plan `expert-mail-preview-main.md` + binding child plan (controller-assigned child p2-detail-tab)
- Executor: `P2Implementer`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-mail-preview`
- Target branch: `fast/expert-mail-preview`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/fast/expert-mail-preview@fast/expert-mail-preview@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/expert-mail-preview`
- Pre-execution code SHA: `6ba9a663eab5f71bba8354b1c6c53fb62318d01d` (p1 evidence commit head)
- Post-execution code SHA: `c2acd4fc1c2b1ec4d40a08db53b31bb44b28b77a`
- Evidence HEAD: N/A — executor makes no separate evidence commit; `docs/plans/fast/**`（含本报告与 ledger.md 修改）由 controller 另行提交
- Implementation boundary: `6ba9a66..c2acd4f`

### Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| A-1 tabs 数组 +1（S-1 逐字） | IMPLEMENTED | app.js | `renderDetailSubTabs` tabs 数组追加 `{ key: "mail-preview", label: "邮件预览" }`；既有三项未动 |
| A-2/A-3 两套面板 DOM（S-2 逐字） | IMPLEMENTED | app.js | `showExpertDetail`（app.js:6734-6736）与 `loadContactDetail`（app.js:7194-7196）各插入同一份 `.detail-tab-panel[data-panel="mail-preview"]` |
| A-4 懒加载分支 | IMPLEMENTED | app.js | `activateDetailSubTab` 既有 `template` 分支后新增 `mail-preview` 平行分支（app.js:6578-6583）；既有分支逐字不动 |
| B-1 `ensureComposeTemplatesLoaded`（I-2） | IMPLEMENTED | app.js | 定义 app.js:8025；被 `loadExpertMailPreview`（:8040）与 `openTemplateEditorForExpert`（:8116）各调用一次（M-4 grep 回执见下） |
| B-2 `loadExpertMailPreview`（S-2/S-3 状态机） | IMPLEMENTED | app.js | 无 ORCID 态 / 加载态 / 工具条（默认选中首个 enabled 模板）/ S-4 空壳 / 首次预览 / catch→错误态并重置 `dataset.loaded` |
| B-3 `renderExpertMailPreview`（I-1/I-6/I-7 + 序号守卫） | IMPLEMENTED | app.js | payload 逐字按计划；`strictPlaceholders: false` 字面量（app.js:8079）；显式 4 字段 blocks 映射；`expertMailPreviewRequestId` 独立计数器 |
| B-4 事件接入 | IMPLEMENTED | app.js | `#contactDetail` click 委托在 `[data-sub-tab]` 之后、`button[data-action]` 之前新增 `[data-role="mail-preview-open-editor"]` 分派（app.js:11008-11015）；新增 `change` 委托仅处理 `[data-role="mail-preview-template"]`（app.js:11019-11026） |
| C-1 `openTemplateEditorForExpert`（I-3/I-4） | IMPLEMENTED | app.js | 严格顺序：ensure 加载 → 模板查找（缺失 showStatus 并 return，不 setView）→ setView → switchMailTemplatesSubTab → openComposeTemplateEditor → await loadComposeTemplatePreviewOptions → 双写 `#previewComposeExpertInput.value` + `state.previewDrawer.*` → await openComposeTemplatePreview |
| D-1 缓存键三处 bump（M-1） | IMPLEMENTED | index.html | 三处 `?v=` 均改为 `20260814-v10-expert-mail-preview-01`（`grep -o 'v=[^"]*' | sort -u` 输出唯一值；与 P1 的 v9 不同） |
| D-2 缓存键断言同步（M-1） | IMPLEMENTED | batchSendTaskConsoleVisualFix.test.js | :37-39 三条断言改为新值（grep -c = 3） |
| D-3 新测试 8 组断言 | IMPLEMENTED | expertMailPreviewTab.test.js（新） | `node --test` 8/8 pass |
| Phase 6 知识回写 | IMPLEMENTED | K-mail-body-display-sites.md、CLAUDE.md | 新增第⑥处 `class="pre"` 正文展示点；`last_used: 2026-08-14`、`hit_count: 23`；CLAUDE.md「团队沉淀知识」一行摘要同步 |
| 后端零改动 | IMPLEMENTED | — | `git diff --stat src/main/kotlin` 为空 |

### 验收不变量核对（含 M-4 grep 回执）

```
$ grep -n "preview-draft" src/main/resources/static/app.js
8088:        const result = await api("/api/compose-templates/preview-draft", {   （既有 renderServerComposeTemplatePreview，未动）
8336:        const result = await api("/api/compose-templates/preview-draft", {   （新 renderExpertMailPreview）
$ grep -n 'compose-templates/\${.*}/preview\|/preview"' src/main/resources/static/app.js
（无命中 → I-1 无 GET /{id}/preview）
$ grep -n "ensureComposeTemplatesLoaded" src/main/resources/static/app.js
8025:async function ensureComposeTemplatesLoaded() {
8040:        await ensureComposeTemplatesLoaded();     （loadExpertMailPreview）
8116:    await ensureComposeTemplatesLoaded();        （openTemplateEditorForExpert）
$ grep -c 'data-panel="mail-preview"' src/main/resources/static/app.js
3
$ grep -c 'data-panel="template"' src/main/resources/static/app.js
3
（相等 ✓；3 = activateDetailSubTab 懒加载分支 1 + 两套面板 div 2。I-5 的「两套详情面板」= 2 个 panel div，
  expertMailPreviewTab.test.js 第 7 组断言 panelDivCount===2 且总数相等）
$ grep -n 'data-panel="mail-preview"' src/main/resources/static/app.js
6578:        const panel = detail.querySelector('[data-panel="mail-preview"]');
6734:            <div class="detail-tab-panel" data-panel="mail-preview" hidden>
7194:            <div class="detail-tab-panel" data-panel="mail-preview" hidden>
$ grep -n "strictPlaceholders" src/main/resources/static/app.js
8079:        strictPlaceholders: false,     （新函数内字面量 false，不读 checkbox）
8324:    const strictPlaceholders = $("#previewComposeStrictPlaceholders")?.checked === true;   （既有编辑器，未动）
8328:        strictPlaceholders,
（新函数体内无 getElementById / `$("#...` 除 `$("#previewComposeExpertInput")`（I-7 允许项）与 `#contactDetail` 委托绑定 → I-7 ✓）
$ grep -o 'v=[^"]*' src/main/resources/static/index.html | sort -u
v=20260814-v10-expert-mail-preview-01
$ grep -c '20260814-v10-expert-mail-preview-01' src/test/js/batchSendTaskConsoleVisualFix.test.js
3
$ git diff --stat src/main/kotlin
（空 → 后端零改动）
```

新函数体内无任何 `${...}` 手工替换逻辑（正文一律 `textContent` 渲染，无 `replace(/\$\{/...)`）→ I-1 完整成立。

### Commands

| Command | Result | Evidence |
|---|---|---|
| `node --test src/test/js/expertMailPreviewTab.test.js` | PASS | exit 0；tests 8 / pass 8 / fail 0 |
| `node --test src/test/js/batchSendTaskConsoleVisualFix.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js` | PASS | exit 0；tests 63 / pass 63 / fail 0 |
| `node --test src/test/js/composeTemplatePreview.test.js src/test/js/contactsLayoutDefault.test.js src/test/js/expertProfileAbsence.test.js src/test/js/expertTagBatchFix.test.js` | PASS | exit 0；tests 53 / pass 53 / fail 0 |
| `node --check src/main/resources/static/app.js` | PASS | exit 0，无输出 |
| `node --test src/test/js/*.test.js`（与 pom 绑定的全量前端套件同口径） | FAIL（预期基线） | exit 1；tests 518 / pass 516 / fail 2 —— 仅 `batchManualExecutionLog.test.js` 两例 `ReferenceError: buildManualExecutionSnapshot is not defined`（文件不在授权清单，基线与 P1 记录一致，已复现） |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` | FAIL（预期基线） | exit 1，失败仅在 exec-maven-plugin `node-test` 目标的上述 2 例既有 JS 失败；其余（含新增测试）均通过 |
| `JAVA_HOME=... mvn test -DskipNodeTests=true` | PASS | exit 0；`Tests run: 2418, Failures: 0, Errors: 0, Skipped: 4`；FlywayMigrationIntegrationTest `Skipped: 1`（未执行（无 Docker），基线记录一致） |
| `JAVA_HOME=... mvn clean package -DskipNodeTests=true` | PASS | exit 0；产出 `target/weibo-talent-introduction-1.0.0-SNAPSHOT.war`（46MB）；WAR 内静态资源已核对：index.html 含新缓存键 ×3、app.js 含 `data-panel="mail-preview"` ×3、styles.css 含 `expert-mail-preview-toolbar` ×2 |
| `git diff --check` | PASS | exit 0，无输出 |
| `git diff --stat src/main/kotlin` | PASS | 空（后端零改动） |

如实记录：`mvn test` / `mvn clean package` 不带 `-DskipNodeTests=true` 时 exit 1 **仅**由 2 例既有 `batchManualExecutionLog.test.js` 失败导致（非本 child 引入，基线记录）；WAR 仅在 `-DskipNodeTests=true` 下构建 —— 均为记录在案的基线行为，未声称 exit 0。

### Changed Files（提交 c2acd4f，共 7 个授权文件）

- `src/main/resources/static/app.js` — tabs 数组、两处面板 DOM、懒加载分支、4 个新函数、click/change 委托
- `src/main/resources/static/styles.css` — 「专家详情 · 邮件预览」段（S-3/S-4 逐字，位于 `.tpl-var-empty/.tpl-var-loading` 块与 `.preview-drawer-shell` 之间）
- `src/main/resources/static/index.html` — 仅缓存键三处 bump（M-1）
- `src/test/js/expertMailPreviewTab.test.js` — 新建，8 组断言
- `src/test/js/batchSendTaskConsoleVisualFix.test.js` — 缓存键三条断言同步（M-1）
- `docs/knowledge/mail/K-mail-body-display-sites.md` — Phase 6 回写（新增⑥正文展示点，bump last_used/hit_count）
- `CLAUDE.md` — Phase 6 回写（K-mail-body-display-sites 一行摘要同步）

### Deviations

1. **I-5 的 grep 计数值**：计划验收文字写「`grep -c 'data-panel="mail-preview"'` 输出 2」，实测为 **3**（`activateDetailSubTab` 懒加载分支的 `querySelector('[data-panel="mail-preview"]')` 1 处 + 两处面板 div 2 处），与 `data-panel="template"` 的计数值（3，同样含懒加载分支）**相等** —— 计划约束的等式不变量成立；「两套详情面板」= 2 个 panel div（`showExpertDetail` / `loadContactDetail` 各一），测试第 7 组断言 `panelDivCount === 2` 且总数相等。
2. **测试第 1 组计数方式**：`data-sub-tab="` 出现次数 = 4（而非 `class="detail-sub-tab`，后者会连 `.detail-sub-tabs` 容器一起匹配到 5）。
3. 缓存键新值 `20260814-v10-expert-mail-preview-01`（计划建议值，与 P1 的 v9 不同）→ M-1 满足。

### Freshness

- Plan identity rechecked: YES（SHA-256 前后一致 `1e6257ca…`）
- Worktree identity rechecked: YES（root@branch@git_dir 前后一致；提交后 HEAD=c2acd4f）
- Reported commits reachable from target branch: YES（`git merge-base --is-ancestor HEAD refs/heads/fast/expert-mail-preview` 通过）
- Required commands run this invocation: YES（全部在上述最终状态上新鲜执行）
- Historical evidence used only as baseline: YES（2 例 JS 失败与 Flyway 跳过均当场复现/确认，未当作通过证据）

### Remaining Blocker

- 无。`docs/plans/fast/**`（ledger.md 修改 + 本报告目录）按约定保持未提交，由 controller 另行提交证据。

### Next Action

- READY_FOR_VERIFICATION → 运行 `verify-p`（child p2-detail-tab）
