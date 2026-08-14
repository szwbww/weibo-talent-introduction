# 专家详情头部改版 —— 主计划

> 本文件**无代码改动**，只定边界、顺序、共享不变量与共享验证命令。
> 实施走两份子计划：`expert-detail-head-p1-preview-sender-account.md` → `expert-detail-head-p2-head-layout-c.md`。

## 需求描述

**可观察结果**

1. 专家详情「邮件预览」标签页渲染出的正文，签名区使用该专家**已绑定的发件账号**信息（`senderName / senderEmail / senderTitle / teamName / countryName`），不再是空串。（P1）
2. 「绑定发件账号」从详情区的 metadata 卡片移到顶部操作栏，与「手动发送邮件」同一区域。（P2）
3. 操作栏改为「主行 + 折叠更多」：主行常驻账号 + 模板 + 发送，状态/层级/回复模式折进「更多」。（P2）
4. 「专家标签」从独立区块并入专家身份行右侧；ES 无画像时退化为一颗内联提示 pill。（P2）

**必须不变（must NOT change）**

- M-A 手动发送邮件的**实际发件账号解析结果**不变：仍由后端 `SenderAccountBindingService.resolveForSend(contact, manual = true)` 从 `expert_contact.bound_sender_account_code` 解析。
- M-B `renderExpertTagEditor` 在**收发件箱视图**（`app.js:9031`、`app.js:9600` 两处经 `renderMailboxExpertTagEditor` 调用）的输出逐字不变。
- M-C `GET /api/compose-templates/{id}/preview` 与模板编辑器抽屉预览（`app.js:8347 renderServerComposeTemplatePreview`）的现有行为不变。
- M-D 专家详情四个子标签的键名、顺序、`data-panel` 出现次数不变。

**Out of scope（本轮显式不做）**

- 方向 D（操作条下沉为 `#contactDetail` 内 sticky）。要动 `index.html:676` 的容器位置与 `app.js:11070 / 11142` 两个事件委托挂载点，回归面最大，另开计划。
- 发送时自动 rebind（把下拉当前值写回绑定）。理由见 M-1。
- `reply_snippet.updated_at` 停留在创建时刻的问题（见 [[plan-expert-mail-preview-2026-08-14]] 决策 4），本轮不修。
- 批量发送面板、收发件箱、模板编辑器抽屉的任何布局调整。
- `updateSaveButtonState`（`app.js:8799-8824`）对既有三个 select 使用**内联样式**标记脏态这一既有实现，本轮不重构。

## 顺序约束

**P1 必须先于 P2。**

依据：P2 的人工验收项 **A-9**（在账号浮层里改绑保存后，邮件预览标签页的签名随之改变）在 P1 之前没有任何可观察差异 —— 预览的 sender 变量恒为空串，改绑前后都一样，该项只能判为阻塞。

P1 本身不依赖 P2，可独立部署与验收。P2 除 A-9 外的其余部分也不依赖 P1。

## 共享不变量

### M-1: 发件账号的唯一权威是数据库绑定，不是前端选中值

- Rule: 任何链路（发送、预览、显示）确定"这封信用哪个账号发"时，权威值只有 `expert_contact.bound_sender_account_code`。前端下拉/浮层中**未保存**的选中值不得进入发送或预览的账号解析。
- Applies to:
  - `app.js` `handleContactAction` 的 `send-manual-mail` 分支（`app.js:8554-8571`）—— 继续传 `senderAccountCode: null`。
  - `app.js` `renderExpertMailPreview`（`app.js:8086-8142`）—— P1 后经 `contactId` 让后端读绑定，前端不传账号码。
  - `MailComposeTemplateService.resolvePreviewAccount`（`MailComposeTemplateService.kt:301-308`）。
- Violation consequence: `ManualExpertMailService.resolveAccount`（`ManualExpertMailService.kt:159-167`）的 I-3 不变量规定「显式 `senderAccountCode` 与 `bound` 都非空且不等 → `IllegalArgumentException`」。透传未保存值给发送接口，对已绑定专家 100% 抛异常。若只透传给预览而不透传给发送，则出现"预览显示 B 的签名、实际发出 A 的签名"——比签名全空更危险，因为它看起来是对的。
- 来源: original（证据：`ManualExpertMailService.kt:159-177` 逐字读取）

