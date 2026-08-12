# 退订链接形态与退订页品牌化 — 主索引与拆分说明

> 本文件**不是可执行计划**，是拆分决策与共享证据的索引。可执行计划见下方各子计划文件。
> 生成日期：2026-08-12
> 触发来源：Gmail 实测收信截图 —— 正文里退订链接以 150+ 字符裸 URL 明文展示；退订页样式简陋。
> 前序计划族：`docs/plans/2026-08-11/unsubscribe-closure-master.md`（Plan 01/02/02b 已展开，01 已落地为 `V87`）。

## 背景与现状

Gmail 实测收到的 INTRODUCTION 邮件里，退订行渲染为：

```
If you'd prefer not to receive future emails, you can unsubscribe here: https://qingfei.szwbww.com/talent/u/unsubscribe?token=bGliaWFuaW5hdGF5cGU0OTZAZ21haWwuY29t.3LYegk6UTeWSWz2N6aW27pJ5Rw2-RjUfcaT2p8-bXyl
```

三个可验证的问题（均已逐行核对代码，非推断）：

1. **裸 URL 明文展示** —— `IntroductionMailComposer.kt:36-41` 构造 `ComposedMail` 时未设 `html`，取默认 `false`（`IntroductionMailComposer.kt:73`），`SmtpMailDeliveryService.kt:60-62` 走 `message.setText(...)` 纯文本分支，Gmail 只能自动 linkify 整串 URL。这正是前序 Plan 01 显式列入 out-of-scope 的一项（`unsubscribe-01-body-link.md:30`）。
2. **token 第一段可直接解码出收件人邮箱** —— `UnsubscribeTokenService.kt:17-21` 的 `sign()` 是 `enc(n) + "." + enc(mac)`，`enc(n)` 是 `Base64.getUrlEncoder().withoutPadding()` 编码的**明文邮箱**（`:45-47`）。截图里 `bGliaWFuaW5hdGF5cGU0…` 即是。
3. **退订页是裸 form** —— `UnsubscribeController.kt:43-55` 内联的 `confirmHtml` 只有一行 `<p>` 加一个无样式 `<button>`；确认后返回 `"<p>You have been unsubscribed.</p>"`（`:40`）。

## 需求方决策（2026-08-12 确认）

| 决策点 | 结论 | 影响 |
|---|---|---|
| 正文链接形态 | **改 HTML multipart，锚文本 + 纯文本保留全 URL** | Plan 06 |
| token 方案 | **一并改为不透明随机 id** | Plan 07（吸收前序索引里"Plan 06 token exp"的位置） |
| 退订页交互 | **确认页 + 成功页两态** | Plan 08 |

## 共享现状证据（各子计划复用，不重复 grep）

### E-1 `unsubscribeUrl` 的唯一产出点（沿用前序 E-1，本轮已复核未变）

`MailVariableService.buildVariables()`（`MailVariableService.kt:117-159`），`:155-157`：

```kotlin
val unsubscribeVars = mapOf(
    "unsubscribeUrl" to unsubscribeUrl(unsubscribeEmail, previewFallbacks)
)
```

`unsubscribeUrl()` 私有实现在 `:251-260`：邮箱为空 → `""`；`unsubscribeTokenService == null` 或 `!enabled()` → `previewUnsubscribeUrl()`（`:262-263`，非预览态也是 `""`）；否则 `service.unsubscribeUrl(email)`。

**关键推论：正文里的 `${unsubscribeUrl}` 可能被替换成空串**，锚文本转换必须对空串免疫（见 Plan 06 的 I-4）。

### E-2 两个冷外联模板当前的发送形态（不对称，是 Plan 06 的核心事实）

| 模板 | 组装点 | `ComposedMail.html` | `ComposedMail.text` | 证据 |
|---|---|---|---|---|
| `INTRODUCTION` | `IntroductionMailComposer.compose()` | **false**（默认值） | **null**（未设） | `IntroductionMailComposer.kt:36-41` |
| `MATERIAL_REMINDER` | `ManualExpertMailService.composeComposeTemplate()` | **true**（`:250`） | `rendered.body`（纯文本，`:251`） | `ManualExpertMailService.kt:246-256` |

