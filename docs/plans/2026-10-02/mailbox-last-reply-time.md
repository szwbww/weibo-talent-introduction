# 收发件箱：专家上次回复时间

状态：待评审的开发计划；本轮未修改生产代码。日期：2026-10-02。

审计基线：`bf19fdfcb24336a41106d1c46fa7147bc6546892` 的当前工作区。工作区另有既存修改，执行时不得覆盖。下述行号对应本轮取证，实施前以方法名和证据中的 SHA256 复核，不机械套用漂移行号。

证据：

- [代码摘录与源文件 SHA256](mailbox-last-reply-evidence/code-baseline.txt)
- [完整检索命令、输出、退出码](mailbox-last-reply-evidence/grep-receipts.txt)，正文以 R1–R12 引用。
- [既有测试运行记录](mailbox-last-reply-evidence/baseline-tests.txt)：134 tests，134 pass，0 fail。这是实施前基线，不是新功能通过证明。
- [已确认的交互预览](../../mockups/mailbox-last-reply-preview/index.html)、[预览截图](../../mockups/mailbox-last-reply-preview/preview.png)。预览复用组件，含线上记录节选和明确标记的模拟样本；不作为实时生产数据或后端联调证据。

## 需求描述

在收发件箱专家列表和当前专家详情头部显示上次回复，格式为 `2026-10-02 星期五 17:59`；固定北京时间。未收到来信时显示“尚未回复”，无法取得可信时间时显示“回复时间暂不可用”。

可观察结果：

1. 列表增加“上次回复”，详情增加“专家上次回复”，详情明确标注“北京时间”；日期、星期、时分常驻可见。
2. 两处读取当前账号范围内的最近来信；我方后续发件不改变该值。刷新取得新 summary 后，两处同步更新。
3. 无来信与时间异常分开呈现；切换专家、账号筛选、已回复/待处理页签后不串值。

必须保持：

- M-1：既有列表排序、分页、搜索、页签、账号/日期/方向筛选以及“待匹配”邮件视图。
- M-2：正在编辑的人工回复正文、目标来信、可信工作台、附件与滚动位置；显示刷新不能触发发送或重建编辑器。
- M-3：名称、账号信息、关注按钮、收发计数、标签、材料和所在地/推荐联系时间的既有结构与交互。
- M-4：后端收件、标记处理、绑定专家、账号可见性、历史记录与接口结构。

不在范围内：新字段/表/迁移、历史回填、邮件分类修正、回复耗时、距今天数、排序/筛选新选项、专家当地时间、自动轮询、消息日分隔线改版、旧任务钻取表格增加列、发布上线。这里“回复”沿用收发件箱现有已关联来信口径，不额外声称识别“真人亲自回复”。

最小实现：复用 `ConversationItemResponse.latestInbound.receivedAt`，前端格式化及渲染。一个前端子系统，5 个实施文件，0 个新增持久化字段。

## 关键不变量

### Invariant I-1: 来信来源与账号范围

- Rule：取当前 summary 的 `latestInbound.receivedAt`。不得改用 `latestMessage.time`、最后一条已加载 timeline、`firstReplyAt`、`createdAt`、`resolvedAt`、所在地推荐样本。
- 当前范围是后端 `activeAccountCodes()` 加可选 `accountCode`，不是另算专家所有邮箱。日期、主题、方向等筛选决定专家是否入列，不截断最近来信投影。
- Applies to：新增格式化/展示函数、`renderPerson`、`renderHeader`、列表成功回包的局部展示刷新；不新增数据库写路径。
- Violation consequence：我方发件覆盖专家回复；跨账号泄漏/误显示；加载历史窗口不同导致值变化。
- 来源：K-mailbox-inbound-source-authority；证据 E-1、E-2、R4。

### Invariant I-2: 北京日期与星期来自同一时间

- Rule：当前接口是无 offset 的 `ISO_LOCAL_DATE_TIME`，按北京时间解释；输出固定 `YYYY-MM-DD 星期X HH:mm`，不依赖浏览器设备时区，不显示秒。星期与日期必须来自同一北京时间。
- `<time datetime>` 写入规范化的带 `+08:00` 时间；title 标注完整北京时间。普通日期值、属性值均经 `escapeText`。
- Applies to：新增格式化函数、列表与详情共用的展示函数；不修改既有 `datePart/timePart/contactZoneParts`。
- Violation consequence：海外设备出现日期或星期错一天；列表和详情不一致。
- 来源：K-mail-datetime-zone-runtime-contract；证据 E-3。

### Invariant I-3: 空值不是“从未回复”的通用替身

- Rule：`latestInbound === null && receivedCount === 0` 才显示“尚未回复”。存在 latestInbound 但时间空白/非法，或计数大于 0 却没有 latestInbound，显示“回复时间暂不可用”。字段缺失/结构异常同样显示后者。
- 不输出 `Invalid Date`、NaN、1970 年、当前时间或空 `<time>`；不得把 2 月 30 日自动修正成 3 月日期。
- Applies to：格式化函数和展示函数两个输出分支。
- Violation consequence：接口异常被伪装成无回复，影响跟进判断。
- 来源：original；正常空值契约见 DTO :62/:68 与 RepositoryIT :127–131，异常分支是本需求明确规定的防御行为。

### Invariant I-4: 同步展示、局部更新、沿用竞态守卫

