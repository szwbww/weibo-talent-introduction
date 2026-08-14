# P1 · 邮件预览注入绑定发件账号

> 前置：`expert-detail-head-main.md`（共享不变量 M-1 / M-2 / M-4、共享验证命令）。
> 本计划**无前端布局改动**，只改预览的账号解析。P2 依赖本计划。

## 需求描述

**可观察结果**

1. 专家详情 →「邮件预览」标签页，正文中的 `${senderName}` / `${senderEmail}` / `${senderTitle}` / `${teamName}` / `${countryName}` 渲染为该专家**已绑定发件账号**的实值，不再是空串。
2. 同一面板底部的「兜底: xxx」徽标不再出现上述 5 个 sender 变量的兜底项（前提是绑定账号的对应字段有值）。
3. 该面板的「收件人:」由 `preview@local` 变为该专家的真实邮箱。

**必须不变（黑盒可验收）**

1. 模板编辑器抽屉预览（`app.js:8347 renderServerComposeTemplatePreview`）显式选择账号时，仍以显式选择为准。→ A-4
2. 未绑定账号的专家，预览行为与现在完全一致（sender 变量为空 + 进 `fallbackKeys`），不报错、不空白、不出现满屏 `${}`。→ A-3
3. 专家详情四个子标签的键名、顺序与切换行为不变。→ A-9

**必须不变（仅机器可验，已提为不变量）**

- `renderExpertMailPreview` 一次渲染只发起 1 次 HTTP 调用 → I-2，验收由 `expertMailPreviewTab.test.js:198` 承担。
- `MailComposeTemplateService` 构造器参数个数与顺序不变 → I-6，验收由 `MailComposeTemplateServiceTest.kt:44-54` 的编译 + `git diff` 空区间承担。
- `GET /api/compose-templates/{id}/preview`（`MailComposeTemplateController.kt:59-61`）行为不变 → 本计划不触碰 `preview()` 方法；该端点经 grep 确认**无任何前端调用点**（`app.js` 中只有 `preview-draft`），故无黑盒路径，验收由 `git diff` 该方法区间为空承担。

**Out of scope**

- 预览不做 enabled/额度/预热门禁（发送侧 `requireAvailable` 的逻辑不搬进预览）。见 I-5 的说明与 A-5。
- 不改 `strictPlaceholders` 的取值（仍为 `false`，见 `K-compose-template-preview-endpoint-split`）。
- 不改 `variantIndex` 的计算方式。
- 不动 `app.js:8347 renderServerComposeTemplatePreview` 的 payload。

## 关键不变量

### I-1: 预览账号解析优先级

- Rule: `resolvePreviewAccount` 的解析顺序严格为 **① 请求显式 `senderAccountCode`（非空白）→ ② `contact.boundSenderAccountCode`（非空白）→ ③ `null`**。三者互斥，命中即返回。`contact == null` 时不得进入 ②。
- Applies to: `MailComposeTemplateService.resolvePreviewAccount`（`MailComposeTemplateService.kt:301-308`）；唯一调用点 `previewDraft`（`:227`）。
- Violation consequence: 若把 ② 排到 ① 前面，模板编辑器抽屉里运营显式选的账号会被专家绑定覆盖，抽屉的账号选择器失效。
- 来源: original（依据 M-2 与 `ManualExpertMailService.resolveAccount:159-177` 的同款优先级形状）

### I-2: 预览渲染的单次请求约束

- Rule: `renderExpertMailPreview(panel, orcidId)` 一次执行只允许调用 `api()` **一次**，且 URL 恒为 `/api/compose-templates/preview-draft`、method 恒为 `POST`。绑定账号 / contactId 只能来自**已在内存中的** `state.contacts`，不得为此新增任何网络请求。
- Applies to: `app.js:8086-8142 renderExpertMailPreview`。
- Violation consequence: `src/test/js/expertMailPreviewTab.test.js:198-220`（`assert.equal(calls.length, 1)`）直接失败。
- 来源: original（证据：逐字读取该测试）

### I-3: contactId 的传入条件

- Rule: payload 的 `contactId` 只在 `state.contacts` 中存在 `orcidId` 严格相等的条目、且该条目的 `contactId` 为非 `null`/非 `undefined` 时传入；其余一切情况传 `null`。
- Applies to: `app.js:8086-8104 renderExpertMailPreview` 的 payload 构造。
- Violation consequence: 传入不存在的 contactId 会让 `resolvePreviewContact`（`MailComposeTemplateService.kt:279-284`）的 `findById(...).orElse(null)` 返回 null，`previewDraft` 走 `contact == null` 分支（`:229-239`）直接返回未渲染的原始模板文本 —— 面板显示满屏 `${}`，且不报错，属静默降级。
- 来源: original（证据：逐字读取 `resolvePreviewContact` 与 `previewDraft` 的 null 分支）

