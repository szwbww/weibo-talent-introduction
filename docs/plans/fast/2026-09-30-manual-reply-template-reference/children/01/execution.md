# Child 01 执行报告 — 人工回复「引用邮件模板」

- 结论：**PLAN_CONFLICT**（实现已完成并通过本 child 新增/更新的全部用例；但计划要求的「全量 0 失败」与「只改 7 个文件」在现有工作树上互斥，见文末「阻塞：范围冲突」）。
- 执行者：ImplTemplateRef01（fast-p child 01 worker）。
- 目标 worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-30-manual-reply-template-reference`
- 分支：`fast/2026-09-30-manual-reply-template-reference`；执行前 HEAD：`78ff2c3`（child_base_sha `6974418` + 控制方两份 docs-only 提交）。
- 计划身份的 SHA-256（执行时读取的字节）：`47aa2d69319486c9abc1e406fea15c6484f0991306489779b02e1dccf9cdcfa1`
  （`docs/plans/2026-09-30/manual-reply-template-reference.md`，654 行）
- Brief SHA-256：`a32f64316ec5ef2a22dc99d06869a8b2144d3264bc15fd3a62de23d43fd7712e`
  （`docs/plans/fast/2026-09-30-manual-reply-template-reference/children/01/brief.md`）
- 计划在执行期间未变动；工作树身份未变动；未 push/merge/rebase/reset/amend。

## 任务完成情况（T-1..T-5）

| 任务 | 状态 | 文件 | 证据 |
| --- | --- | --- | --- |
| T-1 入口 / 弹框 / 生命周期 | IMPLEMENTED | `mailbox-chat.js`、`styles.css` | 工具栏 `mc-open-template-reference`（S-1 逐字按钮、位于材料与跟进之间、会议组件缺席也渲染）；原生 `<dialog>` + `showModal` + body portal + `cancel` 事件；打开/关闭接到 `teardownConversationSubViews`、`meetingCloseDisposeOnAccountScopeChange`、`retargetManual`、`openManageOverlay`、`openMaterialRequestDialog`、`openFollowUpDialog`、unmount（经 teardown）；`closeTemplateReferenceDialog` 只删自己的 `.reply-template-dialog` |
| T-2 只读加载 / 渲染 / 异常态 | IMPLEMENTED | `mailbox-chat.js` | 每次打开 `GET /api/compose-templates`（只留 `enabled === true`）→ 默认选首个 → `POST /api/compose-templates/preview-draft`（payload 见下）；形状校验失败进 list/preview 失败态；`fallbackKeys` / `variables` / `included=false` 转 UI 文本；正文唯一使用 `result.body`；重试只重试当前阶段 |
| T-3 原子填入 | IMPLEMENTED | `mailbox-chat.js` | `normalizeManualTextLineBreaks` + text node + `<br>`（无 class `<div>` 包裹），无 `innerHTML` 直插；默认追加 / 显式替换；默认不改主题、勾选才写预览主题；替换先置 `manual.qa = null`；随后 `handleManualComposeInput(editor)` + `saveConversationState()`；保留 `outboundAttachmentDraft` 与 `followUpAnchorMailRecordId`；发送路由未改 |
| T-4 行为与样式合同测试 | IMPLEMENTED | 4 个测试文件 | `mailboxChatBehavior.test.js` 新增 18 用例（复用既有 MiniDOM/bootChat/API 记录，默认路由增加两个只读端点）；material/meeting 两个集成用例更新顺序断言并新增互操作；新增 `mailboxTemplateReferenceStyle.test.js`（3 suites / 10 用例） |
| T-5 版本键与收尾 | IMPLEMENTED（键）/ 见冲突 | `index.html` | 11 个 `?v=` 全部改为 `20260930-manual-template-reference`（旧键 0 命中）；真实浏览器截图见「视觉证据」 |

### 预览 payload（实测逐字段）

```json
{"subject":"Template subject A","subjectSnippetId":5,
 "blocks":[{"blockOrder":1,"blockType":"CUSTOM_TEXT","refId":null,"customText":"第一段"},
           {"blockOrder":2,"blockType":"REPLY_SNIPPET","refId":3,"customText":null}],
 "contactId":1,"senderAccountCode":"acc1","strictPlaceholders":false,"variantIndex":0}
