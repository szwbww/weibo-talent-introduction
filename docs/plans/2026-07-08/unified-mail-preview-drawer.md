# 统一邮件预览抽屉 + 占位符随机抽样

> 创建日期: 2026-07-08
> 前置依赖: `qa-reply-personalization-backend.md`、`qa-variable-config-ui-and-validation.md`（均已实装并验证）。
> 触发原因: 现有「专家变量渲染预览」modal（`index.html:1560 #varPreviewModal`）过于粗糙；预览能力分散。目标是一个全局右侧抽屉统一承载变量渲染预览，并支持"按占位符满足度随机抽专家"。

## 需求描述

**可观测结果**：
1. QA 规则编辑、回复片段编辑（所有带 `data-var-preview-target` 的预览按钮）点击后从窗口右侧滑出统一预览抽屉：上下文条（专家选择/随机抽样/发件账号）+ 仿真邮件卡片（To/Subject/渲染后正文，变量值绿色高亮、兜底值黄色高亮）+ 变量状态面板（绿=有值、黄=兜底、红=白名单外）+ 覆盖率行。
2. 「随机」按钮按模式抽专家：**全满足**（正文所有专家占位符对应字段均有值）或**有缺失**（至少一个字段为空），范围可选 CANDIDATE（L2）/ APPLICATION（L1）；同时显示"满足全部占位符 N / 总数 M"覆盖率。
3. 旧 `#varPreviewModal` 删除。

**不可变更**：
1. **预览无发送能力**——抽屉内不得出现"发送测试邮件"或任何触发外发的按钮（需求方明确排除）。
2. `POST /api/qa/render-preview` 对现有消费者向后兼容：`app.js:7391 refreshComposedRenderedPreview`（组装台内联预览）只读 `rendered`/`fallbackKeys`，这两个字段的名称与语义不变，扩展只做加法。
3. 前端不解析/替换 `${}`（K-composed-reply-order-contract 同源契约）；正文展示沿用 `escapeHtml` + pre-wrap（K-mail-body-display-sites）。
4. 预览按钮的 `data-var-preview-target` / `data-var-preview-contact-*` DOM 契约不变（入口只换弹出目标，不换取数方式）。
5. QA 保存校验、渲染发送 seam（K-qa-outbound-render-seams 三点）行为不变——本计划纯增读路径。

**不在范围**（显式延后）：
- 模板编辑器（mail_compose_template modal）内置预览面板迁移到抽屉——它有 blocks/variantSeed 语义，单独计划。
- 组装台内联预览迁移——保留现状，仅共享同一后端端点。
- 范围「待处理来信」（需 MySQL contact→ES orcidId 跨库 join，terms 截断策略单独设计）。
- `mode=MISSING_ANY` 之外的组合筛选（按国家/tag 过滤抽样）。

## 关键不变量

### Invariant I-1: 预览同源、纯只读
- Rule: 抽屉一切渲染结果来自 `POST /api/qa/render-preview`（内部 `MailVariableService.renderPreview`，与发送 seam 同实现）；新随机端点与 ES 查询无任何写操作、无 `@Transactional`、无 save/send。前端 JS 不得出现 `${` 的解析或替换逻辑（现有 `app.js:7402` 的 `includes("${")` 短路判断属于"是否需要调后端"的探测，可保留）。
- Applies to: 新端点、`app.js` 抽屉组件全部渲染路径。
- Violation consequence: 预览与实发漂移；或预览产生副作用。
- 来源: K-preview-mirrors-pipeline, K-composed-reply-order-contract

### Invariant I-2: render-preview 响应只做加法
- Rule: 响应新增 `variables: [{key,label,value,filled,usedFallback}]` 与 `invalidTokens: [string]` 字段；既有 `rendered`、`fallbackKeys` 字段名称、类型、语义不变。
- Applies to: `QaRuleManagementController.renderPreview`、`MailVariableService.renderPreview`。
- Violation consequence: 组装台内联预览（`app.js:7420-7432`）静默失效。
- 来源: original（消费者清单见现状审计）