- Rule：有效 `fetchList` 成功回包后，列表与已选专家详情的回复时间取同一份当前行；只替换详情的回复时间槽。保留既有 `disposed/listSeq` 守卫。
- 当前列表没有已选专家时，不从别的行取值、不自行换人，仍交给既有选择流程；待匹配模式不执行此更新。失败回包沿用已有错误处理，不把旧值改成“尚未回复”。
- 不因显示功能修改 `instance.selectedSummary`、人工回复目标或草稿；不调用 `selectExpert`、`renderConversationContent`、`renderTimeline` 来刷新时间。
- Applies to：新增 `renderLastReplyHeader`，`renderHeader` 与 `fetchList` 的调用点。
- Violation consequence：两处时间不同，或刷新覆盖用户草稿、跳动滚动位置、切换回复目标。
- 来源：original；K-mailbox-draft-cache-owner-capture 用于确定不得触碰草稿 Map；证据 E-4、R5/R11。

### Invariant I-5: 纯展示与缓存激活边界

- Rule：不新增 API 请求、定时器、全局预览对象、localStorage/sessionStorage 或另存的 lastReplyAt 状态；不改变原 summary 对象。新增 CSS 只作用于 `.mail-chat` 内的新业务类。
- `mailbox-chat.css` 及其历史目标文件保持字节不变。`index.html` 现有版本化资源统一升级缓存键，不增加资源或调整顺序。
- Applies to：生产 JS/CSS/index 与测试改动。
- Violation consequence：重复状态漂移、额外网络开销、既有样式合同失败、浏览器仍使用旧脚本。
- 来源：K-mailbox-chat-css-byte-contract、K-frontend-cache-key-triad；证据 E-5、R6/R7/R12。

## 样式契约

### S-1: 列表回复时间行

- 复用：`.mc-person-main`（`mailbox-chat.css:17`）、`.mc-person` 活跃态（:16）、`.mc-person-meta`（:159）；这些既有规则不改。
- 插入位置：`renderPerson` 的 `<small>${escapeText(latestLine)}</small>` 后、`.mc-person-meta` 前。
- 新增 DOM（正常分支；具体值由 I-1/I-2 生成）：

```html
<span class="mailbox-reply-list">
    <span>上次回复</span>
    <time datetime="2026-10-02T17:59:00+08:00" title="北京时间 2026-10-02 星期五 17:59">2026-10-02 星期五 17:59</time>
</span>
```

空值分支用 `<span class="mailbox-reply-empty">尚未回复</span>` 替代 time；异常分支同结构、文案“回复时间暂不可用”。按钮原 aria-label 末尾增加相同纯文本说明（含北京时间），保留专家名称/标签描述。日期不能只出现在 title。

### S-2: 详情回复时间槽

- 复用：`.mc-identity`（`mailbox-chat.css:35`）、`.mc-identity h2/p`（:36–37、:104）、原 `calendar-summary`。既有规则不改。
- 目标层级：

```html
<div class="mc-identity">
    <h2>专家名称</h2>
    <p>邮箱 · 账号信息</p>
    <span class="mailbox-reply-detail" data-role="last-reply-time">
        <span>专家上次回复</span>
        <time datetime="2026-10-02T17:59:00+08:00" title="北京时间 2026-10-02 星期五 17:59">2026-10-02 星期五 17:59</time>
        <span class="mailbox-reply-zone">北京时间</span>
    </span>
    <span class="calendar-summary" data-role="meeting-summary"></span>
</div>
```

名称、邮箱、账号及 calendar-summary 的内容继续由原函数生成，上面仅明确层级。空/异常分支用 S-1 的 empty span 替代 time，并省略北京时间尾注。局部刷新只修改 `[data-role="last-reply-time"]` 的内容；renderHeader 重建后仍只有一个槽。

### S-3: 新增样式的逐字合同

下列整块追加至 `src/main/resources/static/styles.css`。来自已确认预览的时间行样式，业务前缀改为 `mailbox-reply-*`；不复制预览导航、顶部开关和全页布局。四个新增类的声明、子元素样式、活跃态和窄屏规则都在此。

```css
/* Mailbox last reply time: list and conversation header. */
.mail-chat .mailbox-reply-list{display:flex;flex-wrap:wrap;align-items:center;gap:3px 6px;font-size:11px;line-height:1.6;color:#64748b;margin-top:1px}
.mail-chat .mailbox-reply-list time{color:#334155;font-variant-numeric:tabular-nums;font-weight:500;white-space:nowrap}
.mail-chat .mc-person[data-active=true] .mailbox-reply-list time{color:#1e40af}
.mail-chat .mailbox-reply-detail{display:flex;flex-wrap:wrap;align-items:center;gap:4px 8px;margin-top:10px;font-size:12px;line-height:1.7;color:#64748b}
.mail-chat .mailbox-reply-detail time{font-size:13px;color:#1e40af;font-weight:600;font-variant-numeric:tabular-nums;white-space:nowrap}
.mail-chat .mailbox-reply-zone{font-size:11px;color:#94a3b8}
.mail-chat .mailbox-reply-empty{color:#94a3b8;font-weight:400}
@media(max-width:760px){.mail-chat .mailbox-reply-detail time{font-size:12px}}
```

新增节点均为展示元素，不新增 hover、active、disabled 控件状态；所属按钮继续继承既有 hover/active/focus 状态。S-1/S-2 中无 class 的 span/time 由父规则及上述子选择器控制。所有选择器包含新业务类，不改变既有 class 的通用规则，因此不波及其他使用点；既有使用点检索见 R8。

