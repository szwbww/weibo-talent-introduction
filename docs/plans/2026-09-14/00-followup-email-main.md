# 跟进邮件开发总计划

> 状态：待执行。本文件是两个子计划的唯一编排入口；只约束执行顺序、文件边界、验证门禁和发布状态，不授权额外功能。

## 需求描述

按两个顺序子计划交付收发件箱跟进邮件：运营在“会议确认”旁打开弹窗，人工选择真实成功发件，编辑短跟进正文并填入现有人工回复；最终人工发送必须引用所选邮件的真实线程。普通刷新必须取得本次新 CSS/JS。

必须保持：

- 子计划 01 的业务语义、正文、DOM、逐字 CSS、接口兼容和安全发送链不得由总计划改写。
- 子计划 02 只修改缓存键及对应固定值测试，不得修改功能代码或样式正文。
- 两个子计划禁止并行；子计划 02 必须从子计划 01 已验证的工作区继续。
- 两个子计划作为一个发布和回滚单元；子计划 01 单独完成时不得发布。
- 每个子计划只能修改自身“变更文件清单”；发现额外文件需求时先修订对应子计划和本总计划。

范围外：

- 不增加第三个功能阶段、数据库迁移、跟进状态、自动发送、定时提醒、批量跟进或 AI 生成。
- 不部署生产环境，不向真实专家发送验收邮件。
- 不在本文件复制或重新设计子计划已经冻结的实现与样式细节。

## 关键不变量

### Invariant I-1: 严格顺序执行
- Rule: 执行顺序固定为 01 功能实现与独立验证 → 02 缓存激活与独立验证 → 联合机器验证 → 人工验收。01 未通过时不得开始 02；02 必须读取 01 修改后的共享文件，不得从旧基线实现或机械覆盖。
- Applies to: 两个子计划、共享测试文件、联合验证。
- Violation consequence: 01 新增的样式断言被 02 覆盖，或生产页面加载到不完整功能。
- 来源: K-master-plan-shared-file-sequential-gates

### Invariant I-2: 子计划白名单是唯一代码边界
- Rule: 01 只允许其 9 个文件（A1 由 8 追加 `src/test/js/meetingConfirmationIntegration.test.js`）；02 只允许其 10 个文件。总计划不授权任何其他生产或测试文件。额外改动即停止执行并修订计划，不得使用模糊统称扩大范围。
- Applies to: 实现、机械修复、测试修复、代码审查。
- Violation consequence: 绕过 create-p 文件上限，混入未经审计的修改。
- 来源: original

### Invariant I-3: 功能与缓存职责分离
- Rule: 01 可以修改业务逻辑、样式正文及其定向测试，但不得修改 `index.html` 或缓存键；02 只能把当前旧键逐字替换为 `20260914-followup-email`，不得修改资源正文、业务断言结构或接口。
- Applies to: 01/02 全部任务。
- Violation consequence: 子计划无法独立验证，缓存变更掩盖业务 diff。
- 来源: original；K-frontend-cache-key-triad

### Invariant I-4: 原子发布与回滚
- Rule: 01 自动验证通过后状态只能是 `IMPLEMENTED_NOT_RELEASABLE`；只有 02 和联合机器验证均通过后才能进入 `READY_FOR_HUMAN_REVIEW`。人工验收通过后才可另行授权发布。若发布后回滚，必须同时回滚 02 缓存键和 01 功能，禁止只回滚一半。
- Applies to: 状态记录、发布候选、回滚。
- Violation consequence: 浏览器缓存继续使用旧资源，或新键指向已回滚的功能文件。
- 来源: K-master-plan-shared-file-sequential-gates

### Invariant I-5: 联合交付保持人工控制
- Rule: 最终页面不得自动选择引用邮件、自动填入或自动发送；必须人工选择、人工点击“填入人工回复”、人工点击现有发送按钮。服务端必须重新校验所选锚点，页面展示、正文引用和 SMTP 线程必须同源。
- Applies to: 01 I-1/I-2/I-3/I-6/I-8、02 缓存激活、联合验收。
- Violation consequence: 引用错误线程或误发专家邮件。
- 来源: original；K-explicit-outbound-anchor-server-revalidation

## 样式契约

### S-1: 功能界面
- 唯一合同：[`followup-email-01-manual-anchor.md`](followup-email-01-manual-anchor.md) 的 S-1、S-2、S-3，包括完整 DOM、全部 `followup-*` CSS 实值和 760px 响应式规则。
- 复用与新增：严格使用 01 中列出的 `.button`、`.mc-note`、`.followup-*`；本总计划不授权新 class 或新 CSS 属性。
- 禁止项：执行时重新解释截图、微调 01 的逐字 CSS、修改字节锁定的 `mailbox-chat.css`、增加 inline style。