### Invariant I-3: esField 映射单源，键提取在服务端
- Rule: 变量 key → ES 字段的映射作为 `VariableMeta.esField: String?` 进入 `variableMetadata()`（唯一定义点）。`esField = null` 表示不参与随机筛选（全部 sender 变量、`unsubscribeUrl`、`expertName`——displayName 兜底 orcidId 永不为空）。随机端点从 `text` 服务端提取占位符键（复用 `PLACEHOLDER_REGEX`），前端只传原文。
- Applies to: `MailVariableService.variableMetadata()`、随机端点。
- Violation consequence: 映射双源漂移；或前端解析占位符破坏 I-1。
- 来源: original

### Invariant I-4: 随机抽样语义
- Rule: `mode=SATISFY_ALL` = 正文全部可筛选键的 esField 均满足 `exists`；`mode=MISSING_ANY` = `bool.should` 各字段 `must_not exists`、`minimum_should_match=1`。text 类型字段（`institution`/`keyword`/`employment`，见 mapping）`exists` 无法排除空串，服务层取随机批（`function_score`+`random_score`，size=20）后在 Kotlin 过滤空白值再抽 1 个；SATISFY_ALL 批内全滤空时返回未命中。`matchCount`/`totalCount` 用同一 filter 的 `_count` 查询。正文无可筛选键时退化为全库随机且 `matchCount==totalCount`。
- Applies to: `ExpertSearchService` 新查询方法、随机端点。
- Violation consequence: "全满足"抽出空串专家，运营对覆盖率失去信任。
- 来源: original（text/keyword 类型差异见 `es/orcid_info_candidate.json` 审计）

### Invariant I-5: 抽屉是全局单例组件，不是视图
- Rule: 抽屉 DOM 挂 `index.html` 顶层一份（`#previewDrawer`），JS 以 `openPreviewDrawer(ctx)` 单入口打开；不新增侧栏 `.nav-tab`/`.view`/`viewMeta`/`refreshCurrentView` 注册（不触发 K-view-registration-triad）；`#varPreviewModal` 及其 JS（`openVariablePreview` 弹 modal 部分、`refreshVariablePreviewFromContext` 的 modal 绑定）删除，入口函数改为打开抽屉。
- Applies to: `index.html`、`app.js`、`styles.css`。
- Violation consequence: 双预览组件并存漂移；或误注册视图导致切换报错。
- 来源: K-view-registration-triad

### Invariant I-6: ES 故障降级不阻断手选预览
- Rule: 随机端点 ES 异常时返回 `{expert: null, error: "..."}`（HTTP 200），前端提示"随机抽样暂不可用"；手动输入 ORCID / 从上下文带入 contact 的预览路径不受影响。
- Applies to: 随机端点、`app.js` 抽屉错误分支。
- Violation consequence: ES 抖动导致整个预览功能不可用。
- 来源: original（语义对齐 Plan A I-5）

## 现状审计

### 后端已有能力（Plan A/B 实装，已 re-grep 确认）
- `MailVariableService`（mail/service，248 行）：`buildVariables`（19 变量：5 sender + 13 expert + unsubscribeUrl）、`variableMetadata()`（key/label/nullable/example，**尚无 esField**）、`renderPreview()`（返回 rendered+fallbackKeys）、`validatePlaceholders`、`PLACEHOLDER_REGEX`、`resolveExpertProfile`（level 解析 + APPLICATION→CANDIDATE 回退）。
- `QaRuleManagementController`：`GET /api/qa/template-variables-meta`（:31）、`POST /api/qa/render-preview`（:35，body: text + contactId|orcidId+level）。
- `ExpertSearchService`：`findByOrcidId(orcidId, level)`（term 查询）；现有 filter 构造均为 `bool.filter` map 写法；**无随机查询、无字段存在性计数**。
- ES mapping（`es/orcid_info_candidate.json`）：`familyNames`(text) `researchFields`(keyword) `institution`(text) `keyword`(text) `employment`(text) `country`(keyword) `degree`(keyword) `hIndex`/`worksCount`/`lastPublicationYear`(integer) `recentWorkTitles`/`patentTitles`(keyword)。`dynamic: false`。