### M-2: 预览与发送必须同源

- Rule: 邮件预览的账号解析顺序必须与发送路径可推导地一致。发送走 `resolveForSend(contact)` 读绑定；预览在无显式账号时也必须回落到同一个 `contact.boundSenderAccountCode`。
- Applies to: `MailComposeTemplateService.previewDraft`（`:199-278`）、`resolvePreviewAccount`（`:301-308`）。
- Violation consequence: 预览成为一个只对自己负责的假象，运营据此判断"这封信长这样"，实际外发内容不同。
- 来源: K-preview-mirrors-pipeline（经 `K-compose-template-preview-endpoint-split` 引用）

### M-3: `renderExpertTagEditor` 的默认输出受逐字契约保护

- Rule: `renderExpertTagEditor(tags, orcidId, level, editorId, profileMissing)` 在**不传新增布局参数**时，输出必须与 `src/test/js/expertProfileAbsence.test.js:46-67` 的 `S1_EXPECTED` / `S2_EXPECTED` 归一化空白后**完全相等**。
- Applies to: `app.js:3964-3992` 函数本体；`app.js:4080 updateExpertTagEditor`（重渲染，**不传** `profileMissing`）；`app.js:4473-4478 renderMailboxExpertTagEditor`（收发件箱两处调用）。
- Violation consequence: `expertProfileAbsence.test.js:77` / `:93` / `:114` 三处 `assert.strictEqual(normalizeWhitespace(html), S*_EXPECTED)` 直接失败，且收发件箱专家概览的标签区样式失真（`styles.css:2132 / 2183 / 2246` 是针对 `.mail-expert-overview .expert-tag-editor` 的专门规则）。
- 来源: original（证据：逐字读取 `expertProfileAbsence.test.js:1-146`）

### M-4: 前端 JS 用例的权威门禁是 `node --test <file>`，不是 `verify.sh`

- Rule: 本仓库 `verify.sh:16` 只跑 `normalizeDiscoveryResultSummary.test.js` **一个文件**，不可用作前端计划的回归门禁。
- Applies to: 两份子计划的 `## 验证命令` 与 `## 验收标准`。
- Violation consequence: 以 `verify.sh` 为门禁会让本计划改动的 4 个测试文件全部不被执行，验证形同虚设。
- 来源: K-js-test-invocation-surface（本轮已重新 grep 验证：`verify.sh:16` 内容属实；`pom.xml:188-203` 的 `node --test src/test/js/*.test.js` 绑定在 `test` phase 属实）

## 共享验证命令

> **前提**：本仓库是 Kotlin + Spring Boot 2.7 / Java 11 Maven 工程，**必须用 JDK 11（zulu-11）**，裸 `mvn` 会构建失败（`CLAUDE.md:7`）。
> **前端 JS 用例与 Maven 无关**，可单独跑，不需要 JDK。

```bash
# ① 前端 JS 权威门禁 —— 本改版涉及的全部测试文件（已实测，node v22.22.3）
node --test \
  src/test/js/expertProfileAbsence.test.js \
  src/test/js/senderBindingDisplay.test.js \
  src/test/js/expertMailPreviewTab.test.js \
  src/test/js/composeTemplatePreview.test.js \
  src/test/js/contactHeadLayout.test.js

# ② app.js 语法检查（pom.xml:205-218 的 node-check-app 同款）
node --check src/main/resources/static/app.js

# ③ 全量 JS 用例
node --test src/test/js/*.test.js

# ④ 后端单测（P1 用）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn test -Dtest=MailComposeTemplateServiceTest

# ⑤ 全量回归
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# ⑥ 空白/换行卫生
git diff --check
```

**通过判据**

- ①②③：退出码 0；① 输出中 `# fail 0` 且 `# pass` ≥ 34（当前 4 个已存在文件的基线为 `# tests 34 / # pass 34 / # fail 0`，2026-08-14 实测）。
- ④⑤：`Tests run: N, Failures: 0, Errors: 0`。
- ⑥：无输出。

**来源**

