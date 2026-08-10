# Fast-P Child Brief — p5 (sender-binding-05-frontend-visibility)

> 唯一权威契约 = `docs/plans/2026-08-10/sender-binding-05-frontend-visibility.md`（已含 A8 修订：变更文件清单 9 行）。
> 本 brief 只承载主计划级约束与跨子计划接口，不重述子计划正文；以子计划为准。
> P1/P2/P3/P4 已落地；上游接口见下。**本计划是批次最后一环。**

## 上游交付（P1..P4，已验）

- `expert_contact.bound_sender_account_code` / `sender_account_bound_at`（V85）、`sender_account_changed` / `sender_account_changed_at`（V86）
- `ExpertContactRepository.countBindingsByAccount(): List<AccountBindingCount>`（P3，单次 GROUP BY，已排除 NULL/空串/SIMULATOR_NOOP）
- 换绑/清标/迁移端点（P4）：`POST /api/expert-contacts/{contactId}/sender-account`、`POST /api/expert-contacts/{contactId}/sender-account/clear-change-mark`、`POST /api/expert-contacts/sender-account/migrate`；异常已映射 400 + 可读 message（I-6 原文透传）
- `ExpertContactResponse`（P4 未加绑定字段 —— **本计划负责加**）

## 全局约束（主计划，违反即 LIGHT_FAIL）

- **M-1**：本批次严格串行；你是唯一 writer（最后一份）。
- **M-2 方法级所有权（P5 范围，排他）**：
  - `expert/controller/ExpertIndexController.kt` — P5 只改 `ExpertIndexResponse` + `from(...)`
  - `campaign/controller/ExpertContactManagementController.kt` — P5 只改 `ExpertContactResponse` 加 2 字段 + 映射（**不碰** P4 的端点/请求体）
  - `mail/controller/MailSenderAccountController.kt` — P5 只改 DTO 加 `boundExpertCount` + 8 处 `toResponse` 调用
  - `mail/service/MailSenderAccountService.kt` — P5 只加公有方法 `bindingCountsByAccount()`（**不碰** `isManualSendable` 等 P2 内容）
  - `static/app.js` / `static/index.html` / `static/styles.css` — **P5 独占**（本批次唯一前端计划）
  - 测试：`test/js/senderBindingDisplay.test.js` 新建 6 例 + `MailSenderAccountServiceTest`（A8：仅 5 处构造实参 +1）
  - 发现需要改本计划名下之外的方法/字段 → 停止并上报
- **M-4**：`MailSenderAccountServiceTest.kt:35-46`（`selects account at daily limit`）与 `:48-57`（`includes auto-paused accounts`）**逐字不变**（A8 只授权构造实参修改）。
- **M-6**：本计划迁移：无。**M-8**：不做五件事 —— 尤其：ES 索引不写绑定字段（I-2）；DB 路径 tags 恒空缺陷**不修**（只保证不重蹈，I-1）；「已变更」筛选不做。
- **M-7 知识写回（P5 Phase 6）**：**新建** `docs/knowledge/frontend/K-contact-list-dual-path-field-parity.md`，记录"列表新字段必须同时进两条路径 DTO（`tags` 已踩过）"。
- **G-2**：NULL = 未绑定（前端显示「未绑定」）。**G-3**：下拉排除 `SIMULATOR_NOOP`（I-5）。

## 变更文件清单（P5 的 10 个授权文件；A8/A10 修订新增第 9/10 项）

1. `src/main/kotlin/com/weibo/talentintroduction/expert/controller/ExpertIndexController.kt`
2. `src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt`
3. `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailSenderAccountController.kt`
4. `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountService.kt`
5. `src/main/resources/static/app.js`
6. `src/main/resources/static/index.html`
7. `src/main/resources/static/styles.css`
8. `src/test/js/senderBindingDisplay.test.js`（新增 6 例）
9. `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountServiceTest.kt`（**A8 修订授权，仅编译修复**：5 处位置传参构造 `:25/:681/:717/:752/:792` 追加 1 个 `Mockito.mock(ExpertContactRepository::class.java)` 实参；M-4 锁定测试逐字不变）
10. `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailSenderAccountContextTest.kt`（**A10 修订授权，仅装配修复**：ApplicationContextRunner 补 1 个 `.withBean(ExpertContactRepository::class.java, Supplier { Mockito.mock(...) })` 注册；不改断言）