### render-preview 读路径（响应契约消费者）
1. `app.js:1913` — 旧 modal 的 `refreshVariablePreviewFromContext`（本计划删除，由抽屉替代）。
2. `app.js:7420` — 组装台 `refreshComposedRenderedPreview`，读 `rendered` + `fallbackKeys`（**保留，I-2 保护对象**）。

### 前端预览入口与旧 modal
- 入口按钮：`data-var-preview-target`（textarea id）+ `resolveVarPreviewContact`（contactId/orcidId），`openVariablePreview`（app.js:1880）打开 `#varPreviewModal`（index.html:1560-1565，`modal-shell`/`modal-backdrop`/`modal-panel` 结构）。
- 现有样式资产：`.var-chip-bar/.var-chip`（styles.css:5324+）、`.var-fallback-hint`（:5387）、`.modal-shell` z-index **50**（:1923）、CSS 变量：`--primary:#2563eb`、`--primary-light:rgba(37,99,235,0.07)`、`--bg-main:#f5f7fb`、`--border:rgba(15,23,42,0.11)`、`--text-main:#1e293b`、`--text-muted:#94a3b8`、`--panel-bg`、`--radius-lg`、`--shadow-xl`。
- 正文展示契约：`.pre`（white-space:pre-wrap）+ `escapeHtml`（K-mail-body-display-sites）。

### 交互点
- 新随机端点（读 ES）× 抽屉（读 render-preview）：两次请求串联，抽中后回填专家再渲染——I-6 保证前者失败不影响后者。
- render-preview 响应扩展 × 组装台既有消费者——I-2。

## 实现方案

### Task 1: `MailVariableService` 增加 esField 与键提取 [I-3]
- `VariableMeta` 增加 `esField: String?`。映射表（写死在 companion）：`expertFamilyName→familyNames`、`researchFields→researchFields`、`institution→institution`、`keyword→keyword`、`expertCountry→country`、`employment→employment`、`hIndex→hIndex`、`worksCount→worksCount`、`lastPublicationYear→lastPublicationYear`、`degree→degree`、`recentWorkTitle→recentWorkTitles`、`patentTitle→patentTitles`；其余（5 个 sender、`expertName`、`unsubscribeUrl`）为 null。
- 新增 `fun filterableEsFields(text: String): List<String>`：`PLACEHOLDER_REGEX` 提取 key → 映射 esField → 去重（顺序稳定）。
- `renderPreview` 返回值扩展：`variables`（对本次 text 中出现的 key，含 key/label/value/filled/usedFallback）与 `invalidTokens`（复用 `validatePlaceholders` 的白名单外 token 子集，**不含**"可空缺兜底"类违规——那是保存期校验语义，预览期只标未知 key）。

### Task 2: `ExpertSearchService` 随机与计数查询 [I-4, I-6]
- `fun countByFieldPresence(level, fields: List<String>, mode): Long`——`_count` API，SATISFY_ALL 用 `filter: [exists...]`；MISSING_ANY 用 `should: [bool{must_not exists}...] + minimum_should_match:1`。
- `fun findRandomByFieldPresence(level, fields, mode): ExpertProfile?`——同 filter 外包 `function_score { random_score: {} }`，`size:20`，`_source` 复用 `sourceFields()`；SATISFY_ALL 在 Kotlin 层过滤 text 字段空白后随机取 1；MISSING_ANY 直接随机取 1。
- 两方法均 try/catch 由调用方处理（controller 捕获转 I-6 降级响应）。

### Task 3: 随机端点 [I-1, I-3, I-4, I-6]
- `QaRuleManagementController` 新增 `POST /api/qa/preview/random-expert`，body `{text, level="CANDIDATE", mode="SATISFY_ALL"|"MISSING_ANY"}`。
- 流程：`filterableEsFields(text)` → count(matchCount) + count(totalCount, fields=emptyList 即 match_all) → 随机取 1 → 响应 `{expert: {orcidId, displayName, email, indexLevel}, matchCount, totalCount, filteredFields, error: null}`；异常时 `{expert:null, error:"..."}`（HTTP 200）。

### Task 4: 抽屉 DOM 与样式 [I-5]
`index.html`：删除 `#varPreviewModal` 整块（1560-1565 及其 panel 内容）；`</body>` 前新增：