即 MATERIAL_REMINDER 已经走 multipart（`val html = mailContentService.plainTextToHtml(rendered.body)`，实测在 `:243`），但 `plainTextToHtml()`（`MailContentService.kt:7-15`）只做 `escapeHtml` + 段落/`<br>` 包装，**不产生 `<a>` 标签**，所以它的 HTML 版里退订 URL 同样是裸文本。

### E-3 `plainTextToHtml` 的转义顺序决定了锚文本替换的可行性

`MailContentService.kt:7-15`：

```kotlin
fun plainTextToHtml(plain: String): String {
    if (plain.isBlank()) return ""
    return plain.split(Regex("\\n\\s*\\n"))
        .map { paragraph ->
            val inner = escapeHtml(paragraph.trim()).replace("\n", "<br>")
            "<p>$inner</p>"
        }
        .joinToString("")
}
```

`escapeHtml`（`:27-32`）只替换 `& < > " '` 五个字符。退订 URL 的字符集是 `https://` + 主机名 + 路径 + `?token=` + base64url（`A-Za-z0-9-_`）+ 分隔符 `.`，**不含任何被转义字符**，因此 URL 在 escape 后逐字保留，可以用精确串替换成 `<a>`。这是 Plan 06 I-3 成立的依据，不是假设。

### E-4 `mail_record.body` 必须保持纯文本（Plan 06 的最大交互点）

`K-plaintext-reply-client-reflow`：前端 `.pre`（**实测 `styles.css:1694`**，`white-space:pre-wrap`）配 `escapeHtml` 渲染（**实测 `app.js:3098` / `:5827` / `:9126` 等多处**），审计 `bodyPreviewText` 也读纯文本。若 `mail.body` 变成 HTML 而记录点原样落库，收发件箱会显示 `<p>` 标签源码。

> 该知识条目里记的 `styles.css:1506` 与 `app.js:5022` 本轮复核**均已过期**（文件增长导致漂移；`app.js:5022` 甚至不是展示点）。条目已就地更正，此处按实测值书写。

INTRODUCTION 的 7 个落库点（`grep -n "body = mail.body"`）：

| # | 文件:行 | 方法 |
|---|---|---|
| 1 | `InitialOutreachService.kt:95` | `txHelper.recordSuccess` |
| 2 | `InitialOutreachService.kt:105` | `txHelper.recordFailure` |
| 3 | `ManualInitialOutreachService.kt:695` | `txHelper.recordSuccess` |
| 4 | `ManualInitialOutreachService.kt:709` | `txHelper.recordFailure`（PERMANENT） |
| 5 | `ManualInitialOutreachService.kt:723` | `txHelper.recordFailure`（TRANSIENT/限流） |
| 6 | `ManualInitialOutreachService.kt:747` | `txHelper.recordFailure`（基础设施） |
| 7 | `ManualInitialOutreachService.kt:763` | `txHelper.recordFailure`（其他） |

两条路径的分工见 `K-dual-outreach-paths`：`InitialOutreachService.sendInitialBatch()` 与 `ManualInitialOutreachService.runScheduledBatch()` 并行存在，共用 `IntroductionMailComposer.compose()`。改 compose 会同时影响两条，因此 7 个落库点必须一次改全。

### E-5 Flyway 占位符替换已在生产关闭（Plan 06 新增迁移的前提）

`src/main/resources/application.yml:8-13` 当前是：

```yaml
  flyway:
    enabled: true
    locations: classpath:db/migration
    # 迁移中的 ${...} 是邮件模板变量（数据），不是 Flyway 占位符。
    # 开启替换会导致含 ${} 的正文迁移抛 "No value provided for placeholder expressions"。
    placeholder-replacement: false
```

前序 Plan 01 已落地，并有回归断言 `UnsubscribeBodyLinkMigrationTest.kt:46`。Plan 06 新增的 `V88` 含 `${unsubscribeUrl}`，依赖该配置继续存在 —— 断言已在位，无需新增（来源: K-flyway-placeholder-replacement，本轮复核为**已修复**状态，条目需更新）。