禁止 inline style、新增本节未声明的 class、新增 `<style>`/独立资源、修改 `.mc-person-main` 间距或既有标签/计数布局。执行人员不得自由调整合同值。

### S-4: 静态资源引用

`index.html:11–15,2345–2350` 的 5 个 CSS、6 个 JS 保留现有标签、文件名、顺序，只把版本值统一改为 `20261002-mailbox-last-reply`。`task-modal-runtime.js` 原本没有版本参数，保持原状。无可见 DOM 变化。

## 现状审计

### E-1：后端已经提供最近来信投影

代码根路径 `src/main/kotlin/com/weibo/talentintroduction/`，以下 Kotlin 路径相对于该目录。

| 代码位置 | 直接证据 | 本计划结论 |
|---|---|---|
| `mail/controller/MailboxConversationController.kt:38–43,62–68` | latestInbound 为可空对象，receivedAt 是 String；同一 summary 包含 receivedCount | 已有展示所需字段，不加 DTO 字段 |
| `mail/service/MailboxConversationService.kt:153–155,188–194` | 批量查询本页联系人的 latestInbound，并将 receivedAt 用 ISO 格式输出 | 不逐专家请求时间，不增加 N+1 |
| `mail/repository/MailboxConversationRepository.kt:295–334` | 候选与 NOT EXISTS 对手均为 INBOUND_PROCESSING；按 event_at，再按 id 判最新 | 更晚的 OUTBOUND 不会覆盖来信时间 |
| 同文件 :475–495 | event_at=imp.received_at；限制已绑定专家、accountCodes、可选 accountCode | 来源是 processing，沿用现有账号范围 |
| `mail/service/MailboxConversationService.kt:528–531` | findAllByAccountCodeNot(SIMULATOR_ACCOUNT_CODE)，未按 enabled=true 过滤 | 不把“活跃账号”注释误解成只允许 enabled=true |

Repository 的相关 SQL 原文：

```sql
WHERE u.expert_contact_id IN (:contactIds)
  AND u.source = 'INBOUND_PROCESSING'
  AND NOT EXISTS (
      SELECT 1
        FROM (${rangeUnionSql(includeBody = false)}) newer
       WHERE newer.expert_contact_id = u.expert_contact_id
         AND newer.source = 'INBOUND_PROCESSING'
```

后半段完整比较条件在 evidence 中逐字保存。该查询没有 process_status/reason_type 的真人回复过滤，也没有日期、主题、方向参数；不得声称它排除了所有自动回复。此功能直接呈现收发件箱已有最近来信语义。

现有后端验证代码：`src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepositoryIT.kt:475–492` 检查更晚 OUTBOUND 不影响 latestInbound；:506–514 检查账号收窄；:677–694 检查消息筛选不改变最近来信全范围投影。本轮只审阅这些代码，未运行依赖 MySQL 的 IT，不冒充已验证运行结果。

### E-2：数据存储与读写路径

本次新增读路径止于前端 summary；不修改 DB、ES、sessionStore schema。为证明字段含义，对上游已有 `inbound_mail_processing` 做关联审计。

Schema：`V5__create_inbound_mail_processing.sql:1–20` 定义 `received_at DATETIME NOT NULL`，expert_contact_id 可空并有外键；实体 `mail/domain/InboundMailProcessing.kt:26,32` 对应 LocalDateTime 和可空 expertContactId。V10 扩正文/处理信息；V14/V15 扩 reason_type/监控字段。V120 :17–22 将逻辑 UID 唯一键改为 `(sender_account_code,uid_validity,imap_uid)`；V134 :28–33 追加可空 mailbox_owner_code 及物理唯一键，未替换逻辑唯一键。新功能不依赖 UID，也不更改这些约束。

生产实体写入及直接 UPDATE 命中全集见 R1/R2（检索范围明确为 src/main，补查 scripts/ops；不把测试 fixture 算成生产写入）：

| 路径 | 写入内容 | 对上次回复的含义 |
|---|---|---|
| AutoMailReplyService.confirmManualReviewWithBody :1298–1330 | 新建 processing；receivedAt=received.receivedAt | 带清洗正文的待处理来信也有时间 |
| AutoMailReplyService.confirmProcessed :1352–1385 | 新建 processing；receivedAt=received.receivedAt | 处理/待处理共用；confirmManualReview 委派此处 |
| UnmatchedInboundMailService.bindToContact :193–201 | record.copy，补 expertContactId 和处理状态 | 绑定后进入专家会话，保留原收信时间 |
| UnmatchedInboundMailService.markResolved :238–245 | 修改处理字段，未改 receivedAt | 处理时间不是回复时间 |
| PendingMailOperationService.markResolved :1524–1533 | 修改处理字段，未改 receivedAt | 同上 |
| PendingMailOperationService.reopen → InboundMailProcessingRepository.reopenManualResolved :50–62 | 条件 UPDATE 状态及 updated_at | 撤销处理不改收信时间 |

历史 SQL：R2 中 V10/V14/V15/V120/V134 的结构变更及 V14/V15 状态补写保留；不修改历史迁移、不加回填。源码范围之外的人工运维 SQL 无法由仓库证明，计划不作“线上从未发生外部修改”的断言。

已有读路径按用途列出，逐行文件/方法调用位置在 R3：

