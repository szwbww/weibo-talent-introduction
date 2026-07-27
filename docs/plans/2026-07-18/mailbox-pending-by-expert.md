# 收发件箱按专家聚合待处理邮件

## 需求描述

在现有收发件箱增加“按邮件 / 按专家聚合”单选展示方式。默认保持“按邮件”；选择“按专家聚合”后，主列表按最近待处理来信时间展示专家信息，每位专家展开后展示其符合当前账号、邮箱、关键词条件的全部待处理邮件，并继续复用现有查看、处理、标签和附件入口。

必须不变：

- 默认“按邮件”模式的 `/api/mail/mailbox` 返回结构、邮件分页、筛选、详情和操作行为不变。
- 未关联专家的待匹配邮件继续留在“按邮件”模式，不伪装成一个“未知专家”聚合组。
- `enabled=false` 的真实邮箱历史记录仍可见；仅排除 `SIMULATOR_NOOP`（来源: K-sender-account-enabled-scope）。
- 待处理邮件被处理后，既有状态写入、审计、专家 `needs_manual_attention` 清理逻辑不变。

范围外：

- 不修改 `mail_record`、`inbound_mail_processing`、`expert_contact` 表结构。
- 不修改来信落库、自动回复、人工处理写路径。
- 不改“来信汇总”页面现有的前端聚合实现；该页面若要以专家为准确分页单位，另立计划。
- 不增加批量处理、全组一键处理或新的专家状态编辑能力。

## 关键不变量

### Invariant I-1: 专家组只包含已关联的待处理来信
- Rule: 专家聚合端点只读取 `inbound_mail_processing.process_status = 'MANUAL_REVIEW' AND expert_contact_id IS NOT NULL`；不混入 OUTBOUND、PROCESSED 或未关联专家记录。
- Applies to: `MailRecordRepository` 的专家汇总查询、组内邮件查询；`MailboxService.listPendingByExpert()`。
- Violation consequence: 主列表出现没有可处理子邮件的专家，或未匹配邮件被错误归入专家组。
- 来源: original

### Invariant I-2: 先聚合专家，再按专家分页
- Rule: 分页单位是去重后的 `expert_contact_id`；总数是符合条件的去重专家数；排序键是每位专家符合条件邮件的 `MAX(received_at) DESC`，并以 `expert_contact_id DESC` 作为稳定次序。禁止先分页邮件再在前端聚合（来源: K-distinct-contact-order-query、K-group-before-pagination）。
- Applies to: 专家汇总列表、总数查询、前端分页文案。
- Violation consequence: 同一专家跨页、专家总数错误、组内待处理数与实际不符。
- 来源: K-distinct-contact-order-query

### Invariant I-3: 汇总条件与子列表条件完全一致
- Rule: `accountCode`、`recipientEmail`、`keyword` 的 SQL 条件必须在专家汇总、去重计数和子邮件批量查询中逐字保持同一语义；账号范围始终为所有非 `SIMULATOR_NOOP` 账号。专家模式固定为“已关联 + INBOUND + MANUAL_REVIEW”，前端不得继续发送方向、标签、日期参数（来源: K-filter-option-scope-parity、K-sender-account-enabled-scope）。
- Applies to: Controller 参数、Service 调用、3 条 Repository 查询、前端模式切换和请求构造。
- Violation consequence: 组头计数、分页总数和展开邮件互相不一致，或历史邮件因账号禁用被隐藏。
- 来源: K-filter-option-scope-parity

### Invariant I-4: 空专家页不得展开空 IN 查询
- Rule: 专家汇总页为空时，Service 必须直接返回空组，禁止调用含 `IN (:expertContactIds)` 的子邮件查询（来源: K-empty-list-in-query-guard）。
- Applies to: `MailboxService.listPendingByExpert()`。
- Violation consequence: MySQL 生成 `IN ()` 并返回 500。
- 来源: K-empty-list-in-query-guard