```html
<div class="preview-drawer-shell" id="previewDrawer" hidden>
  <button class="preview-drawer-backdrop" id="previewDrawerBackdrop" type="button" aria-label="关闭预览"></button>
  <aside class="preview-drawer" role="dialog" aria-modal="true" aria-labelledby="previewDrawerTitle">
    <header class="preview-drawer-head">
      <div class="preview-drawer-title-group">
        <h2 id="previewDrawerTitle">邮件预览</h2>
        <span class="preview-source-badge" id="previewSourceBadge">变量渲染</span>
      </div>
      <button class="button secondary preview-drawer-close" id="previewDrawerCloseBtn" type="button" aria-label="关闭">×</button>
    </header>
    <div class="preview-drawer-context">
      <div class="preview-ctx-grid">
        <label class="preview-ctx-field">以专家渲染
          <input type="text" id="previewOrcidInput" placeholder="ORCID，如 0000-0001-2345-6789" />
        </label>
        <label class="preview-ctx-field">范围
          <select id="previewScopeSel">
            <option value="CANDIDATE">全部候选（L2）</option>
            <option value="APPLICATION">已回信（L1）</option>
          </select>
        </label>
      </div>
      <div class="preview-ctx-row">
        <label class="preview-ctx-field preview-ctx-grow">抽样模式
          <select id="previewModeSel">
            <option value="SATISFY_ALL">随机 · 全满足</option>
            <option value="MISSING_ANY">随机 · 有缺失</option>
          </select>
        </label>
        <button class="button secondary preview-dice-btn" id="previewDiceBtn" type="button">随机抽取</button>
        <button class="button secondary" id="previewRefreshBtn" type="button">刷新预览</button>
      </div>
      <p class="preview-coverage" id="previewCoverage" hidden></p>
    </div>
    <div class="preview-drawer-body">
      <div class="preview-mail-card">
        <table class="preview-mail-meta">
          <tr><td>To</td><td id="previewMailTo">—</td></tr>
          <tr><td>Subject</td><td id="previewMailSubject">—</td></tr>
        </table>
        <div class="preview-mail-divider"></div>
        <div class="preview-mail-body pre" id="previewMailBody"></div>
      </div>
      <div class="preview-var-section">
        <div class="preview-var-head">
          <span>变量状态</span>
          <span class="preview-var-stat" id="previewVarStat"></span>
        </div>
        <div id="previewVarRows"></div>
      </div>
      <p class="preview-drawer-error" id="previewDrawerError" hidden></p>
    </div>
  </aside>
</div>
```

`styles.css` 追加（**逐条按此实现，不做发挥**）：