- 会话聚合、列表及 timeline：MailboxConversationRepository；旧列表/监控聚合：MailRecordRepository，入口 MailboxService。
- 队列/摘要/最近收件：InboundMailProcessingRepository，UnmatchedInboundMailService、MailMonitoringService、InboundMailSummaryController。
- 人工回复、会议、发件锚点：PendingMailOperationService、ManualReplySendAttemptService、MeetingConfirmationService、ExpertContactManagementService。
- 正文预览/可信工作台/训练：AutoReplyPreviewService、TrustReplyWorkbenchService、RagReplyController、AiTrainingController。
- 标签和材料归属：InboundMailTagRepository、InboundMailTagService、ExpertMaterialService。
- 其他现存读用途：AutoMailReplyService 判重、BounceBackfillService 扫描、ExpertContactLocationService 联系时间样本、ExpertRepliedDismissalService 已回复移出锚点。

这些消费者不需要调整；本次不改变其输入表与字段。（来源：K-inbound-processing-write-paths、K-mailbox-inbound-source-authority，已重新 grep。）

### E-3：时区事实及可复用函数边界

- `config/TimeZoneConfig.kt:8–12`：启动时明确 `TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))`。
- `mail/service/ImapMailReceiveService.kt:339–343`：message.receivedDate → Instant → systemDefault → LocalDateTime；缺失时使用 LocalDateTime.now。故含义是系统保存的收件时间，不宣称等于发件人当地发送时刻。
- `MailboxConversationService.kt:77,193`：ISO_LOCAL_DATE_TIME 不携带 offset。
- `mailbox-chat.js:220–227`：datePart/timePart 只截日期/时分，没有星期和合法性校验。
- 同文件 :1955–1984 的 contactZoneParts 面向推荐时间，直接 `new Date(String(iso))` 且不输出星期；不能直接传入无 offset 的本字段，也不为本小功能重构它。
- 因此新增一个局部格式化函数，先按已证实的 `+08:00` 解释，再显式 Asia/Shanghai 格式化。API 若将来改变为带 offset 格式，需另行修改契约；本计划不提前设计多格式兼容层。

### E-4：前端状态、渲染与刷新链

`src/main/resources/static/mailbox-chat.js`：

| 状态/入口 | 写路径 | 已有读路径与风险 |
|---|---|---|
| instance.list.items | fetchList :1367–1369，根据模式写 data.items/data.records | renderList/renderPerson；当前成功回包不会主动重绘详情标题 |
| instance.selectedSummary | clearSelectedConversation :1555 清空；resolveFocusAndSelection :1596 替换；selectExpert :1689 赋当前行；refreshAfterExpertTagChange :4085 替换 | renderHeader、工作台、人工回复、checkInboundChangeQuiet 读取；不是纯展示专用存储 |
| 列表成功请求 | fetchList :1353–1398，disposed/listSeq :1366 检查后 renderList/renderPager | 增加时间局部刷新必须放在这层有效回包内；不在每个调用者重复增加 |
| 宿主刷新按钮 | onMovedRefreshClick :693–695 → refreshFromHost :7294–7301 | fetchList 后 refreshConversationQuiet；后者 :6626 只 renderTimeline，没有 renderHeader |
| 原 loadList | :1401–1407 → resolveFocusAndSelection :1594–1597 | 换了 selectedSummary 后走 quiet refresh，标题仍不能据此保证已更新 |
| 切换专家 | selectExpert :1683–1755 | seq/convEpoch 防旧详情响应；新时间不新增异步任务 |
| sessionStore / drafts Map | :395–465、saveConversationState 及既有草稿方法，检索全集 R11 | 本功能不保存时间进缓存，不新增键，不修改写路径 |

selectedSummary 赋值点与 fetchList 调用点逐条见 R5。新增显示不能顺手修复其他 summary 消费方，也不能为时间刷新覆盖整个 selectedSummary：那会影响工作台来信锚点。列表优先取当前页新行，详情专用槽由同一行更新。

### E-5：样式与静态资源审计

`src/test/js/mailboxChatStyle.test.js:17–42` 对 mailbox-chat.css 与 `docs/plans/2026-09-09/mailbox-refinement-evidence/mailbox-chat.target.css` 做字节比较；:105–154 一带检查 class 声明位置及模板卫生。规则是 mc-* 类必须在 mailbox-chat.css，其他类在 styles.css。故新增 `mailbox-reply-*`，写入 styles.css，不重做历史 CSS 基线。

前端样式盘点（真实最终覆盖也已读取，不能只看文件顶部）：

- 可复用 class：mc-person-main :17，mc-person :15–16，mc-person-meta :159，mc-person-counts :160，mc-person-tags :161–164；mc-header :34 及 :102，mc-identity :35–37 及 :104，mc-header-meta :103。路径均为 mailbox-chat.css。
- 基础 token：列表默认字号 12px、行高 1.6（:1）；名称 13px/600（:19）；计数 11px（:160）；详情标题最终 17px/600（:104）；普通色 #64748b，时间新强调色 #1e40af。卡片边框/选中态保留 :15–16，原按钮焦点为 2px solid #3b82f6（:68）；详情布局间距、圆角、阴影不新增覆盖。
- 新时间行的字号/间距/颜色以 S-3 精确值为准，既有区域以原 CSS 为准。
- DOM 约定：列表 `.mc-person > button.mc-person-main`，计数与标签在 `.mc-person-meta`；详情 `.mc-header > .mc-identity`。时间行不成为新点击控件。