### Invariant I-5: 子邮件身份和现有操作契约不变
- Rule: 每封子邮件仍使用 `source='INBOUND_PROCESSING'`、原 `id`、`inboundProcessingId`、标签与附件标志，并复用 `renderMailboxActions()` 和现有事件委托；不得生成聚合级伪邮件 ID。
- Applies to: Repository 子邮件映射、Service DTO 映射、`renderMailboxExpertGroups()`。
- Violation consequence: 查看详情、处理、标记解决、标签和附件操作指向错误记录。
- 来源: original

### Invariant I-6: 本计划只增加读路径
- Rule: 新端点和 UI 不写任何业务表；待处理状态仍只由既有 `AutoMailReplyService` 新建路径、`UnmatchedInboundMailService`/`PendingMailOperationService` 状态更新路径产生或清除（来源: K-inbound-processing-write-paths）。
- Applies to: 全部变更文件。
- Violation consequence: 聚合浏览行为意外改变待办状态或专家状态。
- 来源: K-inbound-processing-write-paths

## 样式契约

### S-1: 展示方式单选器
- 复用：外层继续使用 `.toolbar`（`styles.css:315`）；单选器新增 `.mailbox-view-mode`，不得复用语义不符的 `.compose-template-preview-mode`。
- 新增：以下规则逐字加入 `styles.css`，不得改值：

```css
.mailbox-view-mode {
    display: inline-flex;
    align-items: center;
    gap: 2px;
    padding: 2px;
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    background: var(--surface);
}

.mailbox-view-mode label {
    position: relative;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 26px;
    padding: 0 10px;
    border-radius: 5px;
    color: var(--text-muted);
    font-size: 12px;
    font-weight: 600;
    cursor: pointer;
    user-select: none;
}

.mailbox-view-mode input {
    position: absolute;
    width: 1px;
    min-width: 0;
    height: 1px;
    min-height: 0;
    opacity: 0;
    pointer-events: none;
}

.mailbox-view-mode label:hover {
    color: var(--text-main);
    background: rgba(var(--primary-rgb), 0.06);
}

.mailbox-view-mode label:has(input:checked) {
    color: var(--primary);
    background: var(--primary-light);
    box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
}

.mailbox-view-mode label:has(input:focus-visible) {
    outline: 2px solid rgba(var(--primary-rgb), 0.35);
    outline-offset: 1px;
}

.mailbox-view-mode label:has(input:disabled) {
    opacity: 0.55;
    cursor: not-allowed;
}
```

- DOM 结构：

```html
<div class="mailbox-view-mode" role="radiogroup" aria-label="收发件箱展示方式">
    <label><input type="radio" name="mailboxViewMode" value="MAIL" checked><span>按邮件</span></label>
    <label><input type="radio" name="mailboxViewMode" value="EXPERT"><span>按专家聚合</span></label>
</div>
```

- 禁止项：inline style；第三种展示状态；未声明 class；修改全局 `.toolbar input` 或 `.checkbox-row` 规则。

### S-2: 专家主列表与待处理邮件子列表
- 复用：专家折叠容器完整复用 `.inbound-expert-group`、`.inbound-expert-group-header`、`.inbound-expert-group-name`、`.inbound-expert-group-email`、`.inbound-expert-group-count`（`styles.css:3370-3459`）；邮件卡片完整复用 `.mailbox-card*`（`styles.css:4442-4505`）；状态展示复用 `.badge`（`styles.css:751-799`）。这些既有规则不做就地修改。
- 新增：无新 CSS。
- DOM 结构：

```html
<details class="inbound-expert-group">
    <summary class="inbound-expert-group-header">
        <span class="inbound-expert-group-name"><a data-action="open-monitoring-contact">专家名</a> <span class="badge">专家状态</span> <span class="badge">专家层级</span></span>
        <span class="inbound-expert-group-email">专家邮箱 · ORCID</span>
        <span class="inbound-expert-group-count">N 封待处理</span>
    </summary>
    <div class="inbound-expert-group-mails">
        <div class="mailbox-card" data-source="INBOUND_PROCESSING" data-id="邮件ID">
            <div class="mailbox-card-tags">既有邮件标签</div>
            <div class="mailbox-card-subject">邮件主题</div>
            <div class="mailbox-card-meta">时间 · 邮箱账号 · 发件邮箱</div>
            <div class="mailbox-card-actions">既有单封邮件操作</div>
        </div>
    </div>
</details>
```