### S-2: 静态资源注册
- 唯一合同：[`followup-email-02-cache-activation.md`](followup-email-02-cache-activation.md) 的 S-1。
- DOM 结构：4 个 CSS、无版本号的 `task-modal-runtime.js`、5 个版本化 JS 的文件名和顺序逐字保持；9 个版本化资源统一使用 `20260914-followup-email`。
- 禁止项：新增、删除或重排资源；修改资源正文。

## 现状审计

### 子计划与共享文件
- 01 白名单由其表格逐项枚举为 9 个文件（原始 8 个 + A1 追加的 `src/test/js/meetingConfirmationIntegration.test.js`），覆盖邮件发送链、收发件箱前端与会议确认集成测试三个子系统。
- 02 白名单由其表格逐项枚举为 10 个文件，覆盖静态入口及固定键测试一个子系统。
- A1 依据：S-1 在 `.mc-editor-tools` 内新增跟进按钮，使 `meetingConfirmationIntegration.test.js:1361,1411` 两处“工具条第 N 个按钮”计数失效，而该文件不在任一子计划白名单内；只有把该文件纳入 01 白名单并重钉这两处断言，才能在保持 S-1 DOM 不变的前提下让全量 JS 套件转绿。
- 使用两份白名单集合求交的当前结果：

```text
plan1 8
plan2 10
shared
src/test/js/mailboxChatStyle.test.js
```

- Interaction points: 01 在共享测试中新增 `styles.css` 规则与 class 卫生断言；02 随后只替换同一文件中的旧缓存键正则。反向执行或并行合并会丢失其中一类修改。（来源: K-master-plan-shared-file-sequential-gates）

### 功能链代码证据
- 数据来源：`MailboxConversationRepository.timelineMessages/rangeUnionSql` 与 `MailboxConversationService.listMessages` 已向前端返回真实 `mail_record.id/body/accountCode/messageId/inReplyTo`；01 无需新增 GET 或 schema。
- 当前发送差距：`PendingMailOperationService.sendConversationManualRichReply:388-397` 固定调用 `findLatestSentOutboundAnchor`；01 才能引入显式 id 的服务端权威校验。
- 现有写路径：01 已审计 `mail_record` 全部生产写入、`mail_send_attempt` claim/finalize 和前端 draft 写入；总计划不增加写路径。
- Interaction points: 时间线读取 → 前端选择 → DTO → 服务端重读并验证 → 原发送链写 `mail_send_attempt/mail_record` → 时间线刷新。联合门禁必须覆盖该完整链。

### 静态资源代码证据
- `index.html:11-14,2110-2114` 当前有 4 个 CSS 和 5 个 JS 使用 `20260910-meeting-generic-template`；`task-modal-runtime.js:2109` 无版本号。
- 当前旧键精确反查命中 `index.html` 与 9 个测试文件，02 已逐项列出文件和行号。执行 02 前必须重新运行其反查命令；结果不同即修订计划，不把新文件临时加入白名单。（来源: K-plan-quantified-claims-need-grep-receipts；K-frontend-cache-key-triad）
- Interaction points: 01 修改 `styles.css/mailbox-chat.js` → 02 新键生成新资源 URL → 普通刷新加载完整功能。两个阶段因此是一个发布单元。

### 前端样式盘点
- 可复用 class、设计 token、DOM 基线和完整新增规则均已在 01 的“样式契约/现状审计”冻结。
- 02 只修改资源 URL 查询参数；其 S-1 冻结资源 DOM。
- 本总计划不修改前端文件，不形成第三套样式事实。

## 实现方案

### 阶段 0：基线门禁
- 读取本总计划及 01、02；记录当前 commit 和 `git status --short`，区分既有工作区改动。不得清理或覆盖不属于两个子计划的现有改动。遵守 I-1、I-2。
- 按 02 的命令重新反查旧缓存键；确认文件集合与计划一致。若不一致，停止实现并修订 02 与本总计划。遵守 I-2、I-3。

### 阶段 1：执行 01 功能计划
- 严格执行 [`followup-email-01-manual-anchor.md`](followup-email-01-manual-anchor.md) 的阶段 1、阶段 2，只修改其白名单，逐条满足 I-1..I-8 与 S-1..S-3。遵守本计划 I-1、I-2、I-3、I-5。
- 运行 01 的定向 Node/Kotlin 验证；再运行当时工作区可执行的全部 JS 与 `mvn test`。所有失败必须归因并解决；不得用 skip 代替通过。
- 通过后记录状态 `IMPLEMENTED_NOT_RELEASABLE`。不得部署、不得修改缓存键、不得开始人工生产验收。遵守 I-4。

### 阶段 2：执行 02 缓存激活计划
- 从阶段 1 的同一工作区继续，严格执行 [`followup-email-02-cache-activation.md`](followup-email-02-cache-activation.md)；共享 `mailboxChatStyle.test.js` 必须保留阶段 1 新增断言，只替换缓存键文字。遵守 I-1、I-2、I-3。
- 运行 02 的旧键/新键反查、9 个定向测试、全部 JS 测试与 `mvn test`。失败不得标为通过。