```

- 普通来信账号 = `instance.manual.targetAccountCode`（实测 `acc1`）。
- 有跟进锚点时 = `followupCandidates()` 中相同 id 的成功发件 `accountCode`（实测锚点 acc9 → payload `senderAccountCode: "acc9"`，未退回 acc1 / 数组首项）。
- 锚点失效（该发件后来被服务端判为 `FAILED`，不再是候选）时：不打开弹框、零请求、显示固定文案「无法确认当前跟进邮件的发件账号，请重新选择跟进邮件」。

## 实施范围（7 文件，无其它改动）

`git status --porcelain`（最终态）：

```
 M src/main/resources/static/index.html
 M src/main/resources/static/mailbox-chat.js
 M src/main/resources/static/styles.css
 M src/test/js/mailboxChatBehavior.test.js
 M src/test/js/materialRequestIntegration.test.js
 M src/test/js/meetingConfirmationIntegration.test.js
?? src/test/js/mailboxTemplateReferenceStyle.test.js        ← 新增（授权清单第 7 项）
```

- `mailbox-chat.css`、`meeting-confirmation.js`、`app.js`、Kotlin、SQL/迁移、`docs/mockups/**`、历史计划：均未改动（`mailbox-chat.css` 与 `docs/plans/2026-09-09/.../mailbox-chat.target.css` 仍字节一致，见新增样式测试断言）。
- 未新增依赖、未联网、未连 MySQL/ES、未发信、未部署。
- 实施开始前按 T-5 复核：`index.html` 为 11 个 `?v=20260929-bounce-alert`；`grep -F '20260929-bounce-alert' src/test` 无命中（无新增固定旧键的测试文件）。

## 与计划的偏差（均在本 child 授权文件内）

1. `styles.css` 的新增 CSS **插入在** `/* task-center-contract:start */` 之前，而非文件末尾。
   原因：现有 `src/test/js/taskActivityCenter.test.js:578`（未授权、必须保持绿）断言 `/* task-center-contract:end */` 之后**不得有任何内容**（`"S-0 must be the last thing in styles.css"`）。S-1/S-2 逐字块仍完整落盘、逐字包含断言成立；`styles.css` 末尾字节与 HEAD 一致（`git diff --check` exit 0，无新增 EOF 空行）。
2. 新增样式测试**不写死缓存键字面量**，改为从 `index.html` 派生键值，只断言「已替换旧键 / 11 项同值 / 键格式」。
   原因：现有 `src/test/js/taskActivityCenter.test.js:695-700` 断言「当前缓存键字面量只能出现在 index.html，脚本与测试都不得固化它」；把字面量写进新测试会让该未授权用例变红。计划 T-5 的意图（11 键统一改值、无残留旧键）仍被完整覆盖。
3. 计划「现状审计 §4」称现有 toolbar 精确顺序断言只有 material(815) / meeting(1423) 两处；实测还有第三处（未授权文件），构成下面的阻塞项。

## 红灯 → 绿灯证据

新增/更新的用例在实施前是红的：把 3 个产品文件临时恢复到 HEAD（`git checkout HEAD -- <3 files>`，备份 sha256 见下），跑 4 个目标文件：

```
/tmp/fastp01-red.log：exit=1，tests 158 / pass 128 / fail 30
代表性命中：
  AssertionError: 引用模板固定在回形针之后、跟进之前
  AssertionError: styles.css 必须逐字包含 S-1 块
  AssertionError: 按钮必须逐字存在于模板
  AssertionError: 每个 data-role 都必须在模板里存在
  AssertionError: 每个 data-action 都必须在实现里被处理
  AssertionError: 新增 class 必须已声明或为既有复用类
  AssertionError: 本次实施必须替换旧缓存键
  AssertionError: 工具栏顺序必须是 B/I/列表/链接/回形针/会议确认/材料索取/引用模板/跟进