- 禁止项：新增近似卡片 class；客户端对普通邮件分页结果 `groupBy`；聚合级处理按钮；改动上述既有 class。既有 class 全部使用点保持原状；本计划仅增加新的使用点。

## 现状审计

### `inbound_mail_processing`
- Schema/mapping: `V5__create_inbound_mail_processing.sql` 定义 `expert_contact_id` 可空、`process_status` 非空、`received_at` 非空、账号+IMAP UID 唯一键；V10 增加正文/清洗正文/解决信息，V14 增加 `reason_type`；领域映射见 `InboundMailProcessing.kt`。
- Write paths:
  1. `AutoMailReplyService.confirmProcessed()` — 所有常规 PROCESSED/MANUAL_REVIEW 新建落库点。
  2. `AutoMailReplyService.confirmManualReviewWithBody()` — 带清洗正文的 MANUAL_REVIEW 新建落库点。
  3. `UnmatchedInboundMailService.bindToContact()` / `markResolved()` — copy 保存，将 MANUAL_REVIEW 改为 PROCESSED。
  4. `PendingMailOperationService.markResolved()` — copy 保存，将专家待处理邮件改为 PROCESSED。
  5. V14/V15 migrations — 历史字段回填（来源: K-inbound-processing-write-paths）。
- Read paths: `MailboxService.listMailbox()` 当前将其与 OUTBOUND `mail_record` 合并按单封邮件分页；`UnmatchedInboundMailService` 读取人工队列；`MailMonitoringService`、`InboundMailSummaryController` 读取监控/汇总；待处理操作服务按 ID 读取。
- Interaction points: 新建 MANUAL_REVIEW 且已有 `expert_contact_id` → 新聚合端点可见；处理为 PROCESSED → 下次刷新从子列表消失，最后一封处理后专家组消失。

### `expert_contact`
- Schema/mapping: `V1__create_business_tables.sql` 定义姓名、邮箱、ORCID；V14 增加 `current_index_level`；V19 增加 `operator_status`；领域映射见 `ExpertContact.kt`。
- Write paths: `InitialOutreachService`/`ManualInitialOutreachService` 创建或补建；`ExpertOperatorStatusService`、`ExpertIndexLevelOperationService`、`ExpertContactManagementService`、`ConversationStateService` 更新状态；来信处理服务更新人工关注；迁移 V13/V14/V19 回填。
- Read paths: 现有收发件箱 SQL LEFT JOIN 获取姓名/邮箱；详情服务按 ID 获取 ORCID/层级；新聚合汇总读取姓名、邮箱、ORCID、当前状态、运营状态、层级。
- Interaction points: 专家信息变更后，聚合主行刷新即展示最新值；不复制专家快照到来信表。

### `mail_sender_account`、`mail_record`、附件与标签兼容路径
- Schema/mapping: 收发件箱通过 `findAllByAccountCodeNot('SIMULATOR_NOOP')` 获取可见账号；这是“真实账号历史可见”的范围，不等于自动发送 enabled 范围（来源: K-sender-account-enabled-scope）。
- Write paths: 本计划不新增写入；账号计数/暂停由现有 Repository 更新，`mail_record` 由首发、自动回复、人工回复、会议服务写入，附件/来信标签由既有服务写入。
- Read paths: 现有 `MailRecordRepository.listMailbox/countMailbox` 读取 OUTBOUND + `inbound_mail_processing`；附件标志通过 `mail_attachment`/`mail_record.message_id` 关联；`InboundMailTagService.listTagsBatch()` 批量补齐子邮件标签。
- Interaction points: 新子邮件查询继续返回与普通待处理列表相同的附件标志和 inbound ID，Service 继续批量装配标签，不改变详情端点。

