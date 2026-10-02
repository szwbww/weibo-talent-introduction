# Fast-P Child Brief — 01（收发件箱：专家上次回复时间）

## 身份与边界

- Master plan（批准版，字节冻结）：`docs/plans/2026-10-02/mailbox-last-reply-time.md`，identity `commit:5d1789f90716a27e265b63340a9aef562a0035d9`。
- 本 child 批准计划（完整合同，必须先通读）：同一路径 `docs/plans/2026-10-02/mailbox-last-reply-time.md`（单子计划 run：master 计划即本 child 计划；「需求描述」「关键不变量」I-1～I-5、「样式契约」S-1～S-4、「实现方案」T-1～T-4、「变更文件清单」「验收标准」逐条生效）。
- Worktree / branch / `child_base_sha`：见派发消息。
- 依赖：none。无下游 child；人工验收 A-1～A-8 在本 run 之外，不要求执行。
- 取证材料（本 worktree 内，只读）：`docs/plans/2026-10-02/mailbox-last-reply-evidence/`（code-baseline.txt、grep-receipts.txt、baseline-tests.txt）。

## 全局约束

1. 只允许修改「Authorized Files」表内 5 个文件；不得新建白名单外文件（含 fixture、静态资源）。`mailbox-chat.css` 及其历史 target CSS、Kotlin、SQL、迁移、`app.js`、`docs/**`（除执行报告外）、其他静态资源全部只读；`index.html` 仅改既有 11 个资源版本值。
2. 不得修改 `docs/plans/**`（fast-p 证据与计划由控制方提交）；不得 push、merge、rebase、squash、amend、reset；不得改写已有提交。
3. 唯一允许的 worktree 外只读参考：`/Users/lukai/IdeaProjects/weibo-talent-introduction/docs/mockups/mailbox-last-reply-preview/`（index.html / preview.png），仅用于对照布局；禁止复制 preview.js 的全局对象、示例数据、顶部开关或轮询逻辑，禁止把 mockup 当生产依赖；S-1/S-2/S-3 是唯一实现依据。
4. 产品代码提交格式：`feat(fast-p): implement 01`；把 fast-p 报告/日志（`docs/plans/fast/**`）排除在该提交之外，报告写完留在工作树由控制方提交。
5. 若计划与代码冲突、需要白名单外文件、需要新行为或需要修订计划：返回 `PLAN_CONFLICT` / `BLOCKED`，不要自行扩范围或改计划。
6. 禁止联网、连线上 MySQL/ES、发信、部署；不得新增依赖；不得新增后端接口、DB/ES 字段、迁移、发送 adapter 改动。
7. 实施前按 T-3.2 重新核对 `index.html` 当前 11 个 `?v=` 键，并在 `src/test` 内反查该键的固定值命中；若命中新的硬编码缓存键测试文件，返回报告请求修订文件清单，不静默扩文件、不按历史知识猜数量。
8. 值来源纪律（I-1）：一切显示值只来自当前 summary 的 `latestInbound.receivedAt`；不得改用 `latestMessage.time`、最后一条已加载 timeline、`firstReplyAt`、`createdAt`、`resolvedAt` 或所在地推荐样本。
9. 计划明确不做：新字段/表/迁移、历史回填、邮件分类修正、回复耗时、距今天数、排序/筛选新选项、专家当地时间、自动轮询、消息日分隔线改版、旧任务钻取表格增加列、发布上线。
10. 实现必须复用既有 harness/组件；不得另搭测试框架、不得复制生产函数到测试里单测副本。

## Authorized Files（5）

| # | 精确路径 | 改动 |
| --- | --- | --- |
| 1 | `src/main/resources/static/mailbox-chat.js` | 私有格式化/展示函数；`renderPerson`、`renderHeader`、`fetchList` 的时间行/时间槽接入与局部刷新 |
| 2 | `src/main/resources/static/styles.css` | 仅逐字追加 S-3 合同块 |
| 3 | `src/main/resources/static/index.html` | 仅 11 个既有资源版本值统一改为 `20261002-mailbox-last-reply` |
| 4 | `src/test/js/mailboxChatBehavior.test.js` | B-1～B-10 需求行为覆盖，复用既有 createChatSandbox/mountChat/bootChat harness |
| 5 | `src/test/js/mailboxChatStyle.test.js` | S-1/S-2/S-3/S-4 样式与结构合同、缓存契约验证 |

## 关键不变量（计划 I-1～I-5 摘要；冲突时以计划原文为准）