改动前 DOM 原文（`mailbox-chat.js:1271–1276` 的局部）：

```html
                        <small>${escapeText(latestLine)}</small>
                        <span class="mc-person-meta">
                            <span class="mc-person-counts">收 ${Number(item.receivedCount) || 0} · 发 ${Number(item.sentCount) || 0}</span>
                            ${tagLine}
                        </span>
                        <span class="calendar-summary" data-role="meeting-summary"></span>
```

改动前 identity 赋值原文（:1826）：

```javascript
identity.innerHTML = `<h2>${escapeText(summary.name || summary.email || "-")}</h2><p>${escapeText(conversationSummaryInfo() || "-")}</p><span class="calendar-summary" data-role="meeting-summary"></span>`;
```

改动前 CSS 原文（完整相关段落见 code-baseline.txt）：

```css
.mail-chat .mc-person-main{display:flex;flex-direction:column;align-items:stretch;min-width:0;gap:5px;padding:12px 0 12px 10px;border:0;background:transparent;color:#475569;text-align:left;font:inherit;cursor:pointer}
.mail-chat .mc-identity{flex:1;min-width:180px}
.mail-chat .mc-identity p{margin-top:4px;color:#64748b;font-size:11px;overflow-wrap:anywhere}
.mail-chat .mc-person-meta{flex-wrap:nowrap;min-width:0;gap:6px;max-width:100%}
```

缓存键完整命中：R6 的 index.html :11、12、13、14、15、2345、2346、2347、2348、2349、2350，共 11 项，目前均为 `20261002-location-search`。R7 `rg -n -F '20261002-location-search' src/test` 的 exit=1；本次无需根据旧知识记录扩大固定键测试修改名单。执行前重查，若并行改动增加固定键测试，先修订文件清单，不绕过门禁。（来源：K-frontend-cache-key-triad。）

### 跨路径交互点

| 编号 | 写入 → 读取 | 本次措施 | 人工覆盖 |
|---|---|---|---|
| IP-1 | 收信持久化 receivedAt → Repository latestInbound → DTO → 前端两处展示 | 同源字段、北京时间格式化 | A-1/A-2 |
| IP-2 | 未匹配来信绑定/标记处理 → 同一 processing 行 → 新 summary | 保留原收件时间，不取 updated_at/resolved_at | A-7 |
| IP-3 | 有效 fetchList 回包 → list.items → 列表与已选详情时间槽 | 一个局部更新入口，保留请求序号守卫和编辑器 | A-3/A-6 |
| IP-4 | index 版本引用 → 浏览器载入 JS/CSS | 统一现有资源键，保留字节 CSS 合同 | A-8 |

### 知识加载处置

本轮使用并更新 last_used/hit_count：K-mailbox-chat-css-byte-contract、K-frontend-cache-key-triad、K-js-test-invocation-surface、K-inbound-processing-write-paths、K-plan-quantified-claims-need-grep-receipts、K-named-fixture-must-use-real-row、K-mailbox-inbound-source-authority、K-mail-datetime-zone-runtime-contract、K-mailbox-draft-cache-owner-capture。

其中草稿知识仅约束“不碰其写路径”，不扩展成草稿修复任务；具名样本知识用于禁止把演示 fixture 假称线上 Rashid 当前行。命中条目都在 90 天内，本轮不归档；既有高频通用规则已有 CLAUDE.md 指针，不重复追加。本需求不依赖多国 tzdb 目录，因此不把该知识中的运行期目录探测扩大到本计划。

## 实现方案

### T-1：格式化及两个展示入口

文件：`src/main/resources/static/mailbox-chat.js`。
遵守：I-1/I-2/I-3/I-5，S-1/S-2。

1. 在现有纯文本时间辅助函数附近新增私有 `formatLastReplyTime(receivedAt)`；输入是当前接口无 offset 的 ISO 本地时间，返回正常显示模型或 null。不得挂 `window.LastReplyPreview`，不得复制整个预览组件。
2. 支持与当前 ISO_LOCAL_DATE_TIME 对应的日期+时分、可选秒及小数秒；只允许严格完整字符串，拒绝空白、普通日期文本与非法分量。给输入显式附加 `+08:00`，用 `Intl.DateTimeFormat('zh-CN', {timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit',weekday:'long',hour:'2-digit',minute:'2-digit',second:'2-digit',hourCycle:'h23'})` 取得同一组 parts。秒仅用于合法性校验，不显示。
3. 对照输入的年月日时分秒校验格式化后分量；Date 无效或日期自动进位即返回 null。支持小数秒时可截取至毫秒精度后创建 Date，因为界面不展示毫秒；不改变原字符串或后端数据。Intl 不可用/抛错也返回 null。
4. 新增私有的展示内容生成函数，集中实现日期、空值、异常文案、time/title/纯文本 aria 描述。列表和详情共用，不复制两套判断。
5. 在 renderPerson 插入 S-1 的结构，并在原 aria-label 后补时间说明；保留现有最新邮件摘要及标签行。
6. 在 renderHeader 的 identity 增加 S-2 时间槽，再调用 T-2 的槽刷新函数；保留名称、邮箱和排期摘要顺序。

既有数据写入源是 E-2 所列路径，经现有 DTO 传入；源端无需调整。新写入只有 DOM 字符串，不产生新数据存储。