### 前端样式盘点
- 可复用 class: `.toolbar`（`styles.css:315-332`）用于筛选工具栏；`.badge`（`:751-799`）用于状态；`.inbound-expert-group*`（`:3370-3459`）用于专家折叠组；`.mailbox-list/.mailbox-card*`（`:4429-4505`）用于邮件列表与操作卡片。
- 设计基准 token: 工具栏间距 8px、控件高 32px；主色 `var(--primary)` / `var(--primary-light)`；边框 `var(--border)`；小圆角 `var(--radius-sm)`；组头展开动画 0.15s；邮件 hover 边框 `rgba(var(--primary-rgb), 0.35)`。
- DOM 结构约定: `#mailboxList` 作为事件委托根；详情/处理动作依赖后代元素的 `data-action`、`data-source`、`data-id`；专家跳转依赖 `data-action="open-monitoring-contact"` 和 contact ID。
- 改动前基线:

```html
<label class="checkbox-row"><input type="checkbox" id="mailboxFilterOnlyPending"> 仅待处理</label>
...
<div class="mailbox-list" id="mailboxList"></div>
<div class="pagination" id="mailboxPagination" style="padding: 16px 24px; display: flex; justify-content: flex-end; gap: 8px;"></div>
```

```css
.mailbox-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 16px 24px;
}

.mailbox-card {
    position: relative;
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding: 12px 14px;
    border: 1px solid var(--border);
    border-radius: var(--radius-md);
    background: var(--panel-bg);
    transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
```

## 实现方案

### 阶段 1：后端专家分页读模型

1. 在 `MailboxController.kt` 增加 `MailboxExpertGroupResponse`、`MailboxExpertGroupListResponse`，并新增 `GET /api/mail/mailbox/pending-by-expert`。参数只接收 `accountCode`、`keyword`、`recipientEmail`、`page`、`size`；`size` 仍限制 1..100。遵守 I-1/I-2/I-3/I-6。
2. 在 `MailRecordRepository.kt` 增加：专家分页汇总投影与查询、`COUNT(DISTINCT imp.expert_contact_id)`、按当前页 contact IDs 批量查询 MANUAL_REVIEW 邮件。三条 SQL 共享相同 WHERE 条件；汇总用 `GROUP BY` + `MAX(received_at)`，子邮件用 `expert_contact_id IN (...)` 并按专家、时间倒序。遵守 I-1/I-2/I-3/I-5。
3. 在 `MailboxService.kt` 增加 `listPendingByExpert()`：复用非模拟账号范围校验；先查专家页，再对非空 IDs 批量查子邮件，批量加载 inbound tags，按专家 ID 组装嵌套 DTO；空页直接返回。既有写路径无需调整，新读路径消费 `AutoMailReplyService` 两个新建 sink 和两个处理服务的状态更新。遵守 I-1 至 I-6。

### 阶段 2：前端模式切换和嵌套展示

1. 在 `index.html` 工具栏加入 S-1 的 radio group。选择 EXPERT 时，方向、标签、仅待处理、起止日期控件禁用；账号、收件人邮箱、关键词继续可用。选择 MAIL 时恢复原控件状态与原请求。遵守 I-3、S-1。
2. 在 `app.js` 的 mailbox state 增加 `viewMode: 'MAIL'` 与 `groups: []`。`loadMailbox()` 按模式调用旧端点或 `/pending-by-expert`；切换模式时页码归零。专家模式不得读取普通邮件 items 做客户端分组。遵守 I-2/I-3/I-4。
3. 抽出单封 `MailboxItemResponse` 卡片渲染 helper，使普通模式与专家子列表共用；新增 `renderMailboxExpertGroups()` 按 S-2 输出专家主行和子邮件卡片。分页文案在 MAIL 模式显示“共 N 条”，EXPERT 模式显示“共 N 位专家”。现有 `#mailboxList` 事件委托不变。遵守 I-5、S-2。
4. 在 `styles.css` 原样加入 S-1 CSS。不得改 S-2 所列既有规则。

### 阶段 3：验证