### 阶段 3：联合验证与人工验收
- 运行两个子计划验收标准的并集；检查最终 diff 中每个代码文件都属于当前阶段白名单，且没有计划外文件。
- 02 通过后状态为 `READY_FOR_HUMAN_REVIEW`，按本计划 A-1..A-3 汇总执行全部子计划人工验收。人工验收使用隔离邮箱和隔离数据。
- 人工验收全部通过后，形成可审查结果；生产发布和真实发信仍需独立指令。遵守 I-4、I-5。

## 变更文件清单

本总计划是编排文档，不新增生产代码白名单：

| 文件 | 作用 |
|---|---|
| `docs/plans/2026-09-14/00-followup-email-main.md` | 顺序、边界、状态与联合验收合同 |
| `docs/plans/2026-09-14/followup-email-01-manual-anchor.md` | 01 唯一功能实现白名单与细节合同 |
| `docs/plans/2026-09-14/followup-email-02-cache-activation.md` | 02 唯一缓存激活白名单与细节合同 |

执行代码文件必须分别以 01、02 的“变更文件清单”为准。总计划不得把两个白名单合并后跨阶段任意编辑。

## 验收标准

- I-1: 执行记录显示 01 验证完成时间早于 02 首次修改时间；共享测试最终同时包含 01 新增规则断言和 02 新缓存键。
- I-2: 分阶段 diff 的每个代码文件分别属于对应子计划白名单；无额外生产或测试文件。
- I-3: 01 diff 不含 `index.html` 或 `20260914-followup-email`；02 diff 只有 10 个白名单文件的缓存键逐字替换，资源正文和断言结构无变化。
- I-4: 状态顺序严格为 `待执行` → `IMPLEMENTED_NOT_RELEASABLE` → `READY_FOR_HUMAN_REVIEW`；联合验证前无部署记录。回滚测试或操作记录同时覆盖 01、02。
- I-5: 01 的 I-1/I-2/I-3/I-6/I-8 自动验证全部通过；选中较旧邮件时，预览、请求 id、`sourceAnchor`、SMTP `In-Reply-To` 使用同一记录。
- S-1: 01 的 S-1..S-3 逐字规则、DOM、class、focus、disabled 与响应式断言全部通过；`mailbox-chat.css` 字节不变。
- S-2: 02 的 S-1 断言通过；旧键零命中，新键反查集合与 02 白名单一致，资源数量和顺序不变。
- 联合命令：执行两个子计划列出的定向命令后，运行 `node --test src/test/js/*.test.js`、`mvn test`、`git diff --check`；退出码均为 0，skip 单独列出且不计通过。

## 人工验收清单

### A-1: 完整跟进路径
- 前置条件: 隔离验收环境已完成 01、02 机器验证；测试专家有两封不同主题的成功发件，验收邮箱可查看原始 MIME。
- 操作步骤: 1. 普通刷新收发件箱；2. 打开该专家人工回复；3. 点击“跟进邮件”；4. 选择较旧发件；5. 编辑正文一个单词；6. 点击“填入人工回复”；7. 点击现有“发送人工回复”；8. 查看收到邮件的正文和原始邮件头。
- 预期结果: 弹窗初始无选中；填入前不发送；编辑内容保留；最终只收到一封邮件；引用正文与所选旧邮件一致；`In-Reply-To` 等于所选旧邮件 Message-ID；未引用较新邮件。
- 覆盖: I-1、I-4、I-5、S-1、S-2；01 A-1/A-2/A-6；02 A-1/A-2

### A-2: 子计划完整回归
- 前置条件: 使用各子计划人工验收清单指定的隔离数据、测试账号、桌面和窄屏视口。
- 操作步骤: 1. 逐条执行 01 A-1..A-12；2. 逐条执行 02 A-1..A-5；3. 为每条记录验收人、日期、PASS/FAIL 和证据。
- 预期结果: 每个子计划 A-n 均有记录且全部为 PASS；普通来信回复、无来信旧回信、会议确认、QA、可信回复、资源数量和加载顺序符合各自明确实值。
- 覆盖: I-1..I-5、S-1、S-2、全部子计划 must-NOT-change 与 interaction points

### A-3: 发布边界
- 前置条件: 01 已完成，但暂未执行 02；部署流程可查看候选状态，未连接生产发布。
- 操作步骤: 1. 检查 01 阶段状态；2. 尝试进入发布评审；3. 完成 02 和联合验证后再次检查状态。
- 预期结果: 第一次状态为 `IMPLEMENTED_NOT_RELEASABLE`，发布评审被门禁阻止；第二次状态为 `READY_FOR_HUMAN_REVIEW`，仍不会自动部署或向真实专家发信。
- 覆盖: I-3、I-4、需求描述“必须保持”第 4 项
