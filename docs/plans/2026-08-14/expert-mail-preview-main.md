# MAIN：片段命名 + 专家详情邮件预览（主计划）

> 主计划本身**不产生代码改动**。它约束两个子计划的边界、顺序、共享不变量与共享验证口径。
> 子计划：
> - P1 `expert-mail-preview-p1-snippet-name.md`
> - P2 `expert-mail-preview-p2-detail-tab.md`
>
> 任何一个子计划的执行 agent 必须先读本文件，再读自己的子计划。

---

## 需求描述

**Observable outcome**

1. 在邮件模板编辑器里选择"回复片段"内容块时，下拉里显示的是可辨认的片段名称，而不是 `尊语 #10`。
2. 在专家列表里选中某位专家后，详情区多出一个"邮件预览"标签页；选择一个邮件模板即可看到**这位专家**实际会收到的标题与正文。
3. 该标签页上有一个按钮，点击后跳到邮件模板页、打开该模板的编辑弹窗，并已自动把当前专家选为预览对象，可直接改模板看效果。

**What must NOT change**

| # | 必须保持的既有行为 | 证据锚点 |
|---|---|---|
| N-1 | 回复 frame（尊语/开场白/致谢/结束语）的选择、解析、fail-closed 校验与 version 计算 | `ReplySnippetService.kt:58-76, 100-113, 122-140, 147-174` |
| N-2 | 信任回复工作台的 frame 下拉仍按 `option.content` 全文显示 | `trust-reply-workbench.js:1245-1252` |
| N-3 | 模板编辑器内的服务端预览行为（含 variantIndex 轮换、strict 占位符开关） | `app.js:8178-8210`、`MailComposeTemplateService.previewDraft()` |
| N-4 | 专家详情既有三个标签页"学术档案 / 联系详情 / 模板预览"的内容与懒加载行为 | `app.js:6486-6491, 6547-6564, 6565-6598` |
| N-5 | 内容变体编辑器的读取契约（每个变体各有一个常驻 `.content-variant-input`） | K-content-variant-input-read-contract（**该条目记录的行号已过期**，实测为 `renderContentVariantRows` `app.js:7744`、`updateContentVariantsCountBadge` `:7829`、`collectContentVariants` `:7843`、`validateContentVariantInputs` `:7858`） |
| N-6 | 片段内容占位符校验（create/update 必调 `requireValidPlaceholders`） | `ReplySnippetService.kt:187, 225` |

**Out of scope（明确推迟，不得在本轮夹带）**

- `ReplyFrameOption`（`ReplySnippetService.kt:342-348`）不加 `name`。工作台下拉显示的是 `option.content` 全文，本来就不模糊（N-2），加了只会多一条 DTO 链路。
- 不改 `GET /api/compose-templates/{id}/preview`（`MailComposeTemplateController.kt:59-61`）。P2 走 `preview-draft`，该端点保持原样。
- 不动 QA 规则的 `displayName` 语义、不动 `variantGroup`、不动主题变体。
- 不在本轮做"从邮件预览直接发送测试邮件"。
- 片段名称不做唯一性约束、不做搜索/筛选。
- 不动 `docs/introduction-mail-template-v2.md` 里提到的签名可验证信息（已单列 backlog）。

---

## 拆分理由与顺序约束

| | P1 片段命名 | P2 专家详情邮件预览 |
|---|---|---|
| 子系统 | 后端 reply + template 模块 / 前端 | 仅前端 |
| 数据存储改动 | `reply_snippet` 新增 1 列 | 无 |
| 后端改动 | 有 | **零** |
| 变更文件数 | 10 | 5 |

**必须 P1 先于 P2。** 依赖是单向的、且是显示层依赖：

P2 的预览结果里会渲染内容块 pill，用的是后端返回的 `ComposeTemplatePreviewBlock.refDisplayName`
（`MailComposeTemplateService.kt:672`，前端消费点 `app.js:8248`）。该字段目前对片段块产出 `SALUTATION #10`
（`MailComposeTemplateService.kt:382-386`）。P1 修好它之后，P2 的预览面板一上来就是有意义的块名，不需要返工。

反向不成立：P1 不依赖 P2 的任何产物。

**两个子计划不得合并。** 合并后变更文件 15 个、跨 3 个子系统（DB 迁移 / 后端服务 / 前端），
超出 create-p 的硬上限（≤10 文件、≤2 子系统），且 `reply_snippet` 加列与新前端面板的验证周期完全无关。

---

## 共享不变量（两个子计划都必须遵守）

### Invariant M-1: 静态资源缓存键三处同值同时 bump
- Rule：`index.html` 的 `styles.css?v=`、`trust-reply-workbench.js?v=`、`app.js?v=` 三个键必须**同值**，且任一静态资源变更时**同时**改到新值；同时必须同步 `src/test/js/batchSendTaskConsoleVisualFix.test.js:37-39` 的三条硬编码字符串断言。
- Applies to：P1（改 `index.html` + `app.js`）、P2（改 `index.html` + `app.js` + `styles.css`）。两个子计划各自独立 bump 一次，不得共用一个键值。
- 当前值（实测）：`20260814-v8-expert-layout-default-01`，三处一致 —— `index.html:11`、`index.html:1969`、`index.html:1970`。
- Violation consequence：`src/test/js/trustReplyWorkbenchSharedMount.test.js:290-295` 断言三键相等、`batchSendTaskConsoleVisualFix.test.js:36-39` 断言三键等于具体字符串；只 bump 部分键 → 构建期 node 测试失败 → WAR 构建中止（2026-08-13 发布 eda4853 实测踩坑）。
- 来源: K-frontend-cache-key-triad