### E-6 正文 SSOT 是 `mail_compose_template_block.custom_text`

`K-mail-template-table-dead` 本轮复核仍成立：`grep -rn "mail_template\b" src/main/kotlin` 零命中。V87 已按此只改 `mail_compose_template_block`，V88 沿用。

### E-7 `UnsubscribeTokenService` 的现有装配面（Plan 07 的文件数约束）

`grep -rn "UnsubscribeTokenService(" src/` 共 9 处构造，全部在测试：

| 文件:行 | 用途 |
|---|---|
| `UnsubscribeTokenServiceTest.kt:17` / `:65` / `:66` | 直接单测 |
| `SmtpMailDeliveryServiceTest.kt:29` / `:35` | enabled / disabled 两个 fixture |
| `UnsubscribeControllerIllegalTokenTest.kt:60` | 非法 token |
| `ManualExpertMailServiceGateTest.kt:51` | 个性化门禁 |
| `MailVariableServiceTest.kt:42` / `:604` | 变量注入 |

生产侧由 Spring 注入，唯一注入方是 `MailVariableService`（`:112`，可空）与 `SmtpMailDeliveryService`（`:13`）。

**结论**：Plan 07 若给构造函数加**必填**参数，会一次性打爆 6 个测试文件，加上主代码 4 个即触顶 10 文件上限。因此 Plan 07 采用**可空 repository + 默认 null** 的加法（与 `MailVariableService.kt:112` 的 `unsubscribeTokenService: UnsubscribeTokenService? = null` 同一范式），把测试改动收敛到 1 个文件。代价是引入 test-only fallback 分支，须由 I-6 显式约束。

### E-8 退订端点的鉴权与路径事实（Plan 08）

- `AuthWebConfig.kt:24-26`：拦截器只 `addPathPatterns("/api/**")`。`/u/**` **不经过鉴权**，公网可达，符合退订页需求。
- `UnsubscribeControllerTest.kt:70` 已断言 `action="unsubscribe/confirm"`（**相对路径**），即前序 Plan 05 锚点里的 context-path 隐患已修复。Plan 08 必须保留该相对路径断言。
- `application.yml:86-88` 的 `talent-introduction.unsubscribe` 只有 `base-url` / `secret` 两项，对应 `UnsubscribeProperties.kt:8-11`。

### E-9 迁移可验证性范式（沿用前序 E-7）

- 文本断言（无需 Docker）：`UnsubscribeBodyLinkMigrationTest.kt` 用 `Files.readString(Path.of("src/main/resources/db/migration/V87__....sql"))`。V88/V89 沿用。
- 真实执行（需 Docker）：`FlywayMigrationIntegrationTest.kt:21` 带 `@EnabledIfSystemProperty(named = "migrationIt", matches = "true")`。

## 证据核验记录（2026-08-12 二轮复核）

初稿中有一批行号继承自 2026-08-11 前序计划与 `docs/knowledge/` 条目，或来自无行号的 `cat` 输出目测。按 create-p 的「知识只做研究种子、不替代 re-grep」规则重跑，**发现并订正 6 处错误**：

| # | 错误位置 | 初稿写法 | 实测值 | 后果若不订正 |
|---|---|---|---|---|
| 1 | Plan 06 现状审计 | `plainTextToHtml` 调用点"**唯一**"（`ManualExpertMailService.kt:241`） | **3 处**：`ManualExpertMailService.kt:243`、`AutoMailReplyService.kt:570`、`PendingMailOperationService.kt:204` | 执行 agent 可能误改另两条回复路径，或反过来漏判影响面 |
| 2 | 本文件 E-2 / Plan 06 T-6 | `ManualExpertMailService.kt:241`（转换）、`:243-252`（ComposedMail） | `:243`（转换，`:240-242` 是注释）、`:246-256`（ComposedMail） | 按 `:241` 定位会改到注释行 |
| 3 | 本文件 E-4 表 | `InitialOutreachService.kt:100` / `:108` | `:95` / `:105` | 落库点定位错误，I-2 漏改 |
| 4 | Plan 06 验收标准 | `SmtpMailDeliveryServiceTest` header 用例 `:126/:152/:176` | `:133` / `:159` / `:183`；且计划要"新增"的 plain 部件断言**已存在**于 `:252` | 会重复新增已有用例 |
| 5 | Plan 08 多处 | `UnsubscribeControllerTest.kt:73/74/79-85/95/97-101` | `:70` / `:71` / `:82` / `:92` / `:94-98` | 改测试时定位错行 |
| 6 | Plan 06 现状审计 | `resolveBlocks()` `:248/:279/:294`（承自 K-mail-template-table-dead） | 函数 `:425`，分支 `:440` / `:480` / `:526` | 读路径定位错误 |