### T-2：补齐详情回复时间的刷新链

文件：`src/main/resources/static/mailbox-chat.js`。
遵守：I-1/I-3/I-4/I-5，S-2。

1. 新增 `renderLastReplyHeader(summary)`，先确认不是待匹配模式、已有 selectedContactId、summary.contactId 与其相同、详情时间槽存在；只写该槽 innerHTML。无匹配时安全返回，不改选中对象。
2. renderHeader 建好槽时默认填入“回复时间暂不可用”；随后优先使用 `findSummaryByContactId(instance.selectedContactId)`，找不到才使用身份匹配的 selectedSummary（初次详情/定位场景）调用槽刷新函数。两者都不可用则保留默认文案，与刷新函数的安全返回规则一致。
3. fetchList 正常会话回包中，在既有 disposed/listSeq 检查和 list.items 写入、renderList 后，查当前 selectedContactId 对应的新行，存在则调用槽更新函数。待匹配、失败、过期回包不调用。
4. 不更新 selectedSummary，不主动触发 checkInboundChangeQuiet，不修改任何草稿/锚点；它们继续走已有控制链。不要通过完整 renderHeader 刷新时间，避免重建其他 header 控件。
5. 不新增请求和缓存。切页后当前行不在新页时遵循既有选择行为；本需求不承诺对不可见、未取得 summary 的联系人持续刷新。

此方式覆盖 refreshFromHost、loadList、发件后的 fetchList、标签后的 fetchList 的显示入口，无需分别改多个调用者。

### T-3：样式及缓存引用

文件：`src/main/resources/static/styles.css`、`src/main/resources/static/index.html`。
遵守：I-5，S-1/S-2/S-3/S-4。

1. styles.css 追加 S-3 原文；保留已有规则和顺序。不要编辑 mailbox-chat.css 或历史 target CSS。
2. 重新读取 index 当前键，精确反查 src/test；仍无固定键才按本清单执行。将现有 11 项版本值同时更新为 S-4，其他 HTML 不动。
3. 不增加依赖、脚本、图标、Date 库、额外页面或新网络接口。

### T-4：复用现有测试框架验证实际挂载行为

文件：`src/test/js/mailboxChatBehavior.test.js`、`src/test/js/mailboxChatStyle.test.js`。
遵守：I-1 至 I-5，S-1 至 S-4。

Behavior 文件已有 createChatSandbox :760、mountChat :1208、bootChat :1218、可控 route、Mini DOM 和人工回复测试；在该文件添加需求测试组，直接运行生产 mailbox-chat.js。不要另搭一套测试框架或只抽取复制版函数测试。

行为用例：

| 用例 | 输入/动作 | 断言 |
|---|---|---|
| B-1 正常日期 | receivedAt=`2026-10-02T17:59:00`，点击专家 | 两处 `2026-10-02 星期五 17:59`，详情北京时间、datetime +08:00；aria 含相同说明 |
| B-2 发件晚于来信 | latestInbound=`2026-10-01T20:48:00`，latestMessage OUTBOUND=`2026-10-02T08:45:00` | 两处仍为 `2026-10-01 星期四 20:48` |
| B-3 无回复/缺失 | null+0；null+2；对象但空字符串；对象但非法时间 | 第一种“尚未回复”，其余“回复时间暂不可用”，无 time 元素 |
| B-4 日期边界 | `2025-12-31T23:58:00`、`2024-02-29T00:00:00`、`2026-02-30T10:00:00` | 分别星期三、星期四、异常文案；小数秒输入不改变日期星期与分钟 |
| B-5 设备时区 | 同一挂载用例分别在 TZ=UTC 与 TZ=America/Los_Angeles 进程运行 | B-1/B-4 输出相同，测试不得只比较两个都为空的节点 |
| B-6 宿主刷新 | 已选 A，输入草稿；新 summary 把 A 从 17:59 改为 18:05；调用组件 refreshFromHost | 两处 18:05；编辑器 DOM 身份、正文、回复目标、工作台挂载次数保持；无发送请求 |
| B-7 loadList 与 header 重建 | 更新 summary 后 refresh；再触发已有 header 渲染路径 | 两处均为新值且只有一个详情槽；不能重建后退回旧 selectedSummary 的时间 |
| B-8 竞态/卸载 | 延迟旧列表响应；先完成新响应/切换专家/卸载，再放行旧响应 | 当前专家/时间不被旧响应覆盖，卸载后不写 DOM |
| B-9 账号范围 | 同一 contact，模拟 accountCode A/B 返回不同 latestInbound；按已有筛选入口提交 | 显示当前回包的时间，不从 timeline/之前账号回退取最大值 |
| B-10 非目标视图及请求预算 | 待匹配/空列表；正常挂载与刷新记录 calls.api | 待匹配不出现新时间行；新增逻辑不引入任何额外 endpoint/定时轮询/存储写入 |

通用 fixture 使用测试专家 A/B，不使用真实姓名来包装合成数据。（来源：K-named-fixture-must-use-real-row。）

Style 文件增加 S-3 完整块包含断言、S-1/S-2 节点/顺序断言及既有字节/类名门禁；缓存键继续从 index 派生。必要时更新旧“精确 identity 模板”断言使其容纳明确的新槽，不删弱原有保护。

## 变更文件清单