```

恢复后同一命令：`exit=0，tests 158 / pass 158 / fail 0`。
三份产品文件恢复前后 sha256 一致：

```
mailbox-chat.js  f82db572ce447c84adfb2986e09f919ca84ea8176e23cbb88e6b11512c32a6bd  MATCH
styles.css       bf0c730078888cd7ce49966766a70a6a7f8e30487469293a4dce40e77f0036e1  MATCH
index.html       cfb1c4a8a25661d6024825f22a5eb87e61d248d3b15c740d0cb95802c6b91d97  MATCH
```

（注：`index.html`/`styles.css` 在此之后有一次计划内的 EOF/位置修正，最终字节如上表 “MATCH” 记录于修正前；`git diff --check` 在最终态 exit 0。）

## 命令证据（最终态，fresh）

| 命令 | 结果 | 证据 |
| --- | --- | --- |
| `node --check src/main/resources/static/mailbox-chat.js` | PASS（exit 0） | 无输出 |
| `node --test <9 个目标文件>` | **FAIL（exit 1）**：tests 234 / suites 50 / pass 233 / fail 1 | `/tmp/fastp01-targeted-final.log`；唯一失败 `mailboxOutboundAttachments.test.js:1559`（见阻塞项） |
| `node --test src/test/js/*.test.js` | **FAIL（exit 1）**：tests 1265 / suites 249 / pass 1264 / fail 1 / skipped 0 | `/tmp/fastp01-full-final.log`；基线 1233 pass / 0 fail，本 child 新增 32 用例全绿 |
| `git diff --check` | PASS（exit 0） | 无空白错误 |

其中目标文件中除 `mailboxOutboundAttachments.test.js` 外 8 个文件全部 0 失败（含新增 style 用例 10/10、behavior 101/101、material 15/15、meeting 32/32）。

基线红/绿对照（`children/01/baseline.md`）：基线 1233 pass、0 fail；本 child 未修改任何范围外文件去消除基线失败，也未新增范围外文件。

## 视觉证据（真实浏览器，不是 mockup）

本地静态服务 `python3 -m http.server 8177`（cwd = `src/main/resources/static`，进程名 `fastp01-static`，已停止）后打开**真实 `index.html`**（加载的正是 `styles.css?v=20260930-manual-template-reference` 与 `mailbox-chat.js?v=20260930-manual-template-reference`）。

伪数据与登录网关说明：本机无后端，页面按真实流程会停在登录遮罩（`.app-shell` 被 `display:none`）。为看到真实人工回复区，仅做了两件**页内**动作（不写入仓库、不改产品代码）：把 `.app-shell`/`#view-mailbox` 设为可见、把 `#loginOverlay` 隐藏；`window.api` 换成固定响应的 stub（模板列表含 2 个启用 + 1 个停用、预览含一段停用片段与一个默认值变量）。渲染管线、CSS、组件代码全部是仓库真实产物。

| 文件 | 内容 |
| --- | --- |
| `/tmp/fastp01-dialog-1280.png` | 1280×900：真实渲染的引用模板弹框（宽 1010px、居中 x=135/y=80、左边栏 252px、正文 12px、不透明白底、左侧仅两个启用模板、右侧预览正文与「预览已生成」、warning 区显示「未包含正文块：2. 停用片段（片段已禁用）/ 使用默认值：expertFamilyName」、footer「同时替换回复主题」+ 取消/填入回复） |
| `/tmp/fastp01-compose-filled.png` | 填入后的人工回复区：工具条逐个为 B / I / 列表 / 链接 / 回形针 / 会议确认 / 材料索取 / **引用模板** / ↗ 跟进邮件（S-1 顺序与浅蓝 `#eef4ff` 触发按钮）；主题保持 `Re: Question 1`；正文为模板全文（空行保留），正文获得焦点 |
| `/tmp/fastp01-dialog-740.png` | 740px 宽：弹框 716px、`grid-template-columns: 714px`（单列）、badge 被媒体规则隐藏（S-2 窄屏合同） |
| `/tmp/fastp01-toolbar.png` | 工具条区域局部截图 |
| `/tmp/fastp01-applied-1280.png` | 填入后整页截图 |

浏览器实测（与断言一致）：`data-role` 节点 16 个全部存在；`aria-busy`/禁用态正确；应用后弹框关闭、`document.activeElement` 为正文；编辑器子节点为单个 `DIV`（内含 5 个 `<br>`），`innerText` 逐字含空行；网络记录只有 `GET /api/compose-templates` 与 `POST /api/compose-templates/preview-draft` 两个只读请求。

**未执行**：人工验收 A-1～A-12（本 run 之外），也未发信、未连测试环境。

## 阻塞：范围冲突（PLAN_CONFLICT）

计划与本 child 验收标准存在两处互斥要求，且需要的文件不在「Authorized Files」7 项内：

### 冲突事实

- `src/test/js/mailboxOutboundAttachments.test.js:1558-1562`（`fast-p 07 · I-1/S-1` 用例）用 `deepStrictEqual` 钉死了工具栏的**完整动作序列**：

  ```js
  ["mc-rich-command" ×4, "mc-upload-attachment", "mc-open-meeting",
   "mc-open-material-request", "mc-open-followup"]
  ```

  S-1 要求在该序列的材料索取与跟进之间插入 `mc-open-template-reference`，插入后实际为 9 项 → 该断言必然失败（实测 actual 9 项 / expected 8 项，`mailboxOutboundAttachments.test.js:1559`）。
- 该文件**不在**本 child 的授权文件清单（7 项）内；brief 全局约束 1/5 与验收「git status shows no other product/test changes」禁止我修改它，也禁止我自行修订计划文件清单。
- 判定依据（`docs/plans/**` 之外的仓库规则）：`CLAUDE.md` 的 `K-ui-removal-retires-obsolete-contract-tests` / `K-plan-quantified-claims-need-grep-receipts` —— 直接断言旧 DOM/动作序列的契约测试必须随改动同步更新，而计划「现状审计 §4」的「现有 toolbar 精确顺序断言」只列了 2 个文件（material:815、meeting:1423），漏了这第 3 处。

### 需要的唯一最小修订（待人工批准）

1. 「Authorized Files」表新增第 8 项：`src/test/js/mailboxOutboundAttachments.test.js`（仍 ≤ 计划上限 10）。
2. 该文件只改 `:1558-1562` 一处顺序断言，把 `"mc-open-template-reference"` 加在 `"mc-open-material-request"` 与 `"mc-open-followup"` 之间（与 T-4 对其余两个集成文件的要求同款：只加位置断言、不删原断言）。
3. 批准后剩余工作量为该 1 行修改 + 重跑 4 条命令；其余实现与测试已就绪（见本报告）。

未获批准前我**没有**触碰该文件，因此 `node --test src/test/js/*.test.js` 与目标命令集均为 `fail 1`，本 child 不能报 `READY_FOR_VERIFICATION`。

## 未做事项 / 供后续验证者

- 未修改 `docs/plans/**`；本报告留在工作树、未进入实现提交。
- 未执行人工验收 A-1～A-12（含审计/发信/真实模板数据核对），未连测试环境，未发信。
- 未启动生产、未重跑迁移（本 child 无 Kotlin/SQL 改动）。
- 文件系统上唯一未提交产物：本报告 `docs/plans/fast/2026-09-30-manual-reply-template-reference/children/01/execution.md`。

## 提交与提交后复跑（最终态）

- 实现提交：`2df9170 feat(fast-p): implement 01`（HEAD，仅含授权 7 文件；`git show --name-only HEAD | grep docs/` = 0 命中）。
- 提交后 fresh 复跑（与提交前逐条一致）：

| 命令 | 结果 |
| --- | --- |
| `node --check src/main/resources/static/mailbox-chat.js` | exit 0 |
| `node --test <9 个目标文件>` | exit 1，tests 234 / pass 233 / fail 1（`/tmp/fastp01-targeted-postcommit.log`） |
| `node --test src/test/js/*.test.js` | exit 1，tests 1265 / pass 1264 / fail 1（`/tmp/fastp01-full-postcommit.log`） |
| `git diff --check` | exit 0 |

- 提交后工作树只剩本报告：` M docs/plans/fast/2026-09-30-manual-reply-template-reference/children/01/execution.md`。
- `pom.xml:196` 的 JS 门禁是 glob（`node --test src/test/js/*.test.js`），新增用例会被 `mvn test` 自动纳入，无需改 pom；因此若走 Maven 全量门禁，会看到同一个 `mailboxOutboundAttachments.test.js:1559` 失败（未按 brief 要求运行 Maven，仅静态核对该配置）。