另订正两处过期知识引用：`K-plaintext-reply-client-reflow` 记的 `styles.css:1506` → 实测 `:1694`；`app.js:5022` → 该行**根本不是**正文展示点，实测展示点为 `app.js:3098` / `:5827` / `:9126` 等多处。两个知识条目已就地更正。

`AuthWebConfig.kt` 的引用由目测的 `:25-27` 订正为实测 `:24-26`。

### 本轮亲手 grep 复核通过、可直接采信的引用

`IntroductionMailComposer.kt`（全文 Read）、`UnsubscribeTokenService.kt`（全文 Read）、`UnsubscribeController.kt`（全文 Read）、`SmtpMailDeliveryService.kt`（全文 Read）、`MailContentService.kt:7-32`、`MailVariableService.kt:109-160/251-263/295`、`EmailSuppressionService.kt:18/25/38`、`UnsubscribeProperties.kt:8-11`、`application.yml:8-13/86-88`、`V87__…sql:11`、`V30__…sql`、`V61`/`V62` 存在性、`UnsubscribeTokenServiceTest.kt`（全文 Read）、`UnsubscribeBodyLinkMigrationTest.kt:46`、`FlywayMigrationIntegrationTest.kt:21`、`styles.css:1-40/1694`、7 个 `body = mail.body` 落库点、9 处 `UnsubscribeTokenService(` 构造点。

### 明确**不是**代码证据的两项（不要当结论看）

1. **V88 的新文案措辞**（`please use this link:`）—— 运营内容决策，没有代码依据可循，只有"HTML 锚文本化后原句会读作 `unsubscribe here: Unsubscribe`"这一条是可验证的事实。措辞由需求方定，改它不影响代码。
2. ~~**Plan 08 样式契约里的具体色值**~~ —— **该缺口已于同日闭环，见下节。**

## 证据缺口闭环：Plan 08 色值已改为官网源码采样（2026-08-12 补记）

初稿的色值来自效果预览（经需求方确认，但非官网采样）。需求方随后提供了官网源码本地仓库 `/Users/lukai/IdeaProjects/qingfeitalent-local`，已连接到会话并完成采样。

- 权威来源：`assets/css/site.css`（2419 行）。`index.html:12` 是 `<body class="visual-parity" data-page="home">`，因此**线上生效值是 `.visual-parity` 变体的覆盖**（`site.css:600-620` 段），不是基础规则。这一点漏掉就会取到错值（如按钮圆角基础规则是 `--radius-pill: 999px` 胶囊形，visual-parity 覆盖成 `7px`）。
- Plan 08 的 `## 样式契约 S-1` 已整块重写，`## 现状审计` 新增「官网设计基准采样表」，每行标注 `site.css` 采样行号。

**初稿 vs 官网实测的主要偏差**（说明这次核对不是走形式）：