```css
.preview-drawer-shell { position: fixed; inset: 0; z-index: 60; }
.preview-drawer-shell[hidden] { display: none; }
.preview-drawer-backdrop { position: absolute; inset: 0; border: none; background: rgba(15, 23, 42, 0.35); backdrop-filter: blur(6px); -webkit-backdrop-filter: blur(6px); cursor: default; opacity: 0; transition: opacity 0.2s ease; }
.preview-drawer-shell.open .preview-drawer-backdrop { opacity: 1; }
.preview-drawer { position: absolute; top: 0; right: 0; bottom: 0; width: min(440px, 92vw); display: flex; flex-direction: column; background: var(--panel-bg, #ffffff); border-left: 1px solid var(--border); box-shadow: -16px 0 40px -16px rgba(15, 23, 42, 0.28); transform: translateX(100%); transition: transform 0.24s cubic-bezier(0.22, 0.8, 0.36, 1); }
.preview-drawer-shell.open .preview-drawer { transform: translateX(0); }
.preview-drawer-head { display: flex; align-items: center; justify-content: space-between; padding: 14px 16px; border-bottom: 1px solid var(--border); flex-shrink: 0; }
.preview-drawer-title-group { display: flex; align-items: center; gap: 8px; }
.preview-drawer-head h2 { margin: 0; font-size: 15px; font-weight: 600; color: var(--text-main); }
.preview-source-badge { font-size: 11px; padding: 2px 8px; border-radius: 999px; background: var(--primary-light); color: var(--primary); }
.preview-drawer-close { width: 28px; height: 28px; padding: 0; line-height: 1; }
.preview-drawer-context { padding: 12px 16px; border-bottom: 1px solid var(--border); flex-shrink: 0; }
.preview-ctx-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 8px; margin-bottom: 8px; }
.preview-ctx-row { display: flex; gap: 8px; align-items: flex-end; }
.preview-ctx-grow { flex: 1; min-width: 0; }
.preview-ctx-field { display: flex; flex-direction: column; gap: 4px; font-size: 12px; color: var(--text-muted); }
.preview-ctx-field input, .preview-ctx-field select { width: 100%; font-size: 13px; height: 32px; }
.preview-dice-btn { white-space: nowrap; height: 32px; }
.preview-coverage { margin: 8px 0 0; font-size: 12px; color: var(--text-muted); }
.preview-coverage strong { color: var(--text-main); font-weight: 600; }
.preview-drawer-body { flex: 1; overflow-y: auto; padding: 14px 16px; }
.preview-mail-card { background: var(--bg-main); border-radius: 10px; padding: 12px 14px; }
.preview-mail-meta { width: 100%; font-size: 12px; table-layout: fixed; border-collapse: collapse; }
.preview-mail-meta td { padding: 2px 0; vertical-align: top; }
.preview-mail-meta td:first-child { color: var(--text-muted); width: 52px; }
.preview-mail-meta td:last-child { color: var(--text-main); word-break: break-all; }
.preview-mail-divider { border-top: 1px solid var(--border); margin: 10px 0; }
.preview-mail-body { font-size: 13px; line-height: 1.7; color: var(--text-main); }
.preview-var-value-tag { background: #dcfce7; color: #166534; border-radius: 3px; padding: 0 3px; }
.preview-var-fallback-tag { background: #fef3c7; color: #92400e; border-radius: 3px; padding: 0 3px; }
.preview-var-section { margin-top: 14px; }
.preview-var-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; font-size: 12px; color: var(--text-muted); }
.preview-var-row { display: flex; align-items: center; gap: 8px; padding: 6px 0; border-bottom: 1px solid var(--border); font-size: 12px; }
.preview-var-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.preview-var-dot.filled { background: #16a34a; }
.preview-var-dot.fallback { background: #f59e0b; }
.preview-var-dot.invalid { background: #dc2626; }
.preview-var-key { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 11px; color: var(--text-muted); width: 148px; flex-shrink: 0; }
.preview-var-label { color: var(--text-muted); width: 64px; flex-shrink: 0; }
.preview-var-value { color: var(--text-main); min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.preview-drawer-error { margin: 12px 0 0; font-size: 12px; color: #b91c1c; background: #fef2f2; border-radius: 8px; padding: 8px 12px; }
@media (max-width: 640px) { .preview-drawer { width: 100vw; border-left: none; } }
```

设计对齐说明（给执行 agent 的意图，不是可选项）：z-index 60 高于 `.modal-shell` 的 50（抽屉可从 QA 规则编辑 modal 之上打开）；绿/黄/红 hex 取 Tailwind green-600/amber-500/red-600 系，与既有 `.var-fallback-hint` 色系一致；开合动画只用 transform/opacity。

### Task 5: 抽屉 JS [I-1, I-2, I-5, I-6]
`app.js`：
- `openPreviewDrawer({ targetId, contactId, orcidId })`：替换 `openVariablePreview` 的 modal 打开逻辑（入口 data-* 契约不变）；写入 `state.previewDrawer = { targetId, contactId, orcidId, level, mode }`；`#previewDrawer.hidden=false` → 下一帧加 `.open`（触发过渡）；`document.body.classList.add("modal-open")` 锁滚动。
- 关闭：close 按钮 / backdrop / Esc 键；移除 `.open` 后 240ms 再置 hidden。
- `refreshPreviewDrawer()`：取 targetId 对应 textarea 当前草稿 → 调 `/api/qa/render-preview` → 渲染：
  - To/Subject：contact 上下文有值则显示，否则 "—"；Subject 显示所在编辑器的 replySubject 值（QA 规则表单有此字段；片段编辑器无则显示 "—"）。
  - 正文：`escapeHtml(rendered)` 后按 `variables` 中 `filled` 值做精确子串包 `.preview-var-value-tag`（usedFallback 的兜底文案包 `.preview-var-fallback-tag`）；匹配不到则不高亮（展示增强，不影响正确性）；结果塞入 `.preview-mail-body`。
  - 变量面板：`variables` 每项一行（dot 状态 = usedFallback ? fallback : filled ? filled : fallback）；`invalidTokens` 每个追加红点行，值列显示 "白名单外，将原样发出"。
  - 统计行："N 有值 · M 兜底 · K 非法"。