### I-4: `contactId` 存在时 `orcidId` 与 `expertEmail` 的语义

- Rule: `resolvePreviewContact` 在 `contactId != null` 时**直接返回库中 contact 并忽略 `orcidId` / `expertEmail`**（`:280-282` 提前 return）。因此 payload 仍须照常传 `orcidId`（`variantIndex` 与前端逻辑依赖它），但**不得**依赖 `expertEmail` 参与收件人解析。
- Applies to: `app.js` payload；`MailComposeTemplateService.resolvePreviewContact`。
- Violation consequence: 误以为需要同时传 `expertEmail` 才能得到正确收件人，会掩盖"传了 contactId 就该以库为准"的事实，并在两者不一致时产生难查的分歧。
- 来源: original（证据：`MailComposeTemplateService.kt:279-284` 的提前 return）

### I-5: 预览不引入发送侧门禁，但必须能显示禁用账号

- Rule: `resolvePreviewAccount` 继续使用 `mailSenderAccountService.getAccount(code)`（不校验 `enabled`），并保留 `runCatching{}.getOrNull()` 的容错。绑定到已禁用账号的专家，预览必须正常渲染其签名。
- Applies to: `MailComposeTemplateService.resolvePreviewAccount`。
- Violation consequence: 若改用 `getEnabledAccount`，绑定到 `enabled=false` 账号的专家预览会静默退回空签名，而该专家的人工发送其实会**失败**（`SenderAccountBindingService.requireAvailable`），预览与发送反而更不同源。`mail_sender_account.enabled=false` 的现行语义是"禁止自动外发"而非"账号不可用"。
- 来源: K-sender-account-enabled-scope（本轮重新 grep 验证：`SenderAccountBindingService.kt:37` 用的是 `getAccount` 再 `requireAvailable`，与本条一致）

### I-6: 不得引入新的构造器依赖

- Rule: 本计划不得给 `MailComposeTemplateService` 增加构造器参数。所需的 `contact` 在 `previewDraft` 内已由 `resolvePreviewContact` 产出（`:226`），且 `expertContactRepository` 已在构造器中（`MailComposeTemplateService.kt:36`）。
- Applies to: `MailComposeTemplateService.kt:29-38` 构造器。
- Violation consequence: `src/test/kotlin/.../MailComposeTemplateServiceTest.kt:44-54` 用 **9 个位置参数**手写构造该服务，增参会让整个测试类编译失败。
- 来源: original（证据：逐字读取该测试的构造调用）

### I-7: 变量替换只能发生在 `renderPreview`

- Rule: 本计划不得为了"注入签名"而在 `MailComposeTemplateService` 内新增任何本地字符串替换。sender 变量的注入路径唯一：`resolvePreviewAccount` 产出 `account` → 传给 `mailVariableService.renderPreview(rawText, account, contact)`（`:236` 主题、`:249` 正文块）→ `MailVariableService.buildVariables` 的 `senderVars`（`MailVariableService.kt:124-129`）。
- Applies to: `MailComposeTemplateService.previewDraft`。
- Violation consequence: 提前吃掉 `${key|fallback}` token，导致 `fallbackKeys`、变量状态、strict skip 与真实发送管道不一致。
- 来源: K-preview-draft-raw-before-render（本轮重新验证：`previewDraft:219` 调 `resolveBlocks(..., renderVariables = false)`，原条目所述的隐患已由该参数隔离，结论仍须保持）

## 现状审计

### MySQL `expert_contact.bound_sender_account_code`

