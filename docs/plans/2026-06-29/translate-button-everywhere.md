# 开发计划：邮件正文「翻译为中文」按钮（全站正文框）

> 由 create-p 生成。复验请用 fix-v。
> 翻译引擎：已部署的 LibreTranslate（`http://127.0.0.1:5000`，语言码 `en` / `zh-Hans`）。

---

## 需求描述

**可观察结果**：后台中所有展示邮件正文的位置，正文框内都带一个「翻译为中文」按钮；点击后在该正文框内就地显示中文译文（不跳转、不弹框），再次点击可收起。译文为按需实时翻译。

**必须不变（NOT change）**：
- 现有邮件收发 / 自动回复 / 人工分流主流程行为完全不变。
- 正文原文展示不变（仍用 `.pre` + `escapeHtml`，纯文本渲染）。
- 不新增数据库列、不改任何领域类、不改 `AutoMailReplyService` 入站管线、不改任何现有 DTO 字段。

**Out of scope（显式延后）**：
- 入库时预翻译并持久化 `cleaned_body_zh`（本计划只做按需翻译，不落库）。后续如需「列表直接显示译文/可检索」再单独立计划。
- 出站草稿/模板的翻译质量优化、多源语言自动检测调优。
- LibreTranslate 自身的高可用 / 鉴权改造（部署侧已完成基础部署）。

---

## 关键不变量

### Invariant I-1: 翻译是无状态、只读、按需的
- Rule: 翻译能力仅通过一个无状态接口 `POST /api/translate`（文本进、文本出）提供。该接口及其服务**不得**写任何数据库表、不得修改任何 `mail_record` / `inbound_mail_processing` 行、不得带 `@Transactional` 写语义。
- Applies to: `TranslationController`、`MailTranslationService`。
- Violation consequence: 触碰邮件事实表 → 破坏现有审计/复验边界（参考 K-backfill-readonly-inbound、K-bounce-visible-fields-persisted 的只读教训）。
- 来源: original（教训参考 K-backfill-readonly-inbound）

### Invariant I-2: 翻译失败绝不影响页面与主流程
- Rule: 翻译服务关闭（`enabled=false`）、超时、5xx、空文本时，接口返回明确的失败/空结果；前端正文原文与页面其余功能**不受任何影响**，按钮回到可重试状态。后端服务方法**绝不抛异常冒泡**，失败 `log.warn` 返回空。
- Applies to: `MailTranslationService.translate()`、`TranslationController`、前端 `onTranslateClick`。
- Violation consequence: 翻译服务抖动会让后台页面报错或正文消失。
- 来源: original

### Invariant I-3: 发送原文、展示转义
- Rule: 点击翻译时，发送给后端的是正文**原始文本**（未经 HTML 转义的 displayed text 源串）；译文回来后在 `.pre` 框内必须经 `escapeHtml` 渲染。绝不把已转义的 HTML 串送去翻译，也绝不把译文当 HTML 注入。
- Applies to: 前端 `translatableBody()` 渲染 + `onTranslateClick`。
- Violation consequence: `&amp;`/`&lt;` 进入翻译降低质量；译文未转义 → XSS。
- 来源: original

### Invariant I-4: 按钮只出现在「邮件正文框」，且覆盖全部正文框
- Rule: 翻译按钮恰好出现在本计划「现状审计」枚举的全部邮件正文展示点（共 6 处），不多不少。实现方式：统一改用共享渲染器 `translatableBody(text)` 产出「正文框 + 按钮」，不得用 “给页面所有 `.pre` 盲挂按钮” 的做法（会污染非邮件正文的 `.pre`）。
- Applies to: app.js 全部 6 个正文渲染点。
- Violation consequence: 漏挂 → 需求不达；盲挂 → 误伤其他 `.pre`（如 QA 内容、JSON 预览）。
- 来源: original

### Invariant I-5: 输入受限、出网受控
- Rule: 接口对入参做长度上限校验（`max-chars`，默认 5000，超出截断或 400）；服务只向配置的 `base-url` 发请求，URL 不接受调用方传入，杜绝 SSRF。
- Applies to: `TranslationController`、`MailTranslationService`、`TranslationProperties`。
- Violation consequence: 超长文本拖垮翻译服务；可控 URL → SSRF。
- 来源: original

---

## 现状审计

> 本计划**不触碰任何数据存储**（无新列、无读写邮件表）。因此审计对象为：①「邮件正文文本」在前端的全部展示点（read paths，即按钮要挂载的位置）；② 后端新增的唯一外部依赖——LibreTranslate HTTP 服务；③ 配置注册路径。

### 存储：无
- 不新增/修改任何 DB 表、ES 索引、领域类、迁移。译文不落库（见 Out of scope）。

### 前端正文展示点（read paths of mail body — 按钮挂载全集）
文件：`src/main/resources/static/app.js`。正文框统一类名 `.pre`（`styles.css:~1506`，`white-space:pre-wrap`，经 `escapeHtml` 渲染——见 K-plaintext-reply-client-reflow）。