- 随机：`#previewDiceBtn` → `POST /api/qa/preview/random-expert {text, level, mode}` → `expert==null` 时 `#previewDrawerError` 显示（error 或 "没有满足条件的专家"）；命中则回填 `#previewOrcidInput`、更新覆盖率行 `满足全部占位符：<strong>N / M</strong>（P%）` 并 `refreshPreviewDrawer()`。
- 删除 `refreshVariablePreviewFromContext`、`renderVariablePreviewResult` 及 `#varPreviewModal` 相关绑定；**组装台 `refreshComposedRenderedPreview`（7391-7433）一行不动**。
- 全文件不得新增 `${` 解析逻辑（I-1）。

### Task 6: 测试
- `MailVariableServiceTest`：esField 映射完整性（可筛选 12 键 + null 键清单断言）、`filterableEsFields` 提取/去重、renderPreview 扩展字段（variables 状态三态、invalidTokens 只含未知 key）。
- `ExpertSearchServiceTest`：SATISFY_ALL/MISSING_ANY 请求体构造断言（exists/must_not/minimum_should_match/random_score/size:20）、text 字段空串批内过滤、空批返回 null。

## 变更文件清单

| # | 文件 | 改动 |
|---|------|------|
| 1 | `mail/service/MailVariableService.kt` | esField + filterableEsFields + renderPreview 扩展 |
| 2 | `expert/service/ExpertSearchService.kt` | 随机 + 计数查询 |
| 3 | `qa/controller/QaRuleManagementController.kt` | random-expert 端点 + render-preview 响应扩展 |
| 4 | `src/main/resources/static/index.html` | 删 varPreviewModal、加抽屉 DOM |
| 5 | `src/main/resources/static/app.js` | 抽屉组件 + 入口迁移 + 随机交互 |
| 6 | `src/main/resources/static/styles.css` | 抽屉样式（按 Task 4 逐条） |
| 7 | `mail/service/MailVariableServiceTest.kt`（test） | Task 6 |
| 8 | `expert/service/ExpertSearchServiceTest.kt`（test） | Task 6 |

（8 文件，2 子系统：后端只读端点 + 前端抽屉）

## 验收标准

- I-1: grep `app.js` 无新增 `${` 解析/替换；随机端点与 ES 查询方法无 save/send/`@Transactional`；render-preview 输出与 `MailVariableService.renderForContact` 对同一输入逐字节一致（单测已在 Plan B 覆盖，回归即可）。
- I-2: 组装台预览手工回归（选规则→预览→渲染值与兜底提示正常）；对 render-preview 旧字段做响应结构断言。
- I-3: `variableMetadata()` 单测断言 12 个可筛选键与 7 个 null 键；前端 grep 无 esField 硬编码。
- I-4: 请求体构造单测（两种 mode）；mock 批内含空串 institution 时 SATISFY_ALL 不返回该专家；正文无占位符 → matchCount==totalCount。
- I-5: `index.html`/`app.js`/`styles.css` grep 无 `varPreviewModal` 残留；侧栏注册四件套无新增；QA 规则编辑与片段编辑的预览按钮均打开抽屉（手工回归，含从 modal 之上打开的层级检查）。
- I-6: mock ES 抛异常 → 端点返回 `{expert:null, error}` HTTP 200；前端显示错误条且手输 ORCID 预览仍可用（手工回归）。
- 样式验收: 对照 Task 4 CSS 清单逐项核对（宽 440px、右侧滑出 240ms、backdrop blur、绿/黄/红三态 hex、z-index 60）；无"发送测试邮件"按钮。
- 集成: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test` 全绿。