1. `MailboxControllerTest.kt`: 验证新端点参数映射、分页 size 限制和嵌套 DTO JSON；旧端点测试保持通过。遵守 I-3/I-5。
2. `MailboxServiceTest.kt`: 覆盖非模拟账号、非法账号、空专家页不调用 `IN` 查询、批量标签与组装、稳定组顺序。遵守 I-1/I-3/I-4/I-5。
3. `MailRecordRepositoryMonitoringIT.kt`: MySQL 语义验证去重专家计数、`MAX(received_at)` 排序、同专家多封邮件不跨页、过滤条件在汇总/计数/子项一致。遵守 I-1/I-2/I-3。
4. 新建 `mailboxExpertGrouping.test.js`: 验证模式切换端点、禁用/恢复筛选器、专家分页单位、嵌套 DOM 与既有 data-action 契约。遵守 I-2/I-3/I-5、S-1/S-2。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxController.kt` | 新 DTO 与专家聚合 GET 端点 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt` | 专家页 + 子邮件批量组装 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt` | 专家聚合、计数、子邮件查询 |
| 4 | `src/main/resources/static/index.html` | 展示方式 radio group |
| 5 | `src/main/resources/static/app.js` | 模式状态、请求、渲染、事件 |
| 6 | `src/main/resources/static/styles.css` | S-1 单选器样式 |
| 7 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxControllerTest.kt` | Controller 回归/新端点测试 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt` | Service 聚合测试 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/monitoring/repository/MailRecordRepositoryMonitoringIT.kt` | MySQL 聚合/分页语义测试 |
| 10 | `src/test/js/mailboxExpertGrouping.test.js` | 前端模式和 DOM 契约测试 |

## 验收标准

- I-1: Repository IT 准备已关联 MANUAL_REVIEW、未关联 MANUAL_REVIEW、已关联 PROCESSED、OUTBOUND 各一条；新端点仅返回第一条所属专家。
- I-2: 同一专家 3 封、另一专家 1 封，`size=1` 时第一页/第二页各 1 位专家，总数为 2；第一位由最新待处理 `received_at` 决定，3 封不跨页。
- I-3: 分别用账号、发件邮箱、主题/正文关键词过滤，断言专家总数、组头 `pendingCount` 与 `mails.size` 一致；前端 EXPERT 请求无 direction/tag/date 参数。
- I-4: Service 空汇总页返回 `groups=[]`、`totalCount=0`，Mockito 验证子邮件 `IN` 查询 never called。
- I-5: 每个子邮件 JSON/DOM 保留真实 `INBOUND_PROCESSING` source 和 ID；现有 view/open-pending/resolve/tag action 测试通过。
- I-6: `git diff` 不包含 migration/domain 业务字段变更；新 Controller 仅 `@GetMapping`。
- S-1: `styles.css` 新规则与契约代码块逐字一致；radio DOM 与骨架一致；键盘 focus 有 2px outline；无 inline style/未声明 class。
- S-2: grep 确认未修改 `styles.css:3370-3459`、`:4442-4505` 既有规则；专家组 DOM 与骨架一致；无新增近似卡片 class。
- 集成: 新 MANUAL_REVIEW 入站写入 → 专家组刷新出现；执行现有“标记已处理” → 邮件消失且计数减 1；处理最后一封 → 专家组消失、总专家数减 1。
- 回归命令: `mvn -Dtest=MailboxControllerTest,MailboxServiceTest,MailRecordRepositoryMonitoringIT test`；前端执行项目既有 JS test runner并包含 `mailboxDateDefault.test.js`、`mailboxInboundTags.test.js`、`mailboxExpertGrouping.test.js`。

## 人工验收清单

### A-1: 默认按邮件展示不变
- 前置条件: 账号 A 下存在 1 封发件、1 封已处理收件、1 封待处理收件。
- 操作步骤: 1. 打开“收发件箱”；2. 保持“按邮件”；3. 点击查询；4. 分别打开三封邮件。
- 预期结果: 单选默认值为“按邮件”；列表仍按单封邮件展示；分页文案为“共 3 条”；三封详情均可打开。
- 覆盖: I-5 / must-NOT-change 第 1 条 / S-1