1. `renderMailItem()` ~L3796-3797 —「查看完整正文」`<details>` 内 `<div class="pre">${body}</div>`（专家详情面板 INBOUND/OUTBOUND 邮件）。
2. `renderMailItem()` ~L3793 —「正文预览」`<div class="mail-preview">`（compact 预览；本计划**不在预览行加按钮**，按钮挂在完整正文框 #1，避免重复）。
3. 收发件箱邮件详情面板 ~L4808-4811 —「正文」`<div class="pre">${body}</div>`（`loadMailbox` 详情，源 `/api/mail/mailbox/...`）。
4. 未匹配来信详情 ~L5384-5388 —「原始正文」`<div class="pre">${record.body}</div>`。
5. 未匹配来信详情 ~L5390-5394 —「清洗后正文」`<div class="pre">${record.cleanedBody}</div>`。
6. 自动回复预览 ~L5157-5161 —「回复正文」`<div class="pre">${preview.replyBody}</div>`（OUTBOUND 草稿；运营也可能要中译，纳入）。

非正文/预览片段（**不挂按钮**，明确排除）：
- 日志详情 `bodyPreviewText` ~L4325/4332（截断预览，非完整正文框）。
- 列表表格单元格 `r.cleanedBody.slice(0,80)` ~L5909（表格摘要）。
- 各类 QA 规则 / JSON / 监控用的其他 `.pre`。

→ 共 **6 个正文框挂载点**（#1、#3、#4、#5、#6 各一个按钮；#2 不挂）。实际渲染点 5 处 `<div class="pre">` 改为共享渲染器。

### 后端：配置注册路径
- `@ConfigurationProperties` 统一在 `config/RestTemplateConfig.kt` 的 `@EnableConfigurationProperties(...)` 列表注册（现有 22 个）。新增 `TranslationProperties` 需加入该列表。
- 已有通用 `restTemplate()` Bean（`RestTemplateConfig.kt:38`，无超时）。本计划新增**带超时**的 `@Qualifier("translationRestTemplate")` Bean，避免翻译卡死拖垮调用线程（I-2）。

### Interaction points
- IP-1：前端 6 处正文框 ←→ 新增 `POST /api/translate`。唯一交互面，文本进出，无状态。
- IP-2：`MailTranslationService` ←→ LibreTranslate（外部 HTTP）。失败必须降级（I-2）。
- 无 DB / 无跨模块写读交互。

---

## 实现方案

### 阶段 A：后端翻译服务（子系统 1）

**A-1 新增 `config/TranslationProperties.kt`**（遵守 I-5）
- `prefix = "talent-introduction.translation"`，字段：`enabled: Boolean=false`、`baseUrl: String="http://127.0.0.1:5000"`、`source: String="en"`、`target: String="zh-Hans"`、`timeoutMs: Int=5000`、`maxChars: Int=5000`、`apiKey: String?=null`。

**A-2 编辑 `config/RestTemplateConfig.kt`**
- 在 `@EnableConfigurationProperties(...)` 列表追加 `TranslationProperties::class`。
- 新增 `@Bean @Qualifier("translationRestTemplate")`，用 `RestTemplateBuilder` 设 connect/read timeout = `translationProperties.timeoutMs`。

**A-3 新增 `mail/service/MailTranslationService.kt`**（遵守 I-1、I-2、I-5）
- 注入 `TranslationProperties` + `@Qualifier("translationRestTemplate") RestTemplate`。
- `fun translate(text: String): TranslationResult`：
  - `enabled=false` 或 `text.isBlank()` → 返回「未启用/空」结果（不报错，I-2）。
  - 截断到 `maxChars`（I-5）。
  - POST `${baseUrl}/translate`，body `{q,source,target,format:"text"[,api_key]}`，解析 `translatedText`。
  - 全异常 try-catch，失败 `log.warn` 返回失败结果，**不抛出**（I-2）。
  - URL 仅取自配置（I-5）。
- 定义返回类型（如 `data class TranslationResult(val ok: Boolean, val text: String?, val reason: String?)`）。

**A-4 新增 `mail/controller/TranslationController.kt`**（遵守 I-1、I-2、I-5）
- `POST /api/translate`，请求体 `{ "text": String }`（**只接 text，不接 url/source 覆盖**，I-5）。
- 入参超 `maxChars` → 400 或截断（与服务一致）。
- 调 `MailTranslationService.translate`，返回 `{ ok, translatedText, reason }`。
- 无 `@Transactional`、无任何持久化（I-1）。

### 阶段 B：前端按钮与就地展示（子系统 2）

**B-1 编辑 `src/main/resources/static/app.js`**
- 新增共享渲染器 `translatableBody(text, opts)`：产出
  `<div class="pre translatable-body" data-src="...">…原文…</div>` +
  `<button class="btn-translate" type="button">🌐 翻译为中文</button>` +
  一个空的 `<div class="translation-text" hidden>` 容器。
  - 原文经 `escapeHtml` 显示；原始文本通过闭包/`data-` 安全传递（不要把原文 HTML 转义后再回送，I-3）。