这是后续实施的完整 allowlist；计划、取证和知识使用计数属于本轮计划产物，不混入实施文件数。

| # | 文件 | 改动边界 |
|---|---|---|
| 1 | `src/main/resources/static/mailbox-chat.js` | 私有格式化/展示函数；renderPerson、renderHeader、fetchList 的时间行/槽接入 |
| 2 | `src/main/resources/static/styles.css` | 仅追加 S-3 合同块 |
| 3 | `src/main/resources/static/index.html` | 现有资源版本值统一升级 |
| 4 | `src/test/js/mailboxChatBehavior.test.js` | B-1 至 B-10 需求行为覆盖，复用既有 harness |
| 5 | `src/test/js/mailboxChatStyle.test.js` | 新增时间行样式合同、结构与缓存契约验证 |

5≤10；一个前端子系统；不改后端、不新增共享存储字段。若执行发现必须修改名单外文件，先拿出失败证据修订计划；不得顺手修改 API、旧 CSS 基线或其他业务。

## 验收标准

| 项 | 验证方式 |
|---|---|
| I-1 | B-1/B-2/B-9；源码检查新增取值仅来自 latestInbound.receivedAt，不依赖 timeline/latestMessage；后端字段与 SQL 无 diff |
| I-2 | B-1/B-4/B-5；两种进程 TZ；午夜/跨年/闰日；time 属性带 +08:00；title 与可见星期一致 |
| I-3 | B-3/B-4；明确区分 null+0 与数据异常；非法日期不被进位显示 |
| I-4 | B-6/B-7/B-8；显示槽刷新无 editor 替换、无目标切换、无额外工作台 mount；旧回包被拦截 |
| I-5 | B-10；allowlist diff；新增无 API/存储/定时器；字节 CSS 对比；统一缓存键 |
| S-1 | DOM 顺序为最新摘要→时间行→计数标签；按钮 aria 保留原描述并包含时间；无 inline style |
| S-2 | identity 顺序为名称→邮箱账号→时间槽→排期；刷新/重建后恰一个时间槽；空分支无 time |
| S-3 | styles.css 包含上方逐字完整块；新增类逐一有声明；新选择器不影响其他页；目标 CSS 保持字节相同 |
| S-4 | index 原有资源名单和顺序相同，11 项同键；执行前后按实际键反查固定值；已有资源契约用例通过 |

命令（项目根目录执行）：

```sh
node --check src/main/resources/static/mailbox-chat.js
node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxChatStyle.test.js
TZ=UTC node --test --test-name-pattern='上次回复' src/test/js/mailboxChatBehavior.test.js
TZ=America/Los_Angeles node --test --test-name-pattern='上次回复' src/test/js/mailboxChatBehavior.test.js
node --test src/test/js/*.test.js
cmp src/main/resources/static/mailbox-chat.css docs/plans/2026-09-09/mailbox-refinement-evidence/mailbox-chat.target.css
git diff --check
```

新增测试组名称须含“上次回复”，两条 TZ 命令必须有实际执行用例，不能全 skipped。先目标用例，再全量 JS 一次；通过后无需反复重复。`verify.sh` 不作为本计划门禁。（来源：K-js-test-invocation-surface。）

本计划不改 Kotlin/SQL，不要求为了显示字段跑全量 Maven/数据库迁移。后续若另获发布授权，再使用仓库 `.multi-ai-kit.yaml` 的实际构建流程；本计划不执行部署。

机器验证产物必须说明基线失败与本次引入失败，记录实际命令/退出码。现有 134 tests 通过只能证明改前状态，不可替代 B 用例或人工验收。

## 人工验收清单

此节是权威清单。人工验收开始时才导出 `mailbox-last-reply-time-acceptance.md`，附逐项勾选、验收人、日期、结果/备注；现在不生成第二份漂移清单。

除 A-8 的发布资源检查外，使用本地/测试环境运行本次生产静态资源；通过浏览器开发工具的接口响应覆盖或测试服务桩准备下述数据。接口为现有 `GET /api/mail/mailbox/conversations`，不要把预览文件里的旧组件当待验收实现。真实收件/绑定验收使用测试邮箱和测试专家，生产只读查看。

### A-1: 日期、星期、时区可见

- 前置条件：会话列表响应中测试专家 A 的 latestInbound.receivedAt=`2026-10-02T17:59:00`，receivedCount=1；保留其余必需字段。
- 操作步骤：1. 打开收发件箱。2. 点击 A。3. 查看左侧和头部。4. 用浏览器时区覆盖设为 America/Los_Angeles，刷新后重看。
- 预期结果：列表“上次回复 2026-10-02 星期五 17:59”；详情“专家上次回复 2026-10-02 星期五 17:59 北京时间”。设备时区变化后仍相同；可见文字不用悬停才能看到。
- 覆盖：需求1，I-1/I-2，S-1/S-2，IP-1。

### A-2: 更晚发件与空值边界

- 前置条件：四位测试专家分别为：A 来信10-01 20:48、最后发件10-02 08:45；B latestInbound=null且receivedCount=0；C null且receivedCount=2；D receivedAt=`2025-12-31T23:58:00`。A 的日期年份均为2026。
- 操作步骤：1. 逐一点击。2. 查看两处时间。3. 将 C 的时间覆盖为 `2026-02-30T10:00:00` 后刷新。
- 预期结果：A“2026-10-01 星期四 20:48”；B“尚未回复”；C 两种异常均“回复时间暂不可用”；D“2025-12-31 星期三 23:58”。无当前时间兜底。
- 覆盖：需求2/3，I-1/I-2/I-3，IP-1，M-4 的字段语义。