| token | 初稿预览值 | 官网实测值 | 采样位置 |
|---|---|---|---|
| 页面底色 | `#0B1B2E` | `#05070f` + 8 层 radial-gradient 星空 + `background-attachment:fixed` | `site.css:5`、`:33-47` |
| 主文字 | `#F2F6FA` | `#eaf0ff` | `site.css:8` |
| 次文字 | `#9DB2C7` | `#93a3c4` / `#c3cee2` | `site.css:9-10` |
| 强调色 | `#4FA3D9` | `#3b82f6` | `site.css:11` |
| 主按钮 | 纯色 `#1E6FB8` | 三段渐变 `linear-gradient(100deg,#60a5fa,#3b82f6 55%,#6366f1)` + `0 0 34px rgba(59,130,246,0.26)` 辉光 | `site.css:17`、`:20`、`:92` |
| eyebrow 字体 | sans / 500 | **mono / 700** `font:700 11px/1 var(--font-mono)` | `site.css:62`、`:603` |
| 按钮圆角/字号/高度 | `8px` / `14px` / `padding 11px 26px` | `7px` / `13px` / `min-height:38px; padding:9px 16px` | `site.css:614` |
| 分隔线 | `rgba(255,255,255,0.08)` | 一致 ✅ | `site.css:15` |

保留的**刻意偏离只有 3 项**（容器宽度 560px vs 官网 1180px、成功页对勾圈用品牌蓝而非绿色、文字 wordmark 与 header 说明字号），每项在 Plan 08 里都写明了理由，其余全部逐字采样。Plan 08 的验收标准新增了正向/反向 grep 断言，作废的初稿色值（`#0B1B2E` / `#1E6FB8` / `#5DCAA5` 等）出现即判定改错来源。

## 拆分结果与依赖

create-p 硬限制：单计划 ≤10 文件、≤2 子系统。本次三项诉求分属三个子系统（邮件组装/投递、token 存储、HTTP 端点 + 前端样式），必须拆。

```
Plan 06  正文 HTML 锚文本            —— 邮件组装与投递
   │  无依赖，可独立部署
   ├─→ Plan 07  不透明随机 token      —— token 存储（改的是 URL 的值，不是它在正文里的形态）
   └─→ Plan 08  品牌化退订页          —— HTTP 端点 + 页面样式
```

三者**互不阻塞**，可并行执行、独立部署。唯一的耦合是人工验收：Plan 08 的页面要用 Plan 07 产出的短 token 才好看，但代码层面互不依赖（Plan 08 对 token 形态不做任何假设）。

| 计划 | 文件 | 子系统 | 文档 |
|---|---|---|---|
| 06 正文 HTML 锚文本 | 9 | 邮件组装/投递 + 模板内容迁移 | [unsubscribe-06-html-anchor-body.md](unsubscribe-06-html-anchor-body.md) |
| 07 不透明随机 token | 6 | token 存储 | [unsubscribe-07-opaque-token.md](unsubscribe-07-opaque-token.md) |
| 08 品牌化退订页 | 6 | HTTP 端点 + 页面样式 | [unsubscribe-08-branded-page.md](unsubscribe-08-branded-page.md) |

## 与前序计划族的关系（修正记录已同步回 2026-08-11 主索引）

- 前序索引里的 **Plan 06「token 有效期 exp」** 被本轮 Plan 07 取代：改为不透明随机 token 后，过期语义应落在 `unsubscribe_token.expires_at` 列上，而不是自签载荷里。本轮 Plan 07 **不做**过期判定（一个计划一个新字段），单列后续。
- 前序 **Plan 05「端点健壮性 + override UI」** 的两项与本轮 Plan 08 重叠：`action` 相对路径（已修复，见 E-8）、`value="$token"` 未 HTML 转义（Plan 08 的 I-3 承接）。Plan 05 剩余项（`enabled()` 为 false 的启动期告警、人工发信 override 勾选框）**不在**本轮范围。
- 前序 **Plan 03（header 收窄）/ Plan 04（会议邮件变量注入）** 与本轮无交集，状态不变。

## 不在任何计划范围内

- Gmail / Outlook 的实际分类、原生退订按钮展示 —— 不可由代码保证，属实测观察项。
- 退订页的多语言 —— 现有正文与页面均为英文单语，本轮不引入 i18n。
- 退订原因收集 —— 需新表新字段，需求方已确认不做。
- token 过期（`expires_at`）与密钥轮换 —— 见上，单列后续。
- 抑制名单运营界面 —— 已有 `EmailSuppressionController`，本轮不动。