- Schema: 由 `SenderAccountBindingService` 独占写入；伴生列 `sender_account_bound_at`、`sender_account_changed`、`sender_account_changed_at`。
- **写路径（grep `bound_sender_account_code` --include=*.kt 全集，3 条 SQL + 3 个调用点）**
  1. `ExpertContactRepository.updateBindingById`（`:70-77`，SET code + bound_at）← 唯一调用点 `SenderAccountBindingService.bindIfAbsent`（`:43-46`）← `ManualExpertMailService.resolveAccount:176`、`ManualInitialOutreachService:280`。
  2. `ExpertContactRepository.rebindSenderAccountById`（`:79-88`，SET code + bound_at + **changed=true** + changed_at）← 唯一调用点 `SenderAccountBindingService.rebind`（`:50-70`）← `POST /api/expert-contacts/{id}/sender-account`。
  3. `ExpertContactRepository.migrateBindingByAccount`（`:90-97`，按源账号批量）← `SenderAccountBindingService.migrateAccount`（`:75-105`）← `POST /api/expert-contacts/sender-account/migrate`。
  4. 首次外发时随 contact 插入：`InitialOutreachService.kt:64`、`ManualInitialOutreachService.kt:641`（`boundSenderAccountCode = boundCode` 构造 `ExpertContact`）。
- **读路径**
  1. `SenderAccountBindingService.resolveForSend`（`:29-41`）—— **发送侧唯一读取入口**。
  2. `ManualExpertMailService.resolveAccount`（`:160`）—— 与显式请求值做 I-3 一致性校验。
  3. `ManualInitialOutreachService:591` —— 批量首封时判断是否已绑定。
  4. `ExpertContactManagementController:445/555`（`ExpertContactResponse`）—— DB 列表/详情路径。
  5. `ExpertIndexController:88/390/411/436`（`ExpertIndexResponse`）—— ES 列表路径。
  6. `ExpertContactRepository:112 findAllByBoundSenderAccountCode`、`:115-132` 两条统计查询 —— 账号页的绑定专家数。
  7. **本计划新增第 7 条**：`MailComposeTemplateService.resolvePreviewAccount`（经 `previewDraft` 已解析出的 `contact`）。
- **Interaction points**
  - IP-1：写路径 2（`rebind`）↔ 新增读路径 7。运营改绑后，预览必须反映新账号。**P1 只保证"重新渲染时读到新值"**；"改绑后自动刷新面板"由 P2 的 I-7 承担。
  - IP-2：写路径 1（`bindIfAbsent`，首封发送时自动补写）↔ 新增读路径 7。未绑定专家发出首封后再看预览，应出现签名。
  - IP-3：读路径 1（发送）↔ 读路径 7（预览）。二者必须解析出同一账号 —— 这是 M-2 的落点。

### `POST /api/compose-templates/preview-draft`

- Controller：`MailComposeTemplateController.kt:63-65`。
- Request DTO：`ComposeTemplatePreviewDraftRequest`（`MailComposeTemplateService.kt:703-713`），字段 `subject / subjectVariants / blocks / variantIndex / orcidId / expertEmail / contactId / senderAccountCode / strictPlaceholders`。**`contactId` 字段早已存在，本计划无需改 DTO。**
- Result DTO：`ComposeTemplatePreviewDraftResult`（`:715-723`）。
- **前端调用点（grep `preview-draft` 全集，2 处）**
  1. `app.js:8107 renderExpertMailPreview` —— 专家详情邮件预览标签页。当前 payload：`contactId: null`（`:8100`）、`expertEmail: null`（`:8101`）、`senderAccountCode: null`（`:8102`）。**本计划改这里。**
  2. `app.js:8365 renderServerComposeTemplatePreview` —— 模板编辑器抽屉。payload 取自 `collectComposeTemplatePreviewContext()`（`app.js:8296-8315`），`senderAccountCode` 来自 `#previewComposeAccountInput`。**本计划不改。**

### `state.contacts` 中的绑定字段

- 两条查询路径均已映射 `boundSenderAccountCode` 与 `contactId`：
  - DB 路径 `app.js:4606-4630`（`contactId: c.id` `:4612`，`boundSenderAccountCode` `:4620`）
  - ES 路径 `app.js:4657-4681`（`contactId: e.contactId` `:4663`，`boundSenderAccountCode` `:4671`）
- 即 K-contact-list-dual-path-field-parity 要求的双路径一致性**已满足**，本计划无需补 DTO。
- 但注意：`state.contacts` 中的 `contactId` 对**从未联系过**的专家为 `null`（ES 路径 `e.contactId` 可空），这正是 I-3 存在的原因。

## 实现方案

### T1 —— 后端：`resolvePreviewAccount` 增加绑定兜底（I-1 / I-5 / I-6 / I-7）

文件：`src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt`

改动 1：`previewDraft` 内调用点（当前 `:226-227`）

```kotlin
        val contact = resolvePreviewContact(request.contactId, request.orcidId, request.expertEmail)
        val account = resolvePreviewAccount(request.senderAccountCode, contact)
```