- I-1 来信来源与账号范围：取当前 summary 的 `latestInbound.receivedAt`；不得改用 `latestMessage.time`、最后一条已加载 timeline、`firstReplyAt`、`createdAt`、`resolvedAt`、所在地推荐样本。范围是后端 `activeAccountCodes()` + 可选 `accountCode`；日期/主题/方向筛选只决定专家是否入列，不截断最近来信投影。不新增 DB 写路径。
- I-2 北京日期与星期同源：接口是无 offset 的 `ISO_LOCAL_DATE_TIME`，按北京时间解释；输出固定 `YYYY-MM-DD 星期X HH:mm`，不依赖设备时区，不显示秒。`<time datetime>` 写规范化 `+08:00` 时间；title 为完整北京时间；普通文本与属性值都经 `escapeText`。星期与日期必须来自同一北京时间。不修改既有 `datePart/timePart/contactZoneParts`。
- I-3 空值不是“从未回复”的通用替身：仅 `latestInbound === null && receivedCount === 0` 显示“尚未回复”；存在 latestInbound 但时间空白/非法，或计数大于 0 却没有 latestInbound，显示“回复时间暂不可用”；字段缺失/结构异常同样后者。不得输出 `Invalid Date`、NaN、1970 年、当前时间或空 `<time>`；不得把 2 月 30 日自动进位成 3 月日期。
- I-4 同步展示、局部更新、沿用竞态守卫：有效 `fetchList` 成功回包后列表与已选专家详情取同一份当前行；只替换详情的回复时间槽。保留既有 `disposed/listSeq` 守卫；待匹配模式不执行此更新；失败回包沿用已有错误处理，不把旧值改成“尚未回复”。不因显示修改 `instance.selectedSummary`、人工回复目标或草稿；不调用 `selectExpert`、`renderConversationContent`、`renderTimeline` 来刷新时间。当前列表无已选专家时，不从别的行取值、不自行换人。
- I-5 纯展示与缓存激活边界：不新增 API 请求、定时器、全局预览对象、localStorage/sessionStorage 或另存的 lastReplyAt 状态；不改变原 summary 对象。新增 CSS 只作用于 `.mail-chat` 内的新业务类。`mailbox-chat.css` 及其历史目标文件保持字节不变。`index.html` 现有版本化资源统一升级缓存键，不增加资源或调整顺序。

## 样式契约（计划 S-1～S-4 摘要；计划原文与其中 CSS 逐字块为唯一依据）

- S-1 列表回复时间行：插入 `renderPerson` 的 `<small>${escapeText(latestLine)}</small>` 之后、`.mc-person-meta` 之前；正常分支结构 `span.mailbox-reply-list` > `span`(“上次回复”) + `time[datetime][title]`；空值分支 `span.mailbox-reply-empty`（“尚未回复”）；异常分支同结构（“回复时间暂不可用”）。按钮原 aria-label 末尾补相同纯文本说明（含北京时间），保留专家名称/标签描述；日期不得只出现在 title。
- S-2 详情回复时间槽：`renderHeader` 的 `.mc-identity` 内 `<h2>`、`<p>` 之后，`span.mailbox-reply-detail[data-role=last-reply-time]` > `span`(“专家上次回复”) + `time[datetime][title]` + `span.mailbox-reply-zone`(“北京时间”)，然后再是原 `span.calendar-summary[data-role=meeting-summary]`。空/异常分支用 S-1 的 empty span 替代 time，并省略北京时间尾注。局部刷新只修改 `[data-role="last-reply-time"]` 内容；重建后仍恰一个槽。
- S-3 新增样式逐字合同：下列整块逐字追加至 `styles.css`，四个新增类（`mailbox-reply-list`、`mailbox-reply-detail`、`mailbox-reply-zone`、`mailbox-reply-empty`）声明、子元素、活跃态、窄屏规则都在块内；禁止 inline style、新增块外未声明 class、`<style>`/独立资源、修改 `.mc-person-main` 间距或既有标签/计数布局；不得自由调整合同值：

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

- S-4 静态资源引用：`index.html:11–15,2345–2350` 的 5 个 CSS、6 个 JS 保留现有标签、文件名、顺序，只把版本值统一改为 `20261002-mailbox-last-reply`；`task-modal-runtime.js` 原本没有版本参数，保持原状；无可见 DOM 变化。

## 实现要点（计划 T-1～T-4 摘要）