知识写回：新建 `docs/knowledge/frontend/K-contact-list-dual-path-field-parity.md`（Phase 6）。

## 关键实现要点（详见计划正文 + 样式契约 S-1..S-4）

- **I-1**：绑定字段必须**同时**进 `ExpertIndexResponse`（ES 路径）与 `ExpertContactResponse`（DB 路径）；`app.js` `loadContacts()` 两个 map 分支（DB `~:4595-4615`、ES `~:4620-4665`）都要取值。
- **I-2**：ES 路径的值来自 `contactMap` join（`contact?.boundSenderAccountCode` / `contact?.senderAccountChanged ?: false`），**禁止**读 `expert.xxx`；`git diff` 不含 `expert.boundSenderAccountCode`、不含对 `ExpertProfile` 的改动。
- **I-3**：`listAccounts()` 先取一次 `bindingCountsByAccount()` Map，再逐账号 lookup；`toResponse` 方法体内**不得**查库；其余 7 个单账号端点传 `counts[code] ?: 0L`（各自取一次 Map）。
- **I-4**：详情卡片值取自 `detail.contact.*`，**禁止** `state.contacts.find(...)`；两个 action 分支都以 `await loadContactDetail(id)` 结尾（先于 `loadContacts()`）。
- **I-5**：下拉 `.filter(a => a.enabled && a.accountCode !== "SIMULATOR_NOOP")`。
- **I-6**：两个 action 分支**无** try/catch、无自造失败文案（错误经既有 `showStatus(error.message, "error")` 透传）。
- **S-1**：`.expert-tag.tag-sender-changed` 规则块逐字（三条颜色属性）；`.expert-tag` / `.expert-tag.tag-discovered` 既有规则块零改动；禁止 inline style、禁止 `.badge` 系列替代。
- **S-2**：列表绑定文本为裸 `<span>`（无 class/style）；`expert-row-sub` 条件表达式必须扩容含 `bindingText`。
- **S-3**：`.metadata-card-value .sender-binding-editor` 两条规则逐字；`.metadata-card*` 既有规则块零改动；SVG 图标与 `app.js:7011-7014` 同规格。
- **S-4**：`index.html` 表头与 `app.js` 行模板**同改**（6 列 → 7 列），`<th>绑定专家数</th>` 与 `<td>${account.boundExpertCount ?? 0}</td>` 位置按计划。
- JS 测试参照 `loadContactsFilter.test.js` 的 `vm` 抽取 + DOM stub 范式；stub 必须覆盖 `#senderBindingSelect`；6 用例按 T5.1 表。

## 必须验证的命令（JDK 11 zulu；裸 mvn 会失败）

```bash
node --test src/test/js/senderBindingDisplay.test.js
node --test src/test/js/*.test.js
node --check src/main/resources/static/app.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package
git diff --check
```

> 通过判据：`node --test` 输出 `# fail 0`；`mvn` 输出 `Tests run: N, Failures: 0, Errors: 0` 与 `BUILD SUCCESS`；`node --check` 无输出且退出码 0。
> 基线：P4 后 2276 tests / 0 F / 0 E / 4 skipped、node 479/0。`verify.sh` **不可**作为前端门禁（只跑单个文件）。

## 产物与提交

- 实现 commit 消息：`feat(fast-p): implement p5`
- 完整执行结果追加到 `docs/plans/fast/sender-binding/children/p5/execution.md`
- 只提交实现文件 + execution.md；**不提交** ledger/verify-log/fix-log。
- 返回格式：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT` + commit SHA + 命令摘要 + report 路径。