改动 2：`resolvePreviewAccount` 本体（当前 `:301-308`）替换为：

```kotlin
    /**
     * 预览账号解析优先级（I-1）：显式请求值 > contact 已绑定账号 > null。
     * 刻意使用 getAccount 而非 getEnabledAccount（I-5）：enabled=false 的现行语义是
     * “禁止自动外发”，被绑定的禁用账号在预览中仍须能渲染出签名。
     */
    private fun resolvePreviewAccount(
        senderAccountCode: String?,
        contact: ExpertContact?
    ): MailSenderAccount? {
        val code = senderAccountCode?.trim()?.takeIf { it.isNotBlank() }
            ?: contact?.boundSenderAccountCode?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        return runCatching { mailSenderAccountService.getAccount(code) }.getOrNull()
    }
```

不新增 import（`ExpertContact` 已在 `:5` 导入，`MailSenderAccount` 已在 `:7` 导入）。不改构造器。

### T2 —— 前端：预览 payload 传入 contactId（I-2 / I-3 / I-4）

文件：`src/main/resources/static/app.js`

在 `renderExpertMailPreview`（`:8086`）内，`payload` 构造之前插入 contactId 解析，并把 `contactId: null`（`:8100`）替换为解析结果。`senderAccountCode` **保持 `null`**（M-1：前端不传账号码，由后端读绑定）。

```javascript
    const previewContact = (state.contacts || []).find((item) => item.orcidId === orcidId);
    const previewContactId = previewContact?.contactId ?? null;
```

payload 中：

```javascript
        contactId: previewContactId,
        expertEmail: null,
        senderAccountCode: null,
```

不得在此函数内新增 `api()` 调用（I-2）。

### T3 —— 前端测试

文件：`src/test/js/expertMailPreviewTab.test.js`

新增 3 个用例（现有 10 个用例一字不改）：

1. `renderExpertMailPreview 从 state.contacts 取 contactId 传入 payload (I-3)` —— `sandbox.state.contacts = [{orcidId:"0000-0001", contactId: 42}]`，断言 `captured.contactId === 42`。
2. `state.contacts 无匹配 orcid 时 contactId 传 null (I-3)` —— `state.contacts = []`，断言 `captured.contactId === null`。
3. `解析 contactId 不引入额外请求 (I-2)` —— 复用现有 `calls` 计数写法，`state.contacts` 非空时仍断言 `calls.length === 1` 且 `captured.senderAccountCode === null`。

⚠ 现有 `createSandbox()` 是否已提供 `state.contacts`，落地时先确认；若无则在这 3 个用例内显式赋值，**不得修改 `createSandbox` 的默认值**（会影响其余 10 个用例）。

### T4 —— 后端测试

文件：`src/test/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateServiceTest.kt`

新增 3 个 `@Test`（现有用例一字不改，构造器调用 `:44-54` 保持 9 参）：

1. `previewDraft falls back to the contact bound sender account when no explicit code` —— stub `expertContactRepository.findById(42)` 返回带 `boundSenderAccountCode = "LiLei"` 的 contact，stub `mailSenderAccountService.getAccount("LiLei")` 返回账号，断言 `renderPreview` 被以该账号调用（`Mockito.verify`）。
2. `previewDraft prefers the explicit sender account over the contact binding` —— 同时给显式 `senderAccountCode = "WangFang"` 与绑定 `"LiLei"`，断言用的是 `WangFang`。
3. `previewDraft resolves a null account when the contact has no binding` —— 绑定为 null，断言 `renderPreview` 以 `null` 账号被调用（与现有 `:901` 用例的 `renderPreview(..., null, ...)` 形状一致）。

## 变更文件清单

| # | 文件 | 改动 | 子系统 |
|---|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt` | `resolvePreviewAccount` 增参 + 绑定兜底；`previewDraft` 调用点传 contact | 后端 template |
| 2 | `src/main/resources/static/app.js` | `renderExpertMailPreview` payload 传 contactId | 前端 static |
| 3 | `src/test/js/expertMailPreviewTab.test.js` | +3 用例 | 前端 static |
| 4 | `src/test/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateServiceTest.kt` | +3 用例 | 后端 template |

文件数 4 ≤ 10 ✓　子系统数 2 ≤ 2 ✓　新增共享存储字段 0 ✓

**本计划无前端样式改动**（不新增/修改任何 class、不改 DOM 结构），故不含 `## 样式契约`。