- 新增点击处理 `onTranslateClick(btn)`（事件委托，绑定一次）：
  - 首次点击：按钮置「翻译中…」禁用 → `await api('/api/translate',{method:'POST',body:{text}})` →
    成功：`translation-text` 内 `escapeHtml(译文)` 显示（I-3），按钮变「收起译文」；
    失败/未启用：按钮变「翻译失败，重试」可再点（I-2）。
  - 再次点击：toggle 显示/收起译文（已翻译则不再请求）。
- 把现状审计 #1、#3、#4、#5、#6 这 5 个 `<div class="pre">…</div>` 替换为 `translatableBody(...)`（I-4）。#2 预览行不动。

**B-2 编辑 `src/main/resources/static/styles.css`**
- `.btn-translate`（小号、次要按钮样式，复用现有变量 `--accent`/`--border`/`--surface`）、`.translation-text`（`.pre` 同款 pre-wrap 盒子 + 顶部虚线分隔）、禁用态样式。

### 阶段 C：测试

**C-1 新增 `MailTranslationServiceTest`**：mock `translationRestTemplate`，覆盖 成功 / 超时 / 5xx / 空文本 / `enabled=false` / 超长截断 六条路径，断言**任何分支都不抛异常**（I-2）、URL 恒为配置 baseUrl（I-5）。

**C-2 新增 `TranslationControllerTest`**（MockMvc）：正常返回 `translatedText`；超长入参被拒/截断（I-5）；服务失败时返回 `ok:false` 而非 500（I-2）。

---

## 变更文件清单

| # | 文件 | 类型 | 说明 |
|---|------|------|------|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/config/TranslationProperties.kt` | 新增 | 翻译配置（I-5） |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/config/RestTemplateConfig.kt` | 编辑 | 注册 props + 带超时 RestTemplate Bean |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailTranslationService.kt` | 新增 | 翻译调用 + 降级（I-1/I-2/I-5） |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/TranslationController.kt` | 新增 | `POST /api/translate`（I-1/I-5） |
| 5 | `src/main/resources/application.yml` | 编辑 | `talent-introduction.translation.*` 配置块 |
| 6 | `src/main/resources/static/app.js` | 编辑 | `translatableBody()` + `onTranslateClick()` + 5 处挂载（I-3/I-4） |
| 7 | `src/main/resources/static/styles.css` | 编辑 | 按钮与译文框样式 |
| 8 | `src/test/kotlin/.../mail/service/MailTranslationServiceTest.kt` | 新增 | 服务单测 |
| 9 | `src/test/kotlin/.../mail/controller/TranslationControllerTest.kt` | 新增 | 接口测试 |

合计 9 个文件（≤10）。子系统 2 个（后端翻译 / 前端）。新增数据字段 0。

---

## 验收标准

- **I-1**：`MailTranslationService` / `TranslationController` 源码无 `repository`/`save`/`@Transactional`/对邮件表的写；接口多次调用不改任何 DB 行。
- **I-2**：停掉 LibreTranslate（或 `enabled=false`）后，点击「翻译」按钮 → 页面不报错、正文原文完好、按钮显示可重试；`MailTranslationServiceTest` 所有失败路径断言不抛异常。
- **I-3**：抓包确认请求体 `text` 为原始正文（无 `&amp;` 等转义）；构造含 `<script>`/`&` 的正文，译文区按文本展示不执行、不破坏 DOM。
- **I-4**：逐一在 6 个正文框（专家详情完整正文、收发件箱正文、未匹配原始正文、未匹配清洗正文、自动回复预览正文）确认按钮存在并可用；确认日志预览 `bodyPreviewText`、表格摘要 `.slice(0,80)`、QA/JSON 等 `.pre` **无**按钮。
- **I-5**：构造 > `maxChars` 的入参 → 被截断或 400；代码审查确认 baseUrl 仅来自 `TranslationProperties`，接口不接受外部 URL。
- **集成（IP-1）**：英文回信在专家详情页点击翻译 → 正文框内就地显示中文（如「我对这个机会很感兴趣……」）；再次点击收起。

---

## 自检 (Phase 4)
- [x] 关键不变量 ≥1/新行为（I-1..I-5，覆盖只读/降级/转义/挂载范围/安全）
- [x] 现状审计列全 read paths（6 正文点 + 配置注册路径）；本计划不碰存储，已显式声明
- [x] 无任务引入未被不变量覆盖的写路径（本计划无写路径）
- [x] 文件数 9 ≤ 10；子系统 2 ≤ 2；新增字段 0 ≤ 1
- [x] 每个任务引用其约束不变量编号
- [x] 验收每个不变量 ≥1 检查
- [x] 文件清单无「等/related files」，逐一命名
- [x] Out of scope 显式延后预翻译落库
- [x] Phase 0 知识：K-plaintext-reply-client-reflow（`.pre` 渲染契约）已采用；K-backfill-readonly-inbound / K-bounce-visible-fields-persisted（只读边界）已转化为 I-1
- [x] 保存于 docs/plans/2026-06-29/