### A-2: 按专家聚合待处理邮件
- 前置条件: 专家甲有 3 封 MANUAL_REVIEW，专家乙有 1 封 MANUAL_REVIEW；甲另有 2 封 PROCESSED；两人均有关联 ExpertContact。
- 操作步骤: 1. 选择“按专家聚合”；2. 点击查询；3. 展开专家甲；4. 展开专家乙。
- 预期结果: 主列表共 2 位专家；甲显示“3 封待处理”且子列表正好 3 封；乙显示“1 封待处理”；2 封 PROCESSED 不出现。
- 覆盖: I-1/I-2 / observable outcome / S-2

### A-3: 专家信息展示
- 前置条件: 专家甲姓名为“张三”、邮箱 `zhang@example.com`、ORCID `0000-0001-0002-0003`、运营状态 REPLIED、层级 APPLICATION。
- 操作步骤: 1. 进入专家聚合模式；2. 找到专家甲；3. 点击专家姓名。
- 预期结果: 主行显示“张三”、`zhang@example.com`、`0000-0001-0002-0003`、“已回复”、“有效”；点击姓名跳转并打开 contact ID 对应的专家详情。
- 覆盖: I-5 / observable outcome / S-2

### A-4: 聚合筛选一致
- 前置条件: 专家甲在账号 A 有主题含“材料”的 2 封待处理邮件，在账号 B 有 1 封；专家乙仅在账号 B 有主题含“会议”的 1 封。
- 操作步骤: 1. 选择专家聚合；2. 账号选 A；3. 关键词填“材料”；4. 查询并展开专家甲。
- 预期结果: 共 1 位专家；专家甲显示“2 封待处理”，展开也只有账号 A 且主题含“材料”的 2 封。
- 覆盖: I-3 / interaction point

### A-5: 空结果稳定
- 前置条件: 输入一个不存在的专家邮箱 `nobody@example.invalid`。
- 操作步骤: 1. 选择专家聚合；2. 邮箱输入该值；3. 查询。
- 预期结果: 页面显示“暂无待处理专家”，分页显示“共 0 位专家”，无 500 错误。
- 覆盖: I-4

### A-6: 处理后实时收敛
- 前置条件: 专家甲恰有 2 封待处理邮件。
- 操作步骤: 1. 专家聚合模式展开甲；2. 对第一封执行现有处理动作并刷新；3. 对第二封执行处理动作并刷新。
- 预期结果: 第一次刷新后甲显示“1 封待处理”；第二次刷新后甲从主列表消失，总专家数减 1；专家的现有人工关注清理行为与改造前一致。
- 覆盖: I-1/I-6 / interaction point / must-NOT-change 第 4 条

### A-7: 未关联邮件不被错误聚合
- 前置条件: 存在 1 封 `expert_contact_id=NULL` 且 MANUAL_REVIEW 的待匹配邮件。
- 操作步骤: 1. 选择专家聚合并查询；2. 切回按邮件；3. 勾选“仅待处理”并查询。
- 预期结果: 专家聚合列表中没有“未知专家”组；按邮件模式能看到该邮件并可执行原待匹配操作。
- 覆盖: I-1 / must-NOT-change 第 2 条

### A-8: 禁用真实账号历史仍可见
- 前置条件: 真实账号 C `enabled=false`，其下有专家甲的 1 封待处理邮件；另有模拟账号 `SIMULATOR_NOOP` 的记录。
- 操作步骤: 1. 选择专家聚合；2. 不选具体账号并查询。
- 预期结果: 专家甲及账号 C 的邮件可见；`SIMULATOR_NOOP` 记录不可见。
- 覆盖: I-3 / must-NOT-change 第 3 条

### A-9: UI 样式与键盘操作
- 前置条件: 桌面宽度与窄屏各打开一次收发件箱。
- 操作步骤: 1. 用 Tab 聚焦两个展示方式；2. 用方向键切换；3. 展开一个专家组；4. 对照样式契约检查。
- 预期结果: 选中项使用 `var(--primary)` 文字和 `var(--primary-light)` 背景；焦点 outline 为 2px；专家组沿用左侧 4px 主色条、58px 最小组头、待处理数胶囊；窄屏不横向溢出。
- 覆盖: S-1/S-2