## 验证命令

> **前提**：JS 用例不需要 JDK；后端用例**必须 JDK 11（zulu-11）**，裸 `mvn` 会构建失败（`CLAUDE.md:7`）。

```bash
# 本计划相关的前端用例（快速迭代）
node --test src/test/js/expertMailPreviewTab.test.js

# app.js 语法检查
node --check src/main/resources/static/app.js

# 本计划相关的后端用例（快速迭代）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=MailComposeTemplateServiceTest

# 全量 JS 用例（前端回归门禁）
node --test src/test/js/*.test.js

# 全量回归
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 空白/换行卫生
git diff --check
```

**通过判据**

- `node --test src/test/js/expertMailPreviewTab.test.js`：退出码 0，`# fail 0`，`# tests` 从基线 **13** 增至 **16**（基线 2026-08-14 实测）。
- `node --check`：退出码 0，无输出。
- `mvn test -Dtest=MailComposeTemplateServiceTest`：`Tests run: N, Failures: 0, Errors: 0`。
- 全量 `mvn test`：`Failures: 0, Errors: 0`。
- `git diff --check`：无输出。

**来源**：JS 命令来自 `pom.xml:188-203` 并于 2026-08-14 在本仓库实测通过（`node v22.22.3`）；Maven 命令逐字引自 `CLAUDE.md:5-30`（JDK 路径未在研究环境实测，见主计划说明）。

## 验收标准

- **I-1**：`MailComposeTemplateServiceTest` 三个新用例分别覆盖「仅绑定」「显式优先」「无绑定→null」三条分支，各自 `Mockito.verify(mailVariableService).renderPreview(anyText, <期望账号>, contact)` 断言成立。
- **I-2**：`expertMailPreviewTab.test.js:198` 既有用例保持通过；新增用例在 `state.contacts` 非空时同样断言 `calls.length === 1`。
- **I-3**：新增两个用例分别断言 `captured.contactId === 42` 与 `captured.contactId === null`。
- **I-4**：`grep -n "expertEmail" src/main/resources/static/app.js` 在 `renderExpertMailPreview` 函数体内应仍为 `expertEmail: null`（未被改成传值）。
- **I-5**：`grep -n "getEnabledAccount" src/main/kotlin/com/weibo/talentintroduction/template/service/MailComposeTemplateService.kt` 应无输出。
- **I-6**：`MailComposeTemplateServiceTest.kt:44-54` 的构造调用行数与参数个数与改动前逐字一致（`git diff` 该区间应为空）。
- **I-7**：`git diff` 中 `MailComposeTemplateService.kt` 不含任何新增的 `renderText(` 调用；`previewDraft` 内 `resolveBlocks(..., renderVariables = false)` 参数未变。
- **MNC（仅机器可验）**：`git diff` 中 `MailComposeTemplateService.kt` 的 `fun preview(id: Long)` 方法体（当前 `:187-197`）为空 diff；`git diff` 中 `renderServerComposeTemplatePreview`（`app.js:8347-8380`）为空 diff。
- **回归**：执行「验证命令」节的全量 JS 用例与全量 `mvn test`，均达到该节判据。

## 人工验收清单

### A-1: 已绑定专家的预览出现真实签名（覆盖 需求 1 / 2，I-1）

- 前置条件：选一位 `expert_contact.bound_sender_account_code` 非空的专家（可在「专家联系」列表项副行看到「账号：XXX」）；确认 `mail_sender_account` 中该账号的 `sender_name` / `sender_title` / `team_name` 有值；确认「邮件模板 → 组装模板」中启用的模板正文含 `${senderName}` 之类占位符。
- 操作步骤：
  1. 进入「专家联系」，点开该专家。
  2. 切到「邮件预览」标签页。
  3. 阅读正文尾部签名区。
- 预期结果：签名处显示该账号的实际 `senderName` 文本（例如账号 `LiLei` 的 sender_name 是「李雷」，则正文出现「李雷」），而非空白或 `${senderName}` 字面量；面板底部不再出现 `兜底: senderName` / `兜底: senderEmail` / `兜底: senderTitle` / `兜底: teamName` / `兜底: countryName` 徽标。
- 覆盖：需求描述 可观察结果 1、2；I-1

### A-2: 收件人显示真实邮箱（覆盖 需求 3，I-3/I-4）