### A-3: 刷新同步且不覆盖草稿

- 前置条件：A 初始回复17:59；已打开人工回复，在正文输入“测试草稿，请保留”；记录目标来信编号。测试接口下一次列表回包将 A 时间改为18:05；messages 仍返回原窗口或空 items。
- 操作步骤：1. 点击系统现有“刷新”。2. 查看列表、详情和编辑器。3. 更改测试回包为18:06，再通过已有列表重查入口刷新。
- 预期结果：两处依次18:05、18:06，星期仍五；正文原样保留，目标来信编号不变；没有发送行为，编辑器不闪退。不要求 messages 成功更新才显示新的 summary 时间。
- 覆盖：需求2，I-4/I-5，IP-3，M-2。

### A-4: 既有过滤和非目标视图

- 前置条件：测试专家 A 在账号 acc-a/acc-b 的最新来信分别为10-01 20:48、10-02 17:59；准备超过20位测试专家与一封未关联专家的待匹配来信。日期年份为2026。
- 操作步骤：1. 搜索 A。2. 分别选择账号 acc-a/acc-b 并提交。3. 切换全部、关注、已回复、待处理。4. 清空搜索并翻页。5. 打开待匹配。
- 预期结果：A 两种账号依次显示“2026-10-01 星期四 20:48”“2026-10-02 星期五 17:59”；搜索和页签仍由原筛选决定成员；分页每页仍按原20条；待匹配保持邮件队列，未新增“专家上次回复”。不新增排序选项。
- 覆盖：需求3，I-1/I-4/I-5，M-1。

### A-5: 现有详情操作与布局

- 前置条件：使用有标签、收发计数、材料和已配置所在地的测试专家；先记录这些显示值。
- 操作步骤：1. 打开专家。2. 查看并切换关注。3. 打开材料、所在地配置与推荐依据，关闭。4. 在1440px及390px宽度查看新时间行。
- 预期结果：原标签/计数/账号/材料值不因展示时间而变化；原按钮可使用。新增时间在摘要与计数之间，详情在账号信息下、排期上；列表11px、详情13px（≤760px为12px），强调色#1e40af；窄屏可换整行，不截掉日期或星期，无页面横向溢出。
- 覆盖：S-1/S-2/S-3，M-3。

### A-6: 迟到回包与切换专家

- 前置条件：A 回复17:59，B 回复10:30；通过测试桩延迟 A 的旧列表响应。新响应把 A 更新为18:05。
- 操作步骤：1. 发起延迟刷新。2. 发起较新刷新并让其先返回。3. 切换 B。4. 放行旧回包。5. 离开收发件箱再放行另一个被延迟的回包。
- 预期结果：A 列表保留18:05；B 详情仍为 B 的10:30，不能显示 A 的时间。离开后无残留时间节点/错误提示，返回后按新接口回包显示。
- 覆盖：I-4，IP-3，M-2。

### A-7: 来信绑定和处理时间不混淆

- 前置条件：在测试环境用测试邮箱给测试账号发送一封来信；若尚无专家绑定，在“待匹配”中查看其收件日期时间，记为 T，并准备一个测试专家。
- 操作步骤：1. 通过现有绑定操作将来信关联专家。2. 在收发件箱查找该专家。3. 标记该来信已处理。4. 若界面提供撤销入口，再撤销处理并刷新。
- 预期结果：显示值始终是 T 对应的日期、星期、时分；不能变成绑定/标记/撤销发生的时间。首次绑定后能在专家会话显示；新的测试来信到达并经现有收件流程后，刷新才更新为新 T。
- 覆盖：I-1/I-4，IP-1/IP-2，M-4。

### A-8: 缓存更新与范围回归

- 前置条件：本地或测试环境提供实施后的静态文件；浏览器曾载入旧版。生产发布不属于本项授权。
- 操作步骤：1. 正常刷新页面。2. 在网络面板查看 styles.css、mailbox-chat.js、app.js 及其他现有版本化资源。3. 打开收发件箱，再切到专家列表。4. 从任务记录进入旧邮件钻取视图。
- 预期结果：现有11项资源均为 `v=20261002-mailbox-last-reply`；新行出现且有 S-3 样式。专家列表和任务钻取没有新增回复时间栏，没有为本功能发起新的 API、定时轮询或数据库写入请求。
- 覆盖：I-5，S-4，IP-4，M-1/M-3/M-4。

## 计划自查

- 已确认需求沿用上一轮预览，没有待猜测的新业务规则；当前 API 语义与拟定展示行为分开陈述。
- 上游读写路径、格式化链、summary 更新入口、CSS 字节限制和缓存键均有源文件位置及检索回执。
- I-1–I-5、S-1–S-4、T-1–T-4、B-1–B-10、A-1–A-8 相互对应；需求与 M-1–M-4、IP-1–IP-4 都有人工覆盖。
- 所有新增类的完整 CSS 已给出；新节点的正常/空/异常结构明确；没有允许执行时自由调整的样式。
- 实施清单5文件，单子系统，无新增存储字段。没有引入后端重算、轮询或通用时间框架。
- 当前只完成计划与基线验证；实施、功能验证、人工验收、上线分别保留真实状态，不提前标记完成。