- T-1 格式化及两个展示入口（mailbox-chat.js）：新增私有 `formatLastReplyTime(receivedAt)`，输入无 offset ISO 本地时间，返回正常显示模型或 null；支持日期+时分、可选秒与小数秒，只允许严格完整字符串，拒绝空白/普通日期文本/非法分量；给输入显式附加 `+08:00`，用 `Intl.DateTimeFormat('zh-CN', {timeZone:'Asia/Shanghai', year:'numeric', month:'2-digit', day:'2-digit', weekday:'long', hour:'2-digit', minute:'2-digit', second:'2-digit', hourCycle:'h23'})` 取同一组 parts；秒仅校验不显示；对照输入分量校验格式化结果，Date 无效或日期进位返回 null；Intl 不可用/抛错返回 null。新增私有展示内容生成函数集中实现日期/空值/异常文案、time/title/纯文本 aria 描述，列表和详情共用。不得挂 `window.LastReplyPreview`，不得复制整个预览组件。
- T-2 详情回复时间刷新链（mailbox-chat.js）：新增 `renderLastReplyHeader(summary)`——先确认非待匹配模式、已有 selectedContactId、summary.contactId 相同、详情槽存在，只写该槽 innerHTML，无匹配安全返回且不改选中对象。`renderHeader` 建槽默认填“回复时间暂不可用”，随后优先 `findSummaryByContactId(instance.selectedContactId)`，找不到才用身份匹配的 selectedSummary 调用槽刷新；都不可用保留默认文案。`fetchList` 正常会话回包中，在既有 disposed/listSeq 检查、list.items 写入、renderList 之后，查当前 selectedContactId 对应新行，存在则调用槽更新；待匹配/失败/过期回包不调用。不更新 selectedSummary、不触发 checkInboundChangeQuiet、不修改草稿/锚点、不通过完整 renderHeader 刷新时间；不新增请求和缓存。
- T-3 样式及缓存引用：仅按 S-3 追加；仅按 S-4 改 11 个键；不增加依赖、脚本、图标、Date 库、额外页面或新网络接口。
- T-4 测试（behavior/style 两文件）：复用既有 harness 与可控 route，直接运行生产 `mailbox-chat.js`；B-1 正常日期（`2026-10-02T17:59:00`：两处 `2026-10-02 星期五 17:59`、详情北京时间、datetime `+08:00`、aria 同说明）；B-2 发件晚于来信（仍显示来信时间）；B-3 无回复/缺失（null+0“尚未回复”；null+2、空字符串、非法时间“回复时间暂不可用”，无 time 元素）；B-4 日期边界（`2025-12-31T23:58:00` 星期三、`2024-02-29T00:00:00` 星期四、`2026-02-30T10:00:00` 异常、小数秒不影响日期星期与分钟）；B-5 设备时区（同一挂载用例在 TZ=UTC 与 TZ=America/Los_Angeles 下 B-1/B-4 输出相同，不得只比较两个都为空）；B-6 宿主刷新（两处 18:05；编辑器 DOM 身份、正文、回复目标、工作台挂载次数保持；无发送请求）；B-7 loadList 与 header 重建（两处新值且只有一个详情槽）；B-8 竞态/卸载（旧回包不覆盖，卸载后不写 DOM）；B-9 账号范围（显示当前回包时间，不回退取最大）；B-10 非目标视图及请求预算（待匹配无新时间行；不引入额外 endpoint/轮询/存储写入）。Style 文件增加 S-3 完整块包含断言、S-1/S-2 节点/顺序断言及既有字节/类名门禁；缓存键继续从 index 派生；必要时更新旧“精确 identity 模板”断言使其容纳新槽，不删弱原有保护。fixture 用测试专家 A/B，不用真实姓名包装合成数据。新增测试组名称须含“上次回复”。

## 必需命令（fresh 运行，逐条记录 exit code 与计数）

```sh
node --check src/main/resources/static/mailbox-chat.js
node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxChatStyle.test.js
TZ=UTC node --test --test-name-pattern='上次回复' src/test/js/mailboxChatBehavior.test.js
TZ=America/Los_Angeles node --test --test-name-pattern='上次回复' src/test/js/mailboxChatBehavior.test.js
node --test src/test/js/*.test.js
cmp src/main/resources/static/mailbox-chat.css docs/plans/2026-09-09/mailbox-refinement-evidence/mailbox-chat.target.css
git diff --check
```

- 基线红/绿对照见 `docs/plans/fast/2026-10-02-mailbox-last-reply-time/children/01/baseline.md`；不得修改范围外文件去消除基线失败。
- 两条 TZ 命令必须有实际执行用例，不能全 skipped。
- 本计划不改 Kotlin/SQL，不要求为了显示字段跑全量 Maven/数据库迁移；不得以 JS 通过宣称后端集成已实测。
- `verify.sh` 不作为本计划门禁。

## 交付物

- 一个本地实现提交：`feat(fast-p): implement 01`。
- 执行报告：`docs/plans/fast/2026-10-02-mailbox-last-reply-time/children/01/execution.md`（不进入实现提交）。
- 返回：`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要、报告路径。