### Invariant M-2: 前端 JS 门禁只认 `node --test <目标文件>`
- Rule：前端改动的权威回归门禁是对目标测试文件单跑 `node --test`；`verify.sh` **只跑 `normalizeDiscoveryResultSummary.test.js` 一个文件**，不得当作门禁。`mvn test` 通过 `exec-maven-plugin`（`pom.xml:188-203`）带上全量 JS，作为全量回归另列。
- Applies to：P1、P2 的所有前端验收。
- Violation consequence：以 `verify.sh` 绿灯当作前端通过，实际改的用例根本没跑。
- 来源: K-js-test-invocation-surface

### Invariant M-3: 新增/改动的渲染函数必须核对宿主 DOM 真实存在
- Rule：任何"按 id / 选择器取元素再写入"的渲染函数，必须核对该选择器在**其真实宿主**中存在——宿主是 `index.html` 的写死结构就 grep `index.html`；宿主是 `app.js` 模板字符串生成的就 grep `app.js` 的生成处。测试里的 DOM stub 永远返回元素，绿灯不构成存在性证据。
- Applies to：P1 的片段表单新字段（宿主 = `index.html`）、P2 的预览面板（宿主 = `app.js` 生成的 `.detail-tab-panel`）。
- Violation consequence：函数在生产中因 `if (!el) return;` 静默短路，测试全绿但功能从不渲染。
- 来源: K-dom-stub-tests-hide-dangling-refs

### Invariant M-4: 计数与全称判断必须附 grep 回执
- Rule：子计划或其执行/验证过程中凡写出"共 N 处调用点""仅此一处""无其他写路径"，必须贴出 grep 命令与输出。通读文件形成的印象不算证据；`src/main` 单侧 grep 漏掉测试里的 Mockito stub 也不算。
- Applies to：P1 的"片段显示名共 2 处实现"、P2 的"两套详情面板"。
- 来源: K-plan-quantified-claims-need-grep-receipts

---

## 共享验证命令

> 本项目是 Kotlin + Spring Boot 2.7（Java 11）Maven 工程，**必须 JDK 11（zulu-11）**，裸 `mvn` 会构建失败。
> 两个子计划的 `## 验证命令` 均引用本节，不得就地重写简化版。

```bash
# 全量测试（回归门禁；含 exec-maven-plugin 绑定的全量 node --test）
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test

# 构建 WAR
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package

# 空白/换行卫生
git diff --check
```

通过判据：上述 `mvn test` 命令退出码 0 且输出含 `Tests run: N, Failures: 0, Errors: 0`；上述 `mvn clean package` 命令退出码 0 且产出 WAR；`git diff --check` 无输出。
来源：`CLAUDE.md:5-27`「Commands」章节 + `CLAUDE.md:134-138` 项目元信息。

子计划各自的单类/单文件命令见各自的 `## 验证命令` 节。

---

## 跨子计划的收尾检查

两个子计划都完成后，执行一次联合核对（任一不通过即回到对应子计划）：

- [ ] 同一个回复片段，在这三处显示的名字**逐字相同**：模板编辑器块下拉（`app.js:8059` 产出）、模板列表的块 pill（`app.js:8004-8006` 消费 `refDisplayName`）、P2 邮件预览面板的块说明（`app.js:8248` 消费 `refDisplayName`）。
- [ ] 从 P2 的"在模板编辑器中打开"跳过去后，编辑器块下拉里的片段名与跳转前预览面板里看到的块名一致。
- [ ] `index.html` 三个缓存键仍然同值（P2 的 bump 覆盖 P1 的 bump 属正常，但三者必须一致），且 `batchSendTaskConsoleVisualFix.test.js:37-39` 三条断言均为最终值。
- [ ] 执行本节「共享验证命令」的全量测试与构建，均通过。

---

## Phase 0 已加载知识（子计划继承，不再重复列出）

| K-id | 用法 |
|---|---|
| K-variant-pool-dto-chain | P1 的 DTO 七层贯通检查清单，直接作为 I-3 |
| K-spring-data-jdbc-null-default | P1 的 update 全列绑定行为，支撑 I-2 的"改名不动 frameVersion"论证 |
| K-flyway-placeholder-replacement | P1 迁移不得含 `${}`；`application.yml` 的 `placeholder-replacement: false` 必须保留 |
| K-dead-template-field-save-ignore | P1 的反向提醒：新字段必须**真的**保存，不能只加 DTO |
| K-preview-draft-raw-before-render | P2 的 I-5：变量替换只在 `MailVariableService.renderPreview()` 发生 |
| K-preview-mirrors-pipeline | P2 的 I-5：预览必须复用发送同源路径 |
| K-frontend-cache-key-triad | M-1 |
| K-js-test-invocation-surface | M-2 |
| K-dom-stub-tests-hide-dangling-refs | M-3 |
| K-plan-quantified-claims-need-grep-receipts | M-4 |
| K-content-variant-input-read-contract | P1 的 N-5：改片段表单不得破坏变体读取契约 |
| K-detail-es-backed-fields-need-authoritative-read | P2 的 I-6 参照：详情页不得把列表缓存当权威数据源 |
| K-mail-body-display-sites | P2 新增一处 `class="pre"` 正文展示点，须并入该知识条目的全集 |

**被主动否决的知识**：
- K-view-registration-triad（新增侧栏 Tab 的四处注册）不适用 —— P2 加的是**详情区子标签**，不是侧栏 view，`viewMeta` / `refreshCurrentView()` 均不涉及。已核对 `app.js:1619-1640` 的 `setView` 与 `app.js:511` 的 `viewMeta`，无需注册。