- ①②③：`pom.xml:188-231` + `verify.sh` + **本环境实测**（2026-08-14，`node v22.22.3`，`node --test` 四文件通过：`# tests 34 # pass 34 # fail 0`）。
- ④⑤：`CLAUDE.md:5-30`「Commands」章节逐字引用。
  ⚠ **JDK 路径未在本环境实测** —— 研究用的 `device_bash` 是仅挂载了项目目录的 Linux 沙箱，`/Library/Java/JavaVirtualMachines/` 不可见。执行方首次运行 ④ 时若路径不符，以本机实际 zulu-11 路径为准并回写 `CLAUDE.md`。

## 知识使用与漂移修正

本轮 Phase 0 载入并**重新 grep 验证**的条目：

| 条目 | 结论 | 处置 |
|---|---|---|
| `K-expert-detail-two-panel-render-sites` | 结论成立，**行号已漂移** | 见下方修正 |
| `K-dom-stub-tests-hide-dangling-refs` | 成立，直接约束 P2 的测试写法 | 采用 |
| `K-contact-list-dual-path-field-parity` | 成立，`boundSenderAccountCode` 双路径均已补齐（`app.js:4620` / `:4671`） | 采用，本轮无需再补 |
| `K-js-test-invocation-surface` | 成立且已实测 | 采用为门禁 |
| `K-compose-template-preview-endpoint-split` | 成立 | 采用（P1） |
| `K-preview-draft-raw-before-render` | 成立 | 采用（P1 不得引入本地 `renderText`） |
| `K-compose-templates-state-scope` | 成立 | 采用（P1 的 `ensureComposeTemplatesLoaded` 已是幂等范式） |
| `K-detail-es-backed-fields-need-authoritative-read` | 成立 | 采用（P2 标签仍按 orcid+level 读 ES，不改） |
| `K-ui-removal-retires-obsolete-contract-tests` | 成立 | 采用（P2 必须同步改 3 个测试文件） |
| `K-sender-account-enabled-scope` | 成立 | 采用（P2 的未绑定/禁用态提示） |
| `K-qingfei-site-design-tokens-source` | **主动拒绝** | 该条针对公网页面（对齐官网 `site.css`），本改版是后台管理 UI，基准是本仓库 `styles.css` 的 `:root`（`styles.css:1-80`）。见 `K-public-page-not-admin-css`。 |

**`K-expert-detail-two-panel-render-sites` 行号漂移修正（本轮实测）**

| 条目原文行号 | 2026-08-14 实际行号 |
|---|---|
| `renderDetailSubTabs` `app.js:6486-6497` | `app.js:6499-6514` |
| `activateDetailSubTab` `app.js:6547-6564` | `app.js:6561-6583` |
| 懒加载守卫 `:6556-6562` | `:6571-6582` |
| `showExpertDetail()` `:6600` | `:6629` |
| `loadContactDetail()` `:6935` | `:6967` |
| `select-expert` 分流 `app.js:8366-8379` | `app.js:8536-8549` |
| `#contactDetail` click 委托 `app.js:10862-10870` | `app.js:11031-11047` |

条目的**结论**（一处定义、两处渲染；`activateDetailSubTab` 未命中不抛错；只监听 click，需要 change 时要另加委托）经本轮 grep 全部成立。P2 落地后须按 Phase 6 回写修正行号。

## 子计划清单

| 计划 | 范围 | 文件数 | 子系统数 |
|---|---|---|---|
| `expert-detail-head-p1-preview-sender-account.md` | 邮件预览注入绑定发件账号 | 4 | 2（前端 static / 后端 template） |
| `expert-detail-head-p2-head-layout-c.md` | 操作栏方向 C + 账号上移 + 方案 A 闸门 + 标签 C-1 | 4 | 1（前端 static） |

合计触及 8 个文件（`app.js` 在两份计划中都出现，实际去重后 7 个），单份计划均在 10 文件 / 2 子系统限制内。

**明确不需要改的文件（已逐行核对，避免执行方误伤）**

- `src/test/js/senderBindingDisplay.test.js` —— 6 个用例全部针对 `renderContactListItems`（列表副行）与 `loadAccounts`（账号表），无一条断言详情区 metadata 卡片。逐行依据见 P2 的 T11「不改」条目。
- `src/main/resources/static/index.html` —— `#contactHeadActions` 容器位置不变（方向 D 已列入 out of scope）。
- 所有 Kotlin 控制器与 DTO —— `ComposeTemplatePreviewDraftRequest` 的 `contactId` 字段早已存在（`MailComposeTemplateService.kt:710`）。