- 前置条件：同 A-1。
- 操作步骤：同 A-1 步骤 1-2，查看面板底部「收件人:」一行。
- 预期结果：显示该专家的真实邮箱（与页面顶部头像行下方的邮箱**逐字相同**），不再是 `preview@local`。
- 覆盖：需求描述 可观察结果 3；I-3、I-4

### A-3: 未绑定专家不报错（回归，覆盖 must-NOT-change 3）

- 前置条件：找一位从未发过信、`bound_sender_account_code` 为 NULL 的专家（列表副行显示「账号：未绑定」）。
- 操作步骤：点开该专家 → 切到「邮件预览」标签页。
- 预期结果：面板正常渲染出主题与正文，签名处为空白（与改动前一致）；面板底部出现 `兜底: senderName` 等徽标；页面不出现红色错误提示，正文**不是**满屏 `${}` 原始模板文本。
- 覆盖：must-NOT-change 3；I-3

### A-4: 模板编辑器抽屉的显式账号选择仍优先（回归，覆盖 must-NOT-change 2 + I-1）

- 前置条件：至少两个启用的发件账号 A、B，且 A ≠ 某专家的绑定账号。
- 操作步骤：
  1. 「邮件模板 → 组装模板」打开任一模板编辑器。
  2. 预览抽屉里「专家」选一位已绑定账号 B 的专家，「账号」显式选 A。
  3. 查看预览正文签名。
- 预期结果：签名显示的是账号 **A** 的信息，不是绑定账号 B。
- 覆盖：must-NOT-change 2；I-1

### A-5: 绑定到已禁用账号时预览仍出签名（覆盖 I-5）

- 前置条件：把某个已绑定专家的绑定账号在「邮箱账号」页设为停用（`enabled=false`）。
- 操作步骤：点开该专家 →「邮件预览」标签页。
- 预期结果：签名区**仍然显示**该禁用账号的 sender 信息（不是空白）。
- 覆盖：I-5
- 备注：验收后请把该账号恢复启用。

### A-6: 改绑后重新进入详情，预览随之改变（跨路径，覆盖 IP-1）

- 前置条件：两个启用账号 A、B；一位已绑定 A 的专家，且 A、B 的 `sender_name` 不同。
- 操作步骤：
  1. 点开该专家，「邮件预览」标签页记下签名中的 sender_name（应为 A 的）。
  2. 在详情页「绑定发件账号」卡片把账号改为 B，点「保存」。
  3. 从列表重新点开该专家，再切到「邮件预览」标签页。
- 预期结果：签名中的 sender_name 变为 **B** 的。
- 覆盖：现状审计 IP-1（写路径 2 `rebind` → 新增读路径 7）
- 备注：本计划**不要求**步骤 2 之后面板自动刷新（那是 P2 的 I-7）；必须重新进入详情才看得到变化，这是 P1 的预期行为。

### A-7: 首封发出后自动补绑，预览随之出现签名（跨路径，覆盖 IP-2）

- 前置条件：一位 `bound_sender_account_code` 为 NULL 的专家。
- 操作步骤：
  1. 点开该专家，「邮件预览」标签页确认签名为空。
  2. 用顶部「手动发送邮件」发出一封介绍邮件。
  3. 从列表重新点开该专家 →「邮件预览」标签页。
- 预期结果：签名区出现内容，且其 sender_name 与列表副行新出现的「账号：XXX」一致。
- 覆盖：现状审计 IP-2（写路径 1 `bindIfAbsent` → 新增读路径 7）

### A-8: 预览账号与发送账号一致（跨路径，覆盖 IP-3 / M-2）

- 前置条件：一位已绑定账号 A 的专家。
- 操作步骤：
  1. 「邮件预览」标签页记下签名中的 sender_name。
  2. 顶部「手动发送邮件」发出一封。
  3. 到「收发件箱」找到这封外发记录，查看其发件账号。
- 预期结果：外发记录的发件账号 = 步骤 1 签名对应的账号 A。
- 覆盖：现状审计 IP-3；共享不变量 M-2

### A-9: 四个子标签未受影响（回归，覆盖 must-NOT-change 3）

- 前置条件：任一已联系过的专家。
- 操作步骤：点开该专家，依次点击「学术档案」「联系详情」「模板预览」「邮件预览」四个子标签。
- 预期结果：四个标签按此顺序排列、数量为 4；每次点击后对应面板出现内容，**不出现空白面板**；「模板预览」显示变量覆盖率，「邮件预览」显示主题 + 正文。
- 覆盖：must-NOT-change 3
